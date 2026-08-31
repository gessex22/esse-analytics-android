package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.dao.PendingHistoryEventDao
import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

// Outbox persistente para SyncApi.recordPublish -- hallazgo SYNC-02#4 de la
// auditoría de sincronización 2026-08-30 (ver
// content-automation-dashboard/docs/SYNC-01-audit-2026-08-30.md). Antes,
// UploadWorker.reportPublish/RemoteUploadWorker solo hacían un retry acotado
// EN MEMORIA (3 intentos) y si fallaban, se rendían en silencio -- la
// publicación real ya había ocurrido y ya quedó bien en Room (este
// dispositivo), pero la central nunca se enteraba, así que
// Estadísticas/Historial/Calendario en cualquier OTRO dispositivo (o la web)
// jamás mostraban ese evento. Mismo patrón que ya usa local-backend
// (`history_outbox`, SQLite) para su propio camino a la central; mirror
// exacto de SyncAPI.recordPublishOrEnqueue/flushPendingHistoryEvents (iOS).
@Singleton
class HistoryOutbox @Inject constructor(
    private val syncApi: SyncApi,
    private val dao: PendingHistoryEventDao,
) {
    // La publicación real ya ocurrió y ya quedó registrada localmente --
    // esta llamada es solo el espejo hacia la central, así que no hace
    // falta propagar el error al caller. 3 intentos en memoria primero
    // (mismo criterio que antes tenía UploadWorker.reportPublish, y que
    // SyncAPI.recordPublish ya hace del lado iOS) -- un corte de red de
    // un par de segundos se resuelve acá sin necesidad de tocar Room; solo
    // si los 3 fallan queda encolado para que flush() lo reintente más
    // tarde, ya con backoff real (el próximo refresh del Dashboard).
    suspend fun sendOrEnqueue(request: RecordPublishRequest) {
        repeat(3) { attempt ->
            val sent = runCatching { syncApi.recordPublish(request) }.isSuccess
            if (sent) return
            if (attempt < 2) kotlinx.coroutines.delay(750L * (attempt + 1))
        }
        dao.insert(request.toEntity())
    }

    // Vacía el outbox -- un intento por evento y por llamada (sin backoff
    // explícito: si el caller la llama seguido -- ej. cada refresh del
    // Dashboard -- eso ya da varios intentos espaciados naturalmente).
    // Mismo criterio de clasificación que
    // local-backend/src/services/history-outbox.service.ts y
    // SyncAPI.flushPendingHistoryEvents (iOS): un 4xx que no sea 401 (token
    // vencido, se resuelve solo con un re-login) ni 429 (rate limit) es un
    // rechazo permanente -- reintentarlo por siempre sería ruido sin utilidad.
    // Todo lo demás (red caída, 5xx, 401, 429) se deja pending.
    suspend fun flush() {
        val pending = dao.getAll()
        for (entity in pending) {
            val result = runCatching { syncApi.recordPublish(entity.toRequest()) }
            when {
                result.isSuccess -> dao.delete(entity)
                result.exceptionOrNull().isPermanentRejection() -> dao.delete(entity)
                else -> dao.update(entity.copy(attempts = entity.attempts + 1))
            }
        }
    }
}

private fun Throwable?.isPermanentRejection(): Boolean {
    val code = (this as? HttpException)?.code() ?: return false
    return code in 400 until 500 && code != 401 && code != 429
}

private fun RecordPublishRequest.toEntity() = PendingHistoryEventEntity(
    platform = platform,
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

private fun PendingHistoryEventEntity.toRequest() = RecordPublishRequest(
    platform = platform,
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
