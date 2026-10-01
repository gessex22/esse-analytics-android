package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.ConfirmLinkRequest
import com.esseanalytics.android.core.network.dto.CrossMatchCandidatesResponseDto
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import com.esseanalytics.android.core.network.dto.ManualPlatformLinkRequest
import com.esseanalytics.android.core.network.dto.PlatformCausalResponse
import com.esseanalytics.android.core.network.dto.PlatformRecentPageDto
import com.esseanalytics.android.core.network.dto.PlatformTransitionRequest
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import com.esseanalytics.android.core.network.dto.ResolveCrossMatchSlotRequest
import com.esseanalytics.android.core.network.dto.ResolveIdentityRequest
import com.esseanalytics.android.core.network.dto.ResolveIdentityResponse
import com.esseanalytics.android.core.network.dto.SkipNextRequest
import com.esseanalytics.android.core.network.dto.SyncReviewResponseDto
import com.esseanalytics.android.core.network.dto.SyncStatsDto
import com.esseanalytics.android.core.network.dto.TriggerSyncResponse
import com.esseanalytics.android.core.network.dto.UpdateFilePlatformsRequest
import com.esseanalytics.android.core.network.dto.UploadHistoryResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response

// Fakes compartidos de la infra causal. Nada acá pega a la red: si un test
// termina llamando a un endpoint que no configuró, el fake falla ruidosamente
// en vez de devolver algo plausible.

// DAO causal en memoria. Replica el comportamiento de las @Query que importan
// (sobre todo el filtro de getUnresolved), para que un test que dice "esta fila
// no sale" esté verificando la misma regla que corre en producción.
class FakeCausalPlatformDao : CausalPlatformDao {
    val identities = LinkedHashMap<Pair<String, String>, String>()
    val revisions = LinkedHashMap<Triple<String, String, String>, Long>()
    val outbox = mutableListOf<PlatformTransitionOutboxEntity>()
    private var nextId = 1L

    override suspend fun upsertIdentity(entity: ContentIdentityEntity) {
        identities[entity.userKey to entity.localKey] = entity.contentId
    }

    override suspend fun findContentId(userKey: String, localKey: String): String? =
        identities[userKey to localKey]

    override suspend fun upsertRevision(entity: PlatformRevisionEntity) {
        revisions[Triple(entity.userKey, entity.contentId, entity.platform)] = entity.revision
    }

    override suspend fun findRevision(userKey: String, contentId: String, platform: String): Long? =
        revisions[Triple(userKey, contentId, platform)]

    override suspend fun insertOutbox(entity: PlatformTransitionOutboxEntity): Long {
        val id = nextId++
        outbox += entity.copy(id = id)
        return id
    }

    override suspend fun updateOutbox(entity: PlatformTransitionOutboxEntity) {
        val index = outbox.indexOfFirst { it.id == entity.id }
        if (index >= 0) outbox[index] = entity
    }

    override suspend fun deleteOutbox(entity: PlatformTransitionOutboxEntity) {
        outbox.removeAll { it.id == entity.id }
    }

    override suspend fun getAllOutbox(): List<PlatformTransitionOutboxEntity> =
        outbox.sortedWith(compareBy({ it.createdAtEpochMs }, { it.id }))

    override suspend fun getResolvable(userKey: String): List<PlatformTransitionOutboxEntity> =
        outbox.filter { it.userKey == userKey && it.contentId != null }
            .sortedWith(compareBy({ it.contentId }, { it.platform }, { it.createdAtEpochMs }, { it.id }))

    override suspend fun getUnresolved(userKey: String): List<PlatformTransitionOutboxEntity> =
        outbox.filter { it.userKey == userKey && it.contentId == null && it.clientFileId != null }

    override suspend fun countBlockedLegacy(userKey: String): Int =
        outbox.count { it.userKey == userKey && it.contentId == null && it.clientFileId == null }

    override suspend fun pendingForPlatform(userKey: String, platform: String): List<PlatformTransitionOutboxEntity> =
        outbox.filter { it.userKey == userKey && it.platform == platform }

    override suspend fun hydrateContentIdByRemoteId(userKey: String, remoteLibraryVideoId: String, contentId: String) {
        replaceWhere { it.userKey == userKey && it.remoteLibraryVideoId == remoteLibraryVideoId && it.contentId == null }
            .forEach { updateOutbox(it.copy(contentId = contentId)) }
    }

    override suspend fun hydrateContentIdByClientFileId(userKey: String, clientFileId: String, contentId: String) {
        replaceWhere { it.userKey == userKey && it.clientFileId == clientFileId && it.contentId == null }
            .forEach { updateOutbox(it.copy(contentId = contentId)) }
    }

    private fun replaceWhere(predicate: (PlatformTransitionOutboxEntity) -> Boolean) = outbox.filter(predicate)
}

// FileDao en memoria -- solo lo que la identidad causal consulta de verdad.
class FakeFileDao(private val files: List<FileEntity> = emptyList()) : FileDao {
    override suspend fun findById(id: Long): FileEntity? = files.firstOrNull { it.id == id }

    override suspend fun findByPath(path: String): FileEntity? = files.firstOrNull { it.filePath == path }

    override suspend fun findByName(name: String): FileEntity? = files.firstOrNull { it.fileName == name }

    override suspend fun findByRemoteLibraryVideoId(remoteId: String): FileEntity? =
        files.firstOrNull { it.remoteLibraryVideoId == remoteId }

    override fun findAll(): Flow<List<FileEntity>> = flowOf(files)

    override suspend fun findNextUnpublished(platform: String): FileEntity? = null

    override fun countAll(): Flow<Int> = flowOf(files.size)

    override suspend fun findNewerAdjacent(afterEpochMs: Long): FileEntity? = null

    override suspend fun insert(file: FileEntity): Long = throw UnsupportedOperationException()

    override suspend fun update(file: FileEntity) = throw UnsupportedOperationException()
}

// SyncApi fake: solo responde lo causal. Cualquier otro endpoint revienta a
// propósito -- si un test lo alcanza, es que el código está llamando algo que
// no debería.
open class FakeSyncApi : SyncApi {
    val resolveIdentityRequests = mutableListOf<ResolveIdentityRequest>()
    val transitionRequests = mutableListOf<PlatformTransitionRequest>()
    val linkRequests = mutableListOf<ManualPlatformLinkRequest>()

    // Respuesta configurable de resolve-identity (null = la central no conoce
    // el contenido todavía).
    var resolveIdentityResponse: ResolveIdentityResponse = ResolveIdentityResponse()
    var resolveIdentityError: Throwable? = null

    override suspend fun resolveIdentity(body: ResolveIdentityRequest): ResolveIdentityResponse {
        resolveIdentityRequests += body
        resolveIdentityError?.let { throw it }
        return resolveIdentityResponse
    }

    override suspend fun platformTransition(body: PlatformTransitionRequest): Response<PlatformCausalResponse> {
        transitionRequests += body
        return Response.success(PlatformCausalResponse(version = 1))
    }

    override suspend fun manualPlatformLink(body: ManualPlatformLinkRequest): Response<PlatformCausalResponse> {
        linkRequests += body
        return Response.success(PlatformCausalResponse(version = 1))
    }

    private fun unexpected(endpoint: String): Nothing =
        throw AssertionError("el flujo causal no debería llamar a $endpoint")

    override suspend fun getCalendarConfig(): List<CalendarConfigDto> = unexpected("calendar-config")
    override suspend fun updateCalendarConfig(platform: String, body: Map<String, String>) =
        unexpected("calendar-config")
    override suspend fun skipNextCalendarVideo(platform: String, body: SkipNextRequest) = unexpected("skip-next")
    override suspend fun getGroupStats(limit: Int, platform: String?): GroupStatsResponse = unexpected("group-stats")
    override suspend fun getFileStats(fileId: String?, fileName: String?): GroupStatsItemDto = unexpected("file-stats")
    override suspend fun getHistory(limit: Int, offset: Int, platform: String?): UploadHistoryResponse =
        unexpected("history")
    override suspend fun getSyncStats(): SyncStatsDto = unexpected("stats")
    override suspend fun getReview(page: Int, limit: Int): SyncReviewResponseDto = unexpected("review")
    override suspend fun confirmLink(id: String, body: ConfirmLinkRequest) = unexpected("review/link")
    override suspend fun markOrphan(id: String) = unexpected("review/orphan")
    override suspend fun triggerYoutubeSync(): TriggerSyncResponse = unexpected("youtube")
    override suspend fun getPlatformRecent(platform: String, limit: Int, cursor: String?): PlatformRecentPageDto =
        unexpected("platform-recent")
    override suspend fun getCrossMatchCandidates(page: Int, limit: Int): CrossMatchCandidatesResponseDto =
        unexpected("cross-match/candidates")
    override suspend fun resolveCrossMatchSlot(body: ResolveCrossMatchSlotRequest) = unexpected("cross-match/resolve")
    override suspend fun recordPublish(body: RecordPublishRequest) = unexpected("record-publish")
    override suspend fun updateFilePlatforms(body: UpdateFilePlatformsRequest) = unexpected("file-platforms")
}

class FakeDeviceIdProvider(private val id: String = "device-fijo-1234") : DeviceIdProvider {
    var calls = 0
    override suspend fun deviceId(): String {
        calls++
        return id
    }
}

// Helper para armar filas del outbox en los tests sin repetir 14 argumentos.
fun outboxRow(
    id: Long = 0,
    userKey: String = "u1",
    kind: String = PlatformTransitionOutboxEntity.KIND_TRANSITION,
    contentId: String? = null,
    remoteLibraryVideoId: String? = null,
    clientFileId: String? = null,
    fileName: String? = null,
    platform: String = "youtube",
    action: String? = PlatformTransitionOutboxEntity.ACTION_MARK_PUBLISHED,
    baseVersion: Long? = null,
    createdAtEpochMs: Long = 0,
) = PlatformTransitionOutboxEntity(
    id = id,
    userKey = userKey,
    kind = kind,
    contentId = contentId,
    remoteLibraryVideoId = remoteLibraryVideoId,
    clientFileId = clientFileId,
    fileName = fileName,
    platform = platform,
    action = action,
    operationId = "op-$id",
    baseVersion = baseVersion,
    platformId = null,
    platformUrl = null,
    title = null,
    publishedAt = null,
    createdAtEpochMs = createdAtEpochMs,
)

fun fileEntity(
    id: Long,
    clientFileId: String,
    fileName: String,
    remoteLibraryVideoId: String? = null,
) = FileEntity(
    id = id,
    clientFileId = clientFileId,
    fileName = fileName,
    filePath = "/videos/$fileName",
    status = "PENDIENTE",
    contentStatus = "BORRADOR",
    createdAtEpochMs = 0,
    updatedAtEpochMs = 0,
    remoteLibraryVideoId = remoteLibraryVideoId,
)
