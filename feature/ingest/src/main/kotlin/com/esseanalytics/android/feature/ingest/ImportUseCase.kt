package com.esseanalytics.android.feature.ingest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaProber
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Success(val file: VideoFile) : ImportResult
    data class Duplicate(val existing: VideoFile) : ImportResult
    data class Error(val message: String) : ImportResult
}

// Camino único para las dos formas de ingesta (Share Sheet + selector SAF,
// ver el plan) — ambas terminan pasando por acá con un Uri de content://.
//
// Share Sheet NO soporta permiso persistente sobre el Uri (muere apenas
// termina esa Activity) -- ahí SIEMPRE hay que copiar a storage privado, no
// hay otra opción confiable si el archivo se va a subir más adelante. El
// selector SAF (OpenMultipleDocuments) SÍ lo soporta: si canPersist=true y
// takePersistableUriPermission funciona, no se copia nada -- el Uri en sí
// queda guardado como filePath y se lee directo de ahí (metadata, miniatura,
// y al momento de subir, UploadWorker copia a un temporal recién ahí).
@Singleton
class ImportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    private val mediaProber: MediaProber,
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
    private val settingsStore: SettingsStore,
    private val remoteLibraryApi: RemoteLibraryApi,
) {
    suspend fun import(uri: Uri, canPersist: Boolean = false): ImportResult = withContext(Dispatchers.IO) {
        var copiedFile: File? = null
        try {
            val displayName = queryDisplayName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
            val extension = displayName.substringAfterLast('.', "mp4")

            val persisted = canPersist && tryPersistPermission(uri)

            val (mediaSource, filePathForStorage) = if (persisted) {
                MediaSource.ContentUri(uri) to uri.toString()
            } else {
                val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
                val destination = File(videosDir, "${UUID.randomUUID()}.$extension")
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied) {
                    return@withContext ImportResult.Error("No se pudo leer el archivo seleccionado.")
                }
                copiedFile = destination
                MediaSource.LocalFile(destination) to destination.absolutePath
            }

            val info = mediaProber.probe(mediaSource)
            val durationSeconds = info.durationSeconds?.toInt()

            // Dedup por (fileName, duración, formato) de lo ya importado, NO
            // por el Uri de origen (no es estable entre selecciones) — ver el
            // plan, sección "Ingesta de videos".
            val existing = fileRepository.findByName(displayName)
            if (existing != null && existing.duracionSegundos == durationSeconds && existing.formato == extension) {
                copiedFile?.delete()
                if (persisted) releasePersistedPermissionBestEffort(uri)
                return@withContext ImportResult.Duplicate(existing)
            }

            val thumbnailsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val thumbnailFile = File(thumbnailsDir, "${UUID.randomUUID()}.jpg")
            val hasThumbnail = thumbnailGenerator.generate(mediaSource, thumbnailFile)

            val videoFile = VideoFile(
                fileName = displayName,
                filePath = filePathForStorage,
                status = FileStatus.PENDIENTE,
                contentStatus = ContentStatus.BORRADOR,
                duracionSegundos = durationSeconds,
                resolucion = if (info.width != null && info.height != null) "${info.width}x${info.height}" else null,
                formato = extension,
                thumbnailPath = if (hasThumbnail) thumbnailFile.absolutePath else null,
                fechaCreacion = Instant.now(),
            )
            val id = fileRepository.insert(videoFile)

            // Sin sentido si no se copió nada -- "eliminar original" borraría
            // la ÚNICA copia que existe (ver persisted arriba).
            if (!persisted && settingsStore.deleteOriginalAfterImport.first()) {
                deleteOriginalBestEffort(uri)
            }

            ImportResult.Success(videoFile.copy(id = id))
        } catch (e: Exception) {
            // Boundary real: Uri externo (Share Sheet/SAF) puede fallar de
            // formas que no controlamos -- permiso revocado, archivo borrado
            // entre que se eligió y se leyó, IO error, etc.
            copiedFile?.delete()
            ImportResult.Error(e.message ?: "Error al importar el video.")
        }
    }

    // Mirror de ImportUseCase.importFromRemoteLibrary en iOS -- deja publicar
    // desde Subir un video que vive en Nube: se baja a storage propio y de ahí
    // en más se trata exactamente igual que cualquier archivo local (mismo
    // UploadWorker, sin código nuevo del lado de publicar).
    //
    // Match por remoteLibraryVideoId primero (link explícito) -- si ya se
    // había bajado este MISMO video antes, se reusa el archivo local en vez
    // de descargarlo de nuevo (evita duplicados en Videos → Todos apenas se
    // toca "Publicar" más de una vez desde Nube).
    suspend fun importFromRemoteLibrary(video: RemoteLibraryVideoDto): ImportResult = withContext(Dispatchers.IO) {
        try {
            val existing = fileRepository.findByRemoteLibraryVideoId(video._id)
            if (existing != null) {
                val merged = mergePlatformsFromRemote(existing, video)
                if (merged != existing) fileRepository.update(merged)
                return@withContext ImportResult.Success(merged)
            }

            val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
            val extension = video.fileName.substringAfterLast('.', "mp4")
            val destination = File(videosDir, "${UUID.randomUUID()}.$extension")

            remoteLibraryApi.streamVideo(video._id).byteStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }

            val mediaSource = MediaSource.LocalFile(destination)
            val thumbnailsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val thumbnailFile = File(thumbnailsDir, "${UUID.randomUUID()}.jpg")
            val hasThumbnail = thumbnailGenerator.generate(mediaSource, thumbnailFile)

            val videoFile = VideoFile(
                fileName = video.fileName,
                filePath = destination.absolutePath,
                status = FileStatus.PENDIENTE,
                contentStatus = ContentStatus.BORRADOR,
                platforms = video.platforms.mapNotNull(Platform::fromApiValue),
                platformsDiscarded = video.platformsDiscarded.mapNotNull(Platform::fromApiValue),
                duracionSegundos = video.durationSeconds?.toInt(),
                resolucion = video.resolution,
                formato = video.formato ?: extension,
                thumbnailPath = if (hasThumbnail) thumbnailFile.absolutePath else null,
                fechaCreacion = video.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
                remoteLibraryVideoId = video._id,
            )
            val id = fileRepository.insert(videoFile)
            ImportResult.Success(videoFile.copy(id = id))
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "No se pudo bajar el video de la nube.")
        }
    }

    // Un video puede haberse resuelto en más plataformas en Nube desde la
    // última vez que se bajó acá (ej. publicado desde la PC mientras tanto) --
    // se completa lo que falte localmente, nunca se pisa un estado que el
    // teléfono ya tenga resuelto.
    private fun mergePlatformsFromRemote(local: VideoFile, video: RemoteLibraryVideoDto): VideoFile {
        val remotePlatforms = video.platforms.mapNotNull(Platform::fromApiValue)
        val remoteDiscarded = video.platformsDiscarded.mapNotNull(Platform::fromApiValue)
        val newPlatforms = (local.platforms + remotePlatforms).distinct()
        val newDiscarded = (local.platformsDiscarded + remoteDiscarded).distinct().filter { it !in newPlatforms }
        return if (newPlatforms != local.platforms || newDiscarded != local.platformsDiscarded) {
            local.copy(platforms = newPlatforms, platformsDiscarded = newDiscarded)
        } else {
            local
        }
    }

    // Solo funciona para Uris de SAF (ACTION_OPEN_DOCUMENT/OpenMultipleDocuments)
    // -- Share Sheet no otorga FLAG_GRANT_PERSISTABLE_URI_PERMISSION, esto
    // tira SecurityException ahí y cae al camino de copiar como siempre.
    private fun tryPersistPermission(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    private fun releasePersistedPermissionBestEffort(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    // Mejor esfuerzo, a propósito: en Android 10+ borrar un archivo que la
    // app no es dueña (el caso típico de Share Sheet, que solo suele otorgar
    // permiso de LECTURA) requiere un flujo de confirmación del sistema
    // (MediaStore.createDeleteRequest, con IntentSender) que no vale la pena
    // acá -- ya tenemos la copia guardada, así que si esto falla se ignora en
    // silencio en vez de mostrarle un error al usuario por algo que no rompió
    // la importación en sí.
    private fun deleteOriginalBestEffort(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
}
