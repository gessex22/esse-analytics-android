package com.esseanalytics.android.core.network

import android.content.Context
import android.net.Uri
import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.dao.PendingCatalogRegistrationDao
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.PendingCatalogRegistrationEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.database.util.toDomain
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.CatalogImportMetadata
import com.esseanalytics.android.core.network.dto.ResolveIdentityRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.security.MessageDigest

@Singleton
class CatalogRegistrationOutbox @Inject constructor(
    private val api: SyncApi,
    private val dao: PendingCatalogRegistrationDao,
    private val causalDao: CausalPlatformDao,
    private val fileDao: FileDao,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val deviceIdProvider: DeviceIdProvider,
    @ApplicationContext private val context: Context,
) {
    private val flushMutex = Mutex()

    suspend fun enqueue(file: VideoFile, sha256: String) {
        val session = tokenStore.currentSession() ?: return
        val server = canonicalServer(settingsStore.serverUrl.first())
        val scope = scope(session.second, server)
        enqueueForScope(file, sha256, session, server, scope)
    }

    private suspend fun enqueueForScope(
        file: VideoFile,
        sha256: String,
        session: Pair<String, String>,
        server: String,
        scope: String,
    ) {
        if (tokenStore.currentSession() != session || canonicalServer(settingsStore.serverUrl.first()) != server) return
        val canonicalId = causalDao.findContentId(scope, CausalKeys.forClientFile(file.clientFileId))
            ?: file.remoteLibraryVideoId?.let { causalDao.findContentId(scope, CausalKeys.forRemote(it)) }
        if (tokenStore.currentSession() != session || canonicalServer(settingsStore.serverUrl.first()) != server) return
        val row = PendingCatalogRegistrationEntity(
            userKey = scope,
            serverUrl = server,
            deviceId = deviceIdProvider.deviceId(),
            clientFileId = file.clientFileId,
            remoteLibraryVideoId = file.remoteLibraryVideoId,
            contentId = canonicalId,
            fileName = file.fileName,
            sha256 = sha256,
            durationSeconds = file.duracionSegundos,
            resolution = file.resolucion,
            format = file.formato,
            createdAt = file.fechaCreacion?.toString(),
            thumbnailPath = file.thumbnailPath,
        )
        dao.enqueue(row)
        flush()
    }

    suspend fun reconcileAndFlush() {
        val session = tokenStore.currentSession() ?: return
        val server = canonicalServer(settingsStore.serverUrl.first())
        val scope = scope(session.second, server)
        for (entity in fileDao.getFilesPendingCatalogReconciliation()) {
            if (tokenStore.currentSession() != session || canonicalServer(settingsStore.serverUrl.first()) != server) return
            val file = entity.toDomain()
            if (!dao.exists(scope, server, file.clientFileId)) {
                val hash = file.contentSha256 ?: hashLocalFile(file.filePath)?.also {
                    fileDao.setSha256IfMissing(entity.id, it)
                } ?: continue
                if (tokenStore.currentSession() != session || canonicalServer(settingsStore.serverUrl.first()) != server) return
                enqueueForScope(file.copy(contentSha256 = hash), hash, session, server, scope)
            }
        }
        flush()
    }

    suspend fun flush() = flushMutex.withLock {
        val session = tokenStore.currentSession() ?: return
        val (token, userId) = session
        val server = canonicalServer(settingsStore.serverUrl.first())
        val userScope = scope(userId, server)
        val rows = dao.pending(userScope, server, System.currentTimeMillis())
        for (row in rows) {
            if (tokenStore.currentSession() != session || canonicalServer(settingsStore.serverUrl.first()) != server) return
            try {
                if (row.state == "completed") {
                    row.contentId?.let { uploadThumbnailIfPossible(row, it, token, userId, server) }
                    continue
                }
                val result = api.resolveIdentity(
                    ResolveIdentityRequest(
                        fileName = row.fileName,
                        contentId = row.contentId,
                        remoteLibraryVideoId = row.remoteLibraryVideoId,
                        deviceId = row.deviceId,
                        clientFileId = row.clientFileId,
                        contentSha256 = row.sha256,
                        metadata = CatalogImportMetadata(row.durationSeconds, row.resolution, row.format, row.createdAt),
                    ),
                )
                if (tokenStore.token != token || tokenStore.currentUser?.id != userId
                    || canonicalServer(settingsStore.serverUrl.first()) != server) return
                val contentId = result.contentId
                if (contentId.isNullOrBlank()) {
                    val attempt = row.attempts + 1
                    val delayMs = (30_000L * (1L shl attempt.coerceAtMost(10))).coerceAtMost(86_400_000L)
                    dao.recordAttempt(row.id, System.currentTimeMillis() + delayMs, "identity_unresolved")
                    continue
                }
                causalDao.upsertIdentity(ContentIdentityEntity(userScope, CausalKeys.forClientFile(row.clientFileId), contentId))
                row.remoteLibraryVideoId?.let {
                    causalDao.upsertIdentity(ContentIdentityEntity(userScope, CausalKeys.forRemote(it), contentId))
                }
                causalDao.hydrateContentIdByClientFileId(userScope, row.clientFileId, contentId)
                row.remoteLibraryVideoId?.let { causalDao.hydrateContentIdByRemoteId(userScope, it, contentId) }
                result.platformRev.orEmpty().forEach { (platform, revision) ->
                    causalDao.upsertRevision(PlatformRevisionEntity(userScope, contentId, platform, revision))
                }
                dao.markCompleted(row.id, contentId)
                uploadThumbnailIfPossible(row, contentId, token, userId, server)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val code = (error as? HttpException)?.code()
                if (code != null && code in 400 until 500 && code != 401 && code != 429) {
                    dao.block(row.id, "http_$code")
                } else {
                    val attempt = row.attempts + 1
                    val delayMs = (30_000L * (1L shl attempt.coerceAtMost(10))).coerceAtMost(86_400_000L)
                    dao.recordAttempt(row.id, System.currentTimeMillis() + delayMs, error.javaClass.simpleName)
                }
            }
        }
    }

    private suspend fun uploadThumbnailIfPossible(
        row: PendingCatalogRegistrationEntity, contentId: String, token: String, userId: String, server: String,
    ) {
        val path = row.thumbnailPath ?: return
        if (row.thumbnailUploaded) return
        val file = File(path)
        if (!file.isFile || file.length() <= 0 || file.length() > 8L * 1024 * 1024) {
            dao.markThumbnailUploaded(row.id) // el catálogo conserva el fallback publicado/genérico
            return
        }
        if (tokenStore.token != token || tokenStore.currentUser?.id != userId
            || canonicalServer(settingsStore.serverUrl.first()) != server) return
        try {
            val part = MultipartBody.Part.createFormData("thumbnail", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            val response = api.uploadCatalogThumbnail(contentId, part)
            if (tokenStore.token != token || tokenStore.currentUser?.id != userId
                || canonicalServer(settingsStore.serverUrl.first()) != server) return
            if (response.isSuccessful) {
                dao.markThumbnailUploaded(row.id)
            } else if (response.code() in 400 until 500 && response.code() != 401 && response.code() != 429) {
                dao.markThumbnailUploaded(row.id)
            } else {
                val attempt = row.attempts + 1
                val delayMs = (30_000L * (1L shl attempt.coerceAtMost(10))).coerceAtMost(86_400_000L)
                dao.recordAttempt(row.id, System.currentTimeMillis() + delayMs, "thumbnail_http_${response.code()}")
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val attempt = row.attempts + 1
            val delayMs = (30_000L * (1L shl attempt.coerceAtMost(10))).coerceAtMost(86_400_000L)
            dao.recordAttempt(row.id, System.currentTimeMillis() + delayMs, "thumbnail_${error.javaClass.simpleName}")
        }
    }

    private suspend fun hashLocalFile(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(path)
            val input = if (uri.scheme == "content") context.contentResolver.openInputStream(uri)
                else File(path).takeIf(File::isFile)?.inputStream()
            if (input == null) return@runCatching null
            val digest = MessageDigest.getInstance("SHA-256")
            input.buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_SERVER = "https://api.esse-analytics.com/"
        fun scope(userId: String, serverUrl: String): String = "${canonicalServer(serverUrl)}|$userId"
        fun canonicalServer(value: String): String = (value.ifBlank { DEFAULT_SERVER }).let { if (it.endsWith('/')) it else "$it/" }
    }
}
