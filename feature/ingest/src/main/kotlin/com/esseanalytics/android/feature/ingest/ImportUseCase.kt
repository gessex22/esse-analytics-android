package com.esseanalytics.android.feature.ingest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaProber
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.VideoFile
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
// El archivo SIEMPRE se copia al storage privado de la app (filesDir/videos),
// nunca se referencia solo por el Uri de origen: mismo modelo mental que
// desktop, "la app es dueña de un archivo en disco" (ver el plan).
@Singleton
class ImportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    private val mediaProber: MediaProber,
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
    private val settingsStore: SettingsStore,
) {
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val displayName = queryDisplayName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
            val extension = displayName.substringAfterLast('.', "mp4")

            val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
            val destination = File(videosDir, "${UUID.randomUUID()}.$extension")

            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false

            if (!copied) {
                return@withContext ImportResult.Error("No se pudo leer el archivo seleccionado.")
            }

            val info = mediaProber.probe(destination)
            val durationSeconds = info.durationSeconds?.toInt()

            // Dedup por (fileName, duración, formato) de lo ya importado, NO
            // por el Uri de origen (no es estable entre selecciones) — ver el
            // plan, sección "Ingesta de videos".
            val existing = fileRepository.findByName(displayName)
            if (existing != null && existing.duracionSegundos == durationSeconds && existing.formato == extension) {
                destination.delete()
                return@withContext ImportResult.Duplicate(existing)
            }

            val thumbnailsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val thumbnailFile = File(thumbnailsDir, "${destination.nameWithoutExtension}.jpg")
            val hasThumbnail = thumbnailGenerator.generate(destination, thumbnailFile)

            val videoFile = VideoFile(
                fileName = displayName,
                filePath = destination.absolutePath,
                status = FileStatus.PENDIENTE,
                contentStatus = ContentStatus.BORRADOR,
                duracionSegundos = durationSeconds,
                resolucion = if (info.width != null && info.height != null) "${info.width}x${info.height}" else null,
                formato = extension,
                thumbnailPath = if (hasThumbnail) thumbnailFile.absolutePath else null,
                fechaCreacion = Instant.now(),
            )
            val id = fileRepository.insert(videoFile)

            if (settingsStore.deleteOriginalAfterImport.first()) {
                deleteOriginalBestEffort(uri)
            }

            ImportResult.Success(videoFile.copy(id = id))
        } catch (e: Exception) {
            // Boundary real: Uri externo (Share Sheet/SAF) puede fallar de
            // formas que no controlamos -- permiso revocado, archivo borrado
            // entre que se eligió y se leyó, IO error, etc.
            ImportResult.Error(e.message ?: "Error al importar el video.")
        }
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
