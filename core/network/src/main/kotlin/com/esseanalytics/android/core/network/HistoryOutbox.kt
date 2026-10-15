package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.PendingHistoryEventDao
import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Durable publication history, strictly scoped to the user and server captured at enqueue time. */
@Singleton
class HistoryOutbox @Inject constructor(
    private val syncApi: SyncApi,
    private val dao: PendingHistoryEventDao,
    private val causalDao: CausalPlatformDao,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
) {
    private val flushMutex = Mutex()

    private data class CapturedSession(val token: String, val userId: String, val server: String)

    suspend fun sendOrEnqueue(request: RecordPublishRequest) {
        val session = captureSession()
        val scopedRequest = session?.let { withCachedIdentity(request, it) } ?: request
        if (session == null) {
            // Do not attach an anonymous/legacy event to whichever account logs in next.
            dao.insert(scopedRequest.toEntity(userKey = null, serverUrl = null, state = "blocked", lastError = "missing_session"))
            return
        }
        if (!isCurrent(session)) {
            enqueue(scopedRequest, session)
            return
        }
        val result = runCatching { syncApi.recordPublish(scopedRequest) }
        if (isCurrent(session) && result.isSuccess) return
        if (isCurrent(session) && result.exceptionOrNull().isPermanentRejection()) {
            dao.insert(scopedRequest.toEntity(session.userId, session.server, state = "blocked", lastError = "permanent_rejection"))
            return
        }
        enqueue(scopedRequest, session)
    }

    private suspend fun enqueue(request: RecordPublishRequest, session: CapturedSession) {
        dao.insert(request.toEntity(session.userId, session.server))
    }

    private suspend fun captureSession(): CapturedSession? {
        val session = tokenStore.currentSession() ?: return null
        val server = CatalogRegistrationOutbox.canonicalServer(settingsStore.serverUrl.first())
        return CapturedSession(session.first, session.second, server)
    }

    private suspend fun isCurrent(expected: CapturedSession): Boolean {
        val current = tokenStore.currentSession() ?: return false
        return current.first == expected.token && current.second == expected.userId &&
            CatalogRegistrationOutbox.canonicalServer(settingsStore.serverUrl.first()) == expected.server
    }

    private suspend fun withCachedIdentity(request: RecordPublishRequest, session: CapturedSession): RecordPublishRequest {
        if (request.contentId != null) return request
        val scope = CatalogRegistrationOutbox.scope(session.userId, session.server)
        val resolved = request.clientFileId?.let { causalDao.findContentId(scope, CausalKeys.forClientFile(it)) }
            ?: request.remoteLibraryVideoId?.let { causalDao.findContentId(scope, CausalKeys.forRemote(it)) }
        return request.copy(contentId = resolved)
    }

    suspend fun flush() = flushMutex.withLock {
        val session = captureSession() ?: return
        val pending = dao.pending(session.userId, session.server, System.currentTimeMillis())
        for (entity in pending) {
            if (!HistoryOutboxScope.canSend(entity, session.userId, session.server) || !isCurrent(session)) return
            val result = runCatching {
                syncApi.recordPublish(withCachedIdentity(entity.toRequest(), session))
            }
            // A login or server switch while the request is in flight must never ACK this row.
            val current = captureSession()
            if (!HistoryOutboxScope.canAcknowledge(
                    entity, session.token, session.userId, session.server,
                    current?.token, current?.userId, current?.server.orEmpty(),
                )) return
            when {
                result.isSuccess -> dao.delete(entity)
                result.exceptionOrNull().isPermanentRejection() ->
                    dao.block(entity.id, session.userId, session.server, "permanent_rejection")
                else -> {
                    val attempts = entity.attempts + 1
                    val delayMs = (30_000L * (1L shl attempts.coerceAtMost(10))).coerceAtMost(86_400_000L)
                    val error = result.exceptionOrNull()?.let { it.javaClass.simpleName.take(80) } ?: "unknown"
                    dao.recordAttempt(entity.id, session.userId, session.server, attempts, System.currentTimeMillis() + delayMs, error)
                }
            }
        }
    }
}

private fun Throwable?.isPermanentRejection(): Boolean {
    val code = (this as? HttpException)?.code() ?: return false
    return code in 400 until 500 && code != 401 && code != 429
}

private fun RecordPublishRequest.toEntity(
    userKey: String?,
    serverUrl: String?,
    state: String = "pending",
    lastError: String? = null,
) = PendingHistoryEventEntity(
    platform = platform,
    contentId = contentId,
    clientFileId = clientFileId,
    platformId = platformId,
    platformUrl = platformUrl,
    fileName = fileName,
    remoteLibraryVideoId = remoteLibraryVideoId,
    title = title,
    publishedAt = publishedAt,
    operationId = operationId,
    deviceId = deviceId,
    deviceName = deviceName,
    userKey = userKey,
    serverUrl = serverUrl,
    state = state,
    lastError = lastError,
)

private fun PendingHistoryEventEntity.toRequest() = RecordPublishRequest(
    platform = platform,
    contentId = contentId,
    clientFileId = clientFileId,
    platformId = platformId,
    platformUrl = platformUrl,
    fileName = fileName,
    remoteLibraryVideoId = remoteLibraryVideoId,
    title = title,
    publishedAt = publishedAt,
    operationId = operationId,
    deviceId = deviceId,
    deviceName = deviceName,
)
