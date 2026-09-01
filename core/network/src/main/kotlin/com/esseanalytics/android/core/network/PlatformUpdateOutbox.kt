package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.dao.PendingPlatformUpdateDao
import com.esseanalytics.android.core.database.entity.PendingPlatformUpdateEntity
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.UpdateFilePlatformsRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

// Outbox persistente para SyncApi.updateFilePlatforms -- SYNC-01 #4 parte b
// (content-automation-dashboard/docs/SYNC-01-audit-2026-08-30.md): antes,
// VideoDetailViewModel.syncPlatformsToCentralIfNeeded solo hacía un
// runCatching puro alrededor de esta llamada -- un corte de red al tocar o
// descartar una plataforma dejaba el cambio guardado localmente (Room) pero
// la central nunca se enteraba, sin ningún reintento (hasta que el usuario
// volviera a tocar esa misma plataforma a mano). Mirror de
// SyncAPI.updateFilePlatformsOrEnqueue/flushPendingPlatformUpdates (iOS), con
// la misma diferencia clave respecto a HistoryOutbox: acá se identifica por
// fileName (primary key, REPLACE en el DAO) -- si el archivo se toca varias
// veces mientras está offline, no tiene sentido encolar cada intermedio (la
// llamada real manda el estado COMPLETO, no un delta), así que solo importa
// el último.
@Singleton
class PlatformUpdateOutbox @Inject constructor(
    private val syncApi: SyncApi,
    private val dao: PendingPlatformUpdateDao,
) {
    suspend fun sendOrEnqueue(
        fileName: String,
        remoteLibraryVideoId: String?,
        platforms: List<Platform>,
        platformsDiscarded: List<Platform>,
    ) {
        val request = UpdateFilePlatformsRequest(
            fileName = fileName,
            remoteLibraryVideoId = remoteLibraryVideoId,
            platforms = platforms.map { it.apiValue },
            platformsDiscarded = platformsDiscarded.map { it.apiValue },
        )
        val sent = runCatching { syncApi.updateFilePlatforms(request) }.isSuccess
        if (sent) {
            // Si había un pendiente viejo de un fallo anterior para este
            // archivo, ya está de más -- lo que acaba de mandarse es más
            // reciente que lo que fuera que hubiera encolado.
            dao.deleteByFileName(fileName)
            return
        }
        dao.upsert(
            PendingPlatformUpdateEntity(
                fileName = fileName,
                remoteLibraryVideoId = remoteLibraryVideoId,
                platforms = platforms.toCsv(),
                platformsDiscarded = platformsDiscarded.toCsv(),
            ),
        )
    }

    // Vacía el outbox -- mismo criterio de clasificación de errores que
    // HistoryOutbox.flush()/SyncAPI.flushPendingPlatformUpdates (iOS): un
    // 4xx que no sea 401/429 es rechazo permanente, todo lo demás queda
    // pending para el próximo intento.
    suspend fun flush() {
        val pending = dao.getAll()
        for (entity in pending) {
            val request = UpdateFilePlatformsRequest(
                fileName = entity.fileName,
                remoteLibraryVideoId = entity.remoteLibraryVideoId,
                platforms = entity.platforms.toPlatformList().map { it.apiValue },
                platformsDiscarded = entity.platformsDiscarded.toPlatformList().map { it.apiValue },
            )
            val result = runCatching { syncApi.updateFilePlatforms(request) }
            when {
                result.isSuccess -> dao.delete(entity)
                result.exceptionOrNull().isPlatformUpdatePermanentRejection() -> dao.delete(entity)
                else -> dao.update(entity.copy(attempts = entity.attempts + 1))
            }
        }
    }
}

private fun Throwable?.isPlatformUpdatePermanentRejection(): Boolean {
    val code = (this as? HttpException)?.code() ?: return false
    return code in 400 until 500 && code != 401 && code != 429
}

private fun List<Platform>.toCsv(): String = joinToString(",") { it.apiValue }
private fun String.toPlatformList(): List<Platform> =
    if (isBlank()) emptyList() else split(",").mapNotNull { Platform.fromApiValue(it.trim()) }
