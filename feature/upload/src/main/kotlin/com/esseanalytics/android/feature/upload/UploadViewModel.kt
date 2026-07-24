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
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import com.esseanalytics.android.feature.ingest.ImportResult
import com.esseanalytics.android.feature.ingest.ImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    fileRepository: FileRepository,
    private val settingsStore: SettingsStore,
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val importUseCase: ImportUseCase,
    private val syncApi: SyncApi,
    private val tokenStore: TokenStore,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    fun thumbnailUrl(video: RemoteLibraryVideoDto): String? =
        remoteLibraryThumbnailUrl(retrofit.baseUrl(), video._id, video.thumbnailStoredFileName, tokenStore.token)

    // Solo archivos que todavía tienen alguna plataforma pendiente -- no
    // tiene sentido ofrecer "subir" uno que ya está resuelto en las 3.
    val files: StateFlow<List<VideoFile>> = fileRepository.observeAll()
        .map { list -> list.filter { !it.isFullyResolved } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Además de los locales, el picker de Subir deja elegir un video de Nube
    // como fuente (mismo criterio que VideoPickerView en iOS) -- se baja y de
    // ahí en más se trata como cualquier archivo local. Filtrado a lo que
    // todavía tiene alguna plataforma pendiente, igual que `files` arriba.
    private val _remoteVideos = MutableStateFlow<List<RemoteLibraryVideoDto>>(emptyList())
    val remoteVideos: StateFlow<List<RemoteLibraryVideoDto>> = _remoteVideos.asStateFlow()

    private val _importingRemoteId = MutableStateFlow<String?>(null)
    val importingRemoteId: StateFlow<String?> = _importingRemoteId.asStateFlow()

    // Título (file_name) del "próximo" video a publicar por plataforma según
    // el calendario de la central -- mismo dato que ya usa CalendarViewModel,
    // acá para marcar en la lista cuál de los pendientes toca subir. Matchea
    // por VideoFile.fileName (sin id en común entre Room local y FileModel).
    private val _nextUploads = MutableStateFlow<Map<Platform, String>>(emptyMap())
    val nextUploads: StateFlow<Map<Platform, String>> = _nextUploads.asStateFlow()

    fun refreshNextUploads() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { syncApi.getCalendarConfig() } }
                .onSuccess { configs ->
                    _nextUploads.value = configs.mapNotNull { cfg ->
                        val platform = Platform.fromApiValue(cfg.platform)
                        val title = cfg.nextVideo?.title
                        if (platform != null && !title.isNullOrBlank()) platform to title else null
                    }.toMap()
                }
        }
    }

    fun refreshRemoteVideos() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remoteLibraryApi.listVideos().videos } }
                .onSuccess { videos ->
                    _remoteVideos.value = videos.filter { video ->
                        !Platform.publishable.all { it.apiValue in video.platforms || it.apiValue in video.platformsDiscarded }
                    }
                }
        }
    }

    // Baja el video elegido de Nube y lo entrega como un VideoFile normal --
    // el caller lo usa exactamente igual que si viniera de `files` (mismo
    // publish() de acá abajo, sin código nuevo del lado de publicar).
    fun importFromRemote(video: RemoteLibraryVideoDto, onResult: (VideoFile?) -> Unit) {
        viewModelScope.launch {
            _importingRemoteId.value = video._id
            val result = importUseCase.importFromRemoteLibrary(video)
            _importingRemoteId.value = null
            onResult((result as? ImportResult.Success)?.file)
        }
    }

    fun publish(
        file: VideoFile,
        platforms: Set<Platform>,
        title: String,
        description: String,
        thumbnailOffsetMs: Long? = null,
        crossPostFacebook: Boolean = false,
    ) {
        viewModelScope.launch {
            val networkType = if (settingsStore.wifiOnlyUploads.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val metadata = UploadMetadata(
                title = title,
                description = description,
                thumbnailOffsetMs = thumbnailOffsetMs,
                crossPostFacebook = crossPostFacebook,
            )

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
