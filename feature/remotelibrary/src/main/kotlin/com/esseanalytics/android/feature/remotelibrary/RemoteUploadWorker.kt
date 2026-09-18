package com.esseanalytics.android.feature.remotelibrary

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.HistoryOutbox
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import com.esseanalytics.android.feature.upload.InstagramUploader
import com.esseanalytics.android.feature.upload.TiktokUploader
import com.esseanalytics.android.feature.upload.UploadMetadata
import com.esseanalytics.android.feature.upload.UploadResult
import com.esseanalytics.android.feature.upload.YoutubeUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.time.Instant

// Publica un video de la cola remota (central) directo a una plataforma --
// SIN tocar UploadWorker/FileRepository/PlatformVideoRepository (ver Parte
// C.2 del plan): descarga el video a un temporal en cacheDir (misma técnica
// que UploadWorker.resolveUploadFile() para content://) y le pasa ese File a
// los uploaders YA existentes de feature:upload, sin cambiarlos -- no
// necesitan saber que el origen es la central y no el storage del teléfono.
@HiltWorker
class RemoteUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val historyOutbox: HistoryOutbox,
    private val settingsStore: SettingsStore,
    private val youtubeUploader: YoutubeUploader,
    private val instagramUploader: InstagramUploader,
    private val tiktokUploader: TiktokUploader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val platform = inputData.getString(KEY_PLATFORM)?.let { Platform.fromApiValue(it) }
            ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: return Result.failure(workDataOf(KEY_ERROR to "Datos de subida incompletos."))
        // fileName real del video de la cola remota -- antes se mandaba `title`
        // como fileName a record-publish (quedaba mal el match en la central si
        // el título editado no coincidía con el nombre del archivo). Fallback a
        // title solo si el caller viejo no lo pasó.
        val fileName = inputData.getString(KEY_FILE_NAME)?.takeIf { it.isNotBlank() } ?: title

        // Facebook es crossposting, no una subida directa -- no debería llegar
        // acá nunca (ver Platform.publishable), pero se cubre el caso igual
        // que UploadWorker.
        val uploader = when (platform) {
            Platform.YOUTUBE -> youtubeUploader
            Platform.INSTAGRAM -> instagramUploader
            Platform.TIKTOK -> tiktokUploader
            Platform.FACEBOOK -> return Result.failure(workDataOf(KEY_ERROR to "Facebook no es una subida directa."))
        }

        val tempFile = downloadToTemp(videoId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "No se pudo descargar el video de la central."))

        val metadata = UploadMetadata(title = title, description = inputData.getString(KEY_DESCRIPTION) ?: "")

        try {
            val result = uploader.upload(tempFile, metadata) { progress ->
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            }

            return when (result) {
                is UploadResult.Success -> {
                    // record-publish (vía HistoryOutbox) es el ÚNICO canal a la
                    // central/Nube tras una publicación real -- ya NO se PATCHea
                    // Biblioteca remota directo (platforms). La central deriva el
                    // estado publicado de este video de la cola del propio evento
                    // record-publish (que ya trae remoteLibraryVideoId), sin un
                    // segundo camino que pueda quedar desincronizado. HistoryOutbox
                    // (hallazgo SYNC-02#4) encola de forma durable si falla.
                    historyOutbox.sendOrEnqueue(
                        RecordPublishRequest(
                            platform = platform.apiValue,
                            platformId = result.platformId,
                            platformUrl = result.platformUrl.ifBlank { null },
                            fileName = fileName,
                            remoteLibraryVideoId = videoId,
                            title = title,
                            publishedAt = Instant.now().toString(),
                            deviceId = settingsStore.getOrCreateInstallId(),
                            deviceName = settingsStore.getOrCreateDeviceName(),
                        ),
                    )
                    Result.success(workDataOf(KEY_RESULT_URL to result.platformUrl))
                }
                is UploadResult.Failure -> {
                    if (result.retryable && runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        Result.failure(workDataOf(KEY_ERROR to result.message))
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun downloadToTemp(videoId: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val body = remoteLibraryApi.streamVideo(videoId)
            val tempDir = File(applicationContext.cacheDir, "remote-uploads").apply { mkdirs() }
            val tempFile = File(tempDir, "${UUID.randomUUID()}.mp4")
            body.byteStream().use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile
        }.getOrNull()
    }

    companion object {
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_PLATFORM = "platform"
        const val KEY_TITLE = "title"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_DESCRIPTION = "description"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URL = "resultUrl"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 3

        fun buildInputData(
            videoId: String,
            platform: Platform,
            title: String,
            fileName: String,
            description: String,
        ) = workDataOf(
            KEY_VIDEO_ID to videoId,
            KEY_PLATFORM to platform.apiValue,
            KEY_TITLE to title,
            KEY_FILE_NAME to fileName,
            KEY_DESCRIPTION to description,
        )
    }
}
