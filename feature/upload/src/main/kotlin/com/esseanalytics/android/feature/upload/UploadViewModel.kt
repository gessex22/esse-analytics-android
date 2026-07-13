package com.esseanalytics.android.feature.upload

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    fileRepository: FileRepository,
    private val settingsStore: SettingsStore,
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    // Solo archivos que todavía tienen alguna plataforma pendiente -- no
    // tiene sentido ofrecer "subir" uno que ya está resuelto en las 3.
    val files: StateFlow<List<VideoFile>> = fileRepository.observeAll()
        .map { list -> list.filter { !it.isFullyResolved } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun publish(
        file: VideoFile,
        platforms: Set<Platform>,
        title: String,
        description: String,
        thumbnailOffsetMs: Long? = null,
    ) {
        viewModelScope.launch {
            val networkType = if (settingsStore.wifiOnlyUploads.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val metadata = UploadMetadata(title = title, description = description, thumbnailOffsetMs = thumbnailOffsetMs)

            platforms.forEach { platform ->
                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(UploadWorker.buildInputData(file.id, platform, metadata))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

                workManager.enqueueUniqueWork(
                    uniqueWorkName(file.id, platform),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }

    fun observeWork(fileId: Long, platform: Platform): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(fileId, platform)).map { it.firstOrNull() }

    // Vista previa en vivo del scrubber de portada -- no confundir con
    // AndroidFrameThumbnailGenerator.generate() (miniatura de biblioteca, ya
    // recortada a un tamaño fijo): acá se quiere el frame tal cual, tamaño
    // real, para que el usuario vea exactamente lo que va a elegir.
    suspend fun captureThumbnailPreview(filePath: String, atMs: Long): Bitmap? =
        thumbnailGenerator.captureFrame(MediaSource.fromStoredPath(filePath), atMs)

    private fun uniqueWorkName(fileId: Long, platform: Platform) = "upload_${fileId}_${platform.apiValue}"
}
