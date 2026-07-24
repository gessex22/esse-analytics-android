package com.esseanalytics.android.core.database

import androidx.room.withTransaction
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.util.toDomain
import com.esseanalytics.android.core.database.util.toEntity
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.model.WorkflowMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Puerto directo de local-backend/src/db/file.repo.ts — en particular
// addPlatform y resolveOthersAsDiscarded, la lógica de negocio más fácil de
// arruinar si se reescribe de memoria en vez de mirar el original. Si el
// comportamiento de "modo simple" cambia acá, cambiarlo primero en desktop y
// después portar de nuevo, no al revés.
@Singleton
class FileRepository @Inject constructor(
    private val db: EsseAnalyticsDatabase,
    private val fileDao: FileDao,
) {
    fun observeAll(): Flow<List<VideoFile>> = fileDao.findAll().map { list -> list.map { it.toDomain() } }

    fun observeCount(): Flow<Int> = fileDao.countAll()

    suspend fun findById(id: Long): VideoFile? = fileDao.findById(id)?.toDomain()

    suspend fun findByName(name: String): VideoFile? = fileDao.findByName(name)?.toDomain()

    suspend fun findByRemoteLibraryVideoId(remoteId: String): VideoFile? =
        fileDao.findByRemoteLibraryVideoId(remoteId)?.toDomain()

    suspend fun findByPath(path: String): VideoFile? = fileDao.findByPath(path)?.toDomain()

    suspend fun findNextUnpublished(platform: Platform): VideoFile? =
        fileDao.findNextUnpublished(platform.apiValue)?.toDomain()

    suspend fun findNewerAdjacent(after: VideoFile): VideoFile? {
        val afterMs = after.fechaCreacion?.toEpochMilli() ?: return null
        return fileDao.findNewerAdjacent(afterMs)?.toDomain()
    }

    suspend fun insert(file: VideoFile): Long = fileDao.insert(file.toEntity())

    suspend fun update(file: VideoFile) {
        fileDao.update(file.copy(updatedAt = Instant.now()).toEntity())
    }

    // Una subida real SIEMPRE gana: agrega la plataforma a `platforms`, y si
    // había quedado en `platformsDiscarded` (por el auto-descarte de abajo),
    // la saca de ahí. Idempotente — no escribe nada si no hay cambios.
    suspend fun addPlatform(fileId: Long, platform: Platform) = db.withTransaction {
        val file = fileDao.findById(fileId)?.toDomain() ?: return@withTransaction
        val newPlatforms = if (platform in file.platforms) file.platforms else file.platforms + platform
        val newDiscarded = if (platform in file.platformsDiscarded) {
            file.platformsDiscarded.filter { it != platform }
        } else {
            file.platformsDiscarded
        }
        if (newPlatforms != file.platforms || newDiscarded != file.platformsDiscarded) {
            update(file.copy(platforms = newPlatforms, platformsDiscarded = newDiscarded))
        }
    }

    // Contraparte de addPlatform -- vuelve la plataforma a "pendiente" (no la
    // descarta). Se usa al borrar manualmente el link de una plataforma, mismo
    // criterio que removePlatform en iOS (FileEntity.swift).
    suspend fun removePlatform(fileId: Long, platform: Platform) = db.withTransaction {
        val file = fileDao.findById(fileId)?.toDomain() ?: return@withTransaction
        if (platform !in file.platforms) return@withTransaction
        update(file.copy(platforms = file.platforms.filter { it != platform }))
    }

    // Ciclo pendiente -> publicado -> descartado -> pendiente -- mirror de
    // togglePlatform en iOS (VideoDetailView.swift) y RemoteLibraryView.tsx
    // (desktop). Editor manual del estado por plataforma, no depende de una
    // subida real vía la API (eso es onPlatformPublished, de abajo).
    suspend fun cyclePlatformStatus(fileId: Long, platform: Platform) = db.withTransaction {
        val file = fileDao.findById(fileId)?.toDomain() ?: return@withTransaction
        val (newPlatforms, newDiscarded) = when {
            platform in file.platforms ->
                file.platforms.filter { it != platform } to file.platformsDiscarded + platform
            platform in file.platformsDiscarded ->
                file.platforms to file.platformsDiscarded.filter { it != platform }
            else ->
                file.platforms + platform to file.platformsDiscarded
        }
        update(file.copy(platforms = newPlatforms, platformsDiscarded = newDiscarded))
    }

    // Modo Simple: al publicar de verdad en una plataforma, las OTRAS 2 quedan
    // descartadas — pero SOLO si todavía estaban pendientes (ni publicadas ni
    // ya descartadas antes). No pisa un estado que el usuario ya haya resuelto.
    suspend fun resolveOthersAsDiscarded(fileId: Long, published: Platform) = db.withTransaction {
        val file = fileDao.findById(fileId)?.toDomain() ?: return@withTransaction
        val others = Platform.publishable.filter { it != published }
        val stillPending = others.filter { it !in file.platforms && it !in file.platformsDiscarded }
        if (stillPending.isEmpty()) return@withTransaction
        update(file.copy(platformsDiscarded = file.platformsDiscarded + stillPending))
    }

    // Punto de entrada único que usan las 3 pantallas de subida (feature:upload)
    // tras confirmar una publicación real — combina addPlatform con el
    // auto-descarte de resolveOthersAsDiscarded SOLO si el modo es Simple,
    // igual que en local-backend (uploadToInstagram/Youtube/Tiktok controllers,
    // cada uno hace ambas llamadas condicionadas a workflow_mode === 'simple').
    suspend fun onPlatformPublished(fileId: Long, platform: Platform, workflowMode: WorkflowMode) {
        addPlatform(fileId, platform)
        if (workflowMode == WorkflowMode.SIMPLE) {
            resolveOthersAsDiscarded(fileId, platform)
        }
    }
}
