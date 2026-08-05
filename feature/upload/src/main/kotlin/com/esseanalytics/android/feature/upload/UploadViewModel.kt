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
import com.esseanalytics.android.core.datastore.PendingBatchStore
import com.esseanalytics.android.core.datastore.PendingPublishBatch
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.model.WorkflowMode
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import com.esseanalytics.android.feature.ingest.ImportResult
import com.esseanalytics.android.feature.ingest.ImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    private val settingsStore: SettingsStore,
    private val pendingBatchStore: PendingBatchStore,
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val importUseCase: ImportUseCase,
    private val syncRepository: SyncRepository,
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

    // Antes un fallo acá (ej. video sin bytes porque el storage dinámico ya
    // los liberó, ver remote-library-retention.service.ts) volvía onResult(null)
    // sin ningún rastro visible -- el spinner se apagaba y listo, sin decirle
    // al usuario qué pasó ni que podía reintentar con otro video.
    private val _remoteImportError = MutableStateFlow<String?>(null)
    val remoteImportError: StateFlow<String?> = _remoteImportError.asStateFlow()

    fun clearRemoteImportError() {
        _remoteImportError.value = null
    }

    // Título (file_name) del "próximo" video a publicar por plataforma según
    // el calendario de la central -- mismo dato que ya usa CalendarViewModel,
    // acá para marcar en la lista cuál de los pendientes toca subir. Matchea
    // por VideoFile.fileName (sin id en común entre Room local y FileModel).
    private val _nextUploads = MutableStateFlow<Map<Platform, String>>(emptyMap())
    val nextUploads: StateFlow<Map<Platform, String>> = _nextUploads.asStateFlow()

    // Lote de publicación en curso o recién terminado (Fase 2 del plan de
    // estabilidad/UX) -- agregado a partir de los WorkInfo individuales de
    // cada plataforma (observeWork), no un estado propio inventado: WorkManager
    // sigue siendo la única fuente de verdad de qué pasó con cada subida.
    private val _activeBatch = MutableStateFlow<PublishBatchState?>(null)
    val activeBatch: StateFlow<PublishBatchState?> = _activeBatch.asStateFlow()
    private var batchObserverJob: Job? = null

    // Si había un lote en curso cuando el proceso murió, esto trae el fileId
    // para que UploadScreen lo auto-seleccione en vez de arrancar en la lista
    // -- sin esto la tarjeta persistente/el resumen de esta fase nunca se
    // llegaban a mostrar tras reabrir la app a la fuerza.
    private val _pendingBatchFileId = MutableStateFlow<Long?>(null)
    val pendingBatchFileId: StateFlow<Long?> = _pendingBatchFileId.asStateFlow()

    init {
        viewModelScope.launch {
            val pending = pendingBatchStore.currentValue() ?: return@launch
            val file = fileRepository.findById(pending.fileId)
            if (file == null) {
                // El archivo ya no existe (se borró desde otro lado) -- no
                // hay nada que reconstruir.
                pendingBatchStore.clear()
                return@launch
            }
            _pendingBatchFileId.value = pending.fileId
            val platforms = pending.platforms.mapNotNull { Platform.fromApiValue(it) }
            if (platforms.isNotEmpty()) startBatchObserver(pending.operationId, file, platforms)
        }
    }

    fun refreshNextUploads() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { syncRepository.getCalendarConfig() } }
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
            if (result is ImportResult.Error) _remoteImportError.value = result.message
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
        if (platforms.isEmpty()) return
        viewModelScope.launch {
            if (settingsStore.workflowMode.first() == WorkflowMode.SIMPLE) {
                fileRepository.resolvePublicationSelection(file.id, platforms)
            }
            val networkType = if (settingsStore.wifiOnlyUploads.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val metadata = UploadMetadata(
                title = title,
                description = description,
                thumbnailOffsetMs = thumbnailOffsetMs,
                crossPostFacebook = crossPostFacebook,
            )
            val operationId = UUID.randomUUID().toString()
            val platformList = platforms.toList()

            platformList.forEach { platform ->
                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(UploadWorker.buildInputData(file.id, platform, metadata, operationId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

                workManager.enqueueUniqueWork(
                    uniqueWorkName(file.id, platform),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }

            pendingBatchStore.save(
                PendingPublishBatch(
                    operationId = operationId,
                    fileId = file.id,
                    fileDisplayName = file.fileName,
                    platforms = platformList.map { it.apiValue },
                ),
            )
            startBatchObserver(operationId, file, platformList)
        }
    }

    // No hay forma de abortar de verdad un upload ya en curso en WorkManager
    // sin dejarlo en un estado ambiguo del lado del servidor -- lo que sí se
    // puede es cancelar las que todavía están ENQUEUED/BLOCKED (no
    // arrancaron de verdad). La que está RUNNING sigue hasta que termine;
    // su resultado real se respeta cuando llegue.
    fun cancelBatch() {
        val batch = _activeBatch.value ?: return
        batch.platforms
            .filter { it.stage == PlatformPublishStage.PENDING }
            .forEach { workManager.cancelUniqueWork(uniqueWorkName(batch.fileId, it.platform)) }
    }

    fun dismissBatch() {
        batchObserverJob?.cancel()
        batchObserverJob = null
        _activeBatch.value = null
        _pendingBatchFileId.value = null
        viewModelScope.launch { pendingBatchStore.clear() }
    }

    fun retryFailedInBatch(
        file: VideoFile,
        title: String,
        description: String,
        thumbnailOffsetMs: Long? = null,
        crossPostFacebook: Boolean = false,
    ) {
        val batch = _activeBatch.value ?: return
        val retriable = batch.platforms
            .filter { it.stage == PlatformPublishStage.FAILED || it.stage == PlatformPublishStage.CANCELLED }
            .map { it.platform }
            .toSet()
        if (retriable.isEmpty()) return
        publish(file, retriable, title, description, thumbnailOffsetMs, crossPostFacebook)
    }

    private fun startBatchObserver(operationId: String, file: VideoFile, platforms: List<Platform>) {
        batchObserverJob?.cancel()
        batchObserverJob = viewModelScope.launch {
            combine(platforms.map { platform -> observeWork(file.id, platform).map { platform to it } }) { pairs -> pairs.toList() }
                .collect { pairs ->
                    val states = pairs.map { (platform, info) ->
                        PlatformPublishState(
                            platform = platform,
                            stage = info.toPlatformStage(),
                            progress = info?.takeIf { it.state == WorkInfo.State.RUNNING }
                                ?.progress?.getFloat(UploadWorker.KEY_PROGRESS, 0f),
                            finalUrl = info?.takeIf { it.state == WorkInfo.State.SUCCEEDED }
                                ?.outputData?.getString(UploadWorker.KEY_RESULT_URL),
                            error = info?.takeIf { it.state == WorkInfo.State.FAILED }
                                ?.outputData?.getString(UploadWorker.KEY_ERROR),
                        )
                    }
                    val stage = deriveBatchStage(states)
                    _activeBatch.value = PublishBatchState(operationId, file.id, file.fileName, stage, states)
                    if (stage != BatchStage.PREPARING && stage != BatchStage.UPLOADING) {
                        // Lote resuelto (total, parcial o fallido) -- ya no
                        // hace falta reconstruirlo si el proceso muere ahora.
                        pendingBatchStore.clear()
                    }
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
