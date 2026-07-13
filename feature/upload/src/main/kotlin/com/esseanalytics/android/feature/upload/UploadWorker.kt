package com.esseanalytics.android.feature.upload

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PlatformVideoRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.Platform
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Un worker por (archivo, plataforma) -- UploadScreen encola uno por cada
// plataforma elegida. Resuelve qué PlatformUploader real usar y, si sale
// bien, deja todo consistente en Room: el link en platform_videos (con el
// fix de unlinkOthersForFile, ver PlatformVideoRepository) y
// FileRepository.onPlatformPublished, que es el MISMO punto de entrada que
// usan las 3 pantallas de subida en desktop (addPlatform +
// resolveOthersAsDiscarded si el modo es Simple).
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fileRepository: FileRepository,
    private val platformVideoRepository: PlatformVideoRepository,
    private val settingsStore: SettingsStore,
    private val youtubeUploader: YoutubeUploader,
    private val instagramUploader: InstagramUploader,
    private val tiktokUploader: TiktokUploader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fileId = inputData.getLong(KEY_FILE_ID, -1L)
        val platform = inputData.getString(KEY_PLATFORM)?.let { Platform.fromApiValue(it) }
        val title = inputData.getString(KEY_TITLE)

        if (fileId < 0 || platform == null || title.isNullOrBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "Datos de subida incompletos."))
        }

        // Facebook es crossposting (Fase 3, ver el plan), no un uploader
        // directo -- no debería llegar acá nunca, pero se cubre el caso.
        val uploader = when (platform) {
            Platform.YOUTUBE -> youtubeUploader
            Platform.INSTAGRAM -> instagramUploader
            Platform.TIKTOK -> tiktokUploader
            Platform.FACEBOOK -> return Result.failure(workDataOf(KEY_ERROR to "Facebook no es una subida directa."))
        }

        val storedPath = fileRepository.findById(fileId)?.filePath ?: return Result.failure()
        val resolved = resolveUploadFile(storedPath)
            ?: return Result.failure(workDataOf(KEY_ERROR to "El archivo local ya no existe."))

        val metadata = UploadMetadata(
            title = title,
            description = inputData.getString(KEY_DESCRIPTION) ?: "",
            privacyStatus = inputData.getString(KEY_PRIVACY) ?: "public",
            thumbnailOffsetMs = inputData.getLong(KEY_THUMBNAIL_OFFSET_MS, -1L).takeIf { it >= 0 },
            crossPostFacebook = inputData.getBoolean(KEY_CROSS_POST_FACEBOOK, false),
        )

        try {
            val result = uploader.upload(resolved.file, metadata) { progress ->
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            }

            return when (result) {
                is UploadResult.Success -> {
                    platformVideoRepository.upsertPublished(
                        platform = platform,
                        platformId = result.platformId,
                        platformUrl = result.platformUrl.ifBlank { null },
                        linkedFileId = fileId,
                        title = title,
                    )
                    fileRepository.onPlatformPublished(fileId, platform, settingsStore.workflowMode.first())

                    // Facebook NO pasa por onPlatformPublished (no está en
                    // Platform.publishable) -- si le pidiéramos eso también,
                    // resolveOthersAsDiscarded en modo Simple descartaría
                    // YouTube/TikTok si seguían pendientes, por un crosspost
                    // que no tiene nada que ver con ellos. Un addPlatform
                    // suelto alcanza, igual que desktop (fileRepo.addPlatform
                    // (fileId, 'facebook') aparte de resolveOthersAsDiscarded).
                    val fbOutputData = when (val fb = result.facebookCrossPost) {
                        is FacebookCrossPostResult.Published -> {
                            platformVideoRepository.upsertPublished(
                                platform = Platform.FACEBOOK,
                                platformId = fb.videoId,
                                platformUrl = fb.url,
                                linkedFileId = fileId,
                                title = title,
                            )
                            fileRepository.addPlatform(fileId, Platform.FACEBOOK)
                            workDataOf(KEY_FACEBOOK_URL to fb.url)
                        }
                        is FacebookCrossPostResult.Failed -> workDataOf(KEY_FACEBOOK_ERROR to fb.message)
                        null -> workDataOf()
                    }

                    val outputData = Data.Builder()
                        .putAll(workDataOf(KEY_RESULT_URL to result.platformUrl))
                        .putAll(fbOutputData)
                        .build()
                    Result.success(outputData)
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
            if (resolved.isTemp) resolved.file.delete()
        }
    }

    // videoFile.filePath puede ser un path local (Share Sheet, o SAF sin
    // permiso persistente, ver ImportUseCase) o un Uri content:// guardado
    // tal cual (SAF con permiso persistente -- nunca se copió nada al
    // importar). Para ESTE caso, y solo acá, se copia a un temporal en
    // cacheDir recién al momento de subir -- PlatformUploader sigue
    // trabajando con un File real, y el temporal se borra apenas termina.
    private suspend fun resolveUploadFile(storedPath: String): ResolvedFile? {
        if (!storedPath.startsWith("content://")) {
            val file = File(storedPath)
            return file.takeIf { it.exists() }?.let { ResolvedFile(it, isTemp = false) }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val tempDir = File(applicationContext.cacheDir, "uploads").apply { mkdirs() }
                val tempFile = File(tempDir, "${UUID.randomUUID()}.mp4")
                applicationContext.contentResolver.openInputStream(Uri.parse(storedPath))?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                ResolvedFile(tempFile, isTemp = true)
            }.getOrNull()
        }
    }

    private data class ResolvedFile(val file: File, val isTemp: Boolean)

    companion object {
        const val KEY_FILE_ID = "fileId"
        const val KEY_PLATFORM = "platform"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_PRIVACY = "privacy"
        const val KEY_THUMBNAIL_OFFSET_MS = "thumbnailOffsetMs"
        const val KEY_CROSS_POST_FACEBOOK = "crossPostFacebook"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URL = "resultUrl"
        const val KEY_FACEBOOK_URL = "facebookUrl"
        const val KEY_FACEBOOK_ERROR = "facebookError"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 3

        fun buildInputData(fileId: Long, platform: Platform, metadata: UploadMetadata) = workDataOf(
            KEY_FILE_ID to fileId,
            KEY_PLATFORM to platform.apiValue,
            KEY_TITLE to metadata.title,
            KEY_DESCRIPTION to metadata.description,
            KEY_PRIVACY to metadata.privacyStatus,
            // -1 = sin elegir (ver doWork, .takeIf { it >= 0 }) -- workDataOf
            // no acepta Long? nullable directo.
            KEY_THUMBNAIL_OFFSET_MS to (metadata.thumbnailOffsetMs ?: -1L),
            // Solo importa cuando platform == INSTAGRAM, ver UploadMetadata.
            KEY_CROSS_POST_FACEBOOK to metadata.crossPostFacebook,
        )
    }
}
