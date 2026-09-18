package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PlatformTransitionRepository
import com.esseanalytics.android.core.database.PlatformVideoRepository
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.CausalPlatformOutbox
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val platformVideoRepository: PlatformVideoRepository,
    private val platformTransitionRepository: PlatformTransitionRepository,
    private val causalPlatformOutbox: CausalPlatformOutbox,
    private val syncApi: SyncApi,
    private val tokenStore: TokenStore,
    @PlatformOkHttp private val platformOkHttpClient: OkHttpClient,
) : ViewModel(), VideoDetailEditor {
    private var currentFile: VideoFile? = null
    private val _file = MutableStateFlow<VideoFile?>(null)
    val file: StateFlow<VideoFile?> = _file.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setInitial(file: VideoFile) {
        currentFile = file
        if (_file.value?.id != file.id) _file.value = file
    }

    suspend fun existingLink(fileId: Long, platform: Platform): String? =
        platformVideoRepository.findByLinkedFileAndPlatform(fileId, platform)?.platformUrl

    fun linkedPlatforms(fileId: Long): Flow<Set<Platform>> =
        platformVideoRepository.observeByFile(fileId).map { list -> list.map { it.platform }.toSet() }

    // Bug real SYNC-01 #2 (auditoría 2026-08-30, corregido 2026-09-01): un
    // video importado desde Biblioteca Remota puede traer la badge de
    // "publicado" copiada de OTRO dispositivo sin que este dispositivo
    // tenga el link real -- existingLink() de arriba solo lee
    // platform_videos local, que nunca se completaba desde la central para
    // este caso. Mostraba "Publicado · Sin enlace" para un link que sí
    // existe (en la central y en Biblioteca Remota), solo que este
    // dispositivo nunca lo pidió.
    //
    // Se pide getFileStats (mismo endpoint que ya usa Dashboard, solo
    // devuelve slots con platformId real -- nunca badge_only, ver
    // BUG-2026-08-15-03 en el backend) al abrir el detalle, y se completa
    // localmente lo que falte. Best-effort: sin red o sin match, no
    // bloquea nada. matchStatus "remote" (no "manual"): el link no se
    // resolvió en ESTE dispositivo, se heredó de otro -- mismo criterio
    // que LocalVideoDetailAdapter.prepare() en iOS.
    // No hace falta refrescar currentFile/_file al final: linkedPlatforms()
    // ya es un Flow reactivo sobre Room (observeByFile), así que insertar acá
    // alcanza para que la UI (VideoDetailSheet.kt) se entere sola.
    suspend fun backfillRemoteLinks(file: VideoFile) {
        val stats = runCatching { syncApi.getFileStats(fileName = file.fileName) }.getOrNull() ?: return
        for (platform in file.platforms) {
            if (platformVideoRepository.findByLinkedFileAndPlatform(file.id, platform) != null) continue
            val slot = stats.platforms[platform.apiValue] ?: continue
            platformVideoRepository.upsertPublished(
                platform = platform,
                platformId = slot.platformId,
                platformUrl = slot.platformUrl,
                linkedFileId = file.id,
                title = slot.title,
                publishedAt = null,
                matchStatus = "remote",
            )
        }
    }

    // El toggle deja de PATCHear la central por snapshot (updateFilePlatforms,
    // PlatformUpdateOutbox) y de PATCHear Biblioteca remota directo: cada cambio
    // viaja como una INTENCIÓN causal (platform-transition) encolada EN LA MISMA
    // transacción Room que el ciclo de badge (ver
    // PlatformTransitionRepository.toggleAndEnqueue). Si la intención no se pudo
    // encolar (Room revirtió), el badge NO cambia. El drenado real a la central
    // lo hace CausalPlatformOutbox (acá solo se dispara, best-effort).
    override fun togglePlatform(platform: Platform) {
        val file = currentFile ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            val userKey = tokenStore.currentUser?.id
            val updated = if (userKey != null) {
                platformTransitionRepository.toggleAndEnqueue(file.id, platform, userKey)
            } else {
                // Sin sesión no hay a quién atribuir la intención causal -- se
                // aplica solo el cambio local para no dejar la UI muerta;
                // sincroniza cuando vuelva a haber sesión + identidad.
                fileRepository.cyclePlatformStatus(file.id, platform)
                fileRepository.findById(file.id)
            }
            if (updated == null) {
                _errorMessage.value = "No se pudo registrar el cambio. Probá de nuevo."
                return@launch
            }
            currentFile = updated
            _file.value = updated
            triggerFlush()
        }
    }

    // Guardar un link viaja como manual-platform-link (con contentId +
    // operationId, resuelto por el outbox); borrar el link (campo vacío) viaja
    // como transición unlink. Ya NO se llama a recordPublish ni se PATCHea
    // Biblioteca remota directo: el registro de historial lo produce la
    // confirmación causal del manual-platform-link del lado central.
    override fun saveLink(platform: Platform, rawUrl: String) {
        val file = currentFile ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()
            val userKey = tokenStore.currentUser?.id

            if (trimmed.isEmpty()) {
                platformVideoRepository.deleteForFile(file.id, platform)
                fileRepository.removePlatform(file.id, platform)
                if (userKey != null) {
                    platformTransitionRepository.enqueueUnlink(
                        userKey = userKey,
                        fileName = file.fileName,
                        remoteLibraryVideoId = file.remoteLibraryVideoId,
                        platform = platform,
                    )
                }
            } else {
                val platformId = PlatformLinkResolver.resolvedPlatformId(platform, trimmed, platformOkHttpClient)
                val previous = platformVideoRepository.findByLinkedFileAndPlatform(file.id, platform)
                val publishedAt = previous?.publishedAt ?: Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val publishedAtIso = publishedAt.truncatedTo(ChronoUnit.MILLIS).toString()
                platformVideoRepository.upsertPublished(
                    platform = platform,
                    platformId = platformId,
                    platformUrl = trimmed,
                    linkedFileId = file.id,
                    publishedAt = publishedAt,
                )
                fileRepository.addPlatform(file.id, platform)
                if (userKey != null) {
                    platformTransitionRepository.enqueueManualLink(
                        userKey = userKey,
                        fileName = file.fileName,
                        remoteLibraryVideoId = file.remoteLibraryVideoId,
                        platform = platform,
                        platformId = platformId,
                        platformUrl = trimmed,
                        title = null,
                        publishedAt = publishedAtIso,
                    )
                }
            }

            fileRepository.findById(file.id)?.let {
                currentFile = it
                _file.value = it
            }
            _isSaving.value = false
            triggerFlush()
        }
    }

    // Drena el outbox causal en background -- best-effort, no bloquea la UI ni
    // propaga errores (la fila queda encolada y se reintenta en el próximo
    // flush: acá o en DashboardViewModel).
    private fun triggerFlush() {
        viewModelScope.launch { runCatching { causalPlatformOutbox.flush() } }
    }
}
