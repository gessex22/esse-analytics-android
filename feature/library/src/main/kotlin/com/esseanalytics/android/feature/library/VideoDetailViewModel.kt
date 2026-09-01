package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PlatformVideoRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import com.esseanalytics.android.core.network.dto.RemoteLibraryPlatformLinkDto
import com.esseanalytics.android.core.network.dto.UpdateFilePlatformsRequest
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
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
    private val remoteLibraryApi: RemoteLibraryApi,
    private val syncApi: SyncApi,
    private val settingsStore: SettingsStore,
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

    override fun togglePlatform(platform: Platform) {
        val file = currentFile ?: return
        viewModelScope.launch {
            val current = fileRepository.findById(file.id) ?: return@launch
            val (platforms, discarded) = PlatformLinkResolver.nextCycle(
                platform,
                current.platforms.map { it.apiValue },
                current.platformsDiscarded.map { it.apiValue },
            )
            val updated = current.copy(
                platforms = platforms.mapNotNull(Platform::fromApiValue),
                platformsDiscarded = discarded.mapNotNull(Platform::fromApiValue),
            )
            fileRepository.update(updated)
            currentFile = updated
            _file.value = updated
            syncPlatformsToCentralIfNeeded(file.id, file.remoteLibraryVideoId)
            syncToRemoteIfNeeded(file.id, file.remoteLibraryVideoId)
        }
    }

    override fun saveLink(platform: Platform, rawUrl: String) {
        val file = currentFile ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()
            val nowIso = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
            val remoteLink: RemoteLibraryPlatformLinkDto

            if (trimmed.isEmpty()) {
                val previousId = platformVideoRepository
                    .findByLinkedFileAndPlatform(file.id, platform)?.platformId
                platformVideoRepository.deleteForFile(file.id, platform)
                fileRepository.removePlatform(file.id, platform)
                remoteLink = RemoteLibraryPlatformLinkDto(
                    platform = platform.apiValue,
                    platformId = previousId ?: "",
                    platformUrl = null,
                    publishedAt = nowIso,
                )
            } else {
                val platformId = PlatformLinkResolver.resolvedPlatformId(
                    platform,
                    trimmed,
                    platformOkHttpClient,
                )
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
                remoteLink = RemoteLibraryPlatformLinkDto(
                    platform = platform.apiValue,
                    platformId = platformId,
                    platformUrl = trimmed,
                    publishedAt = publishedAtIso,
                )

                runCatching {
                    syncApi.recordPublish(
                        RecordPublishRequest(
                            platform = platform.apiValue,
                            platformId = platformId,
                            platformUrl = trimmed,
                            fileName = file.fileName,
                            remoteLibraryVideoId = file.remoteLibraryVideoId,
                            publishedAt = publishedAtIso,
                            deviceId = settingsStore.getOrCreateInstallId(),
                            deviceName = settingsStore.getOrCreateDeviceName(),
                        ),
                    )
                }.onFailure {
                    _errorMessage.value = "El link se guardó en el celular pero no se pudo sincronizar con Estadísticas (${it.message}). Volvé a guardarlo para reintentar."
                }
            }

            syncToRemoteIfNeeded(file.id, file.remoteLibraryVideoId, listOf(remoteLink))
            fileRepository.findById(file.id)?.let {
                currentFile = it
                _file.value = it
            }
            _isSaving.value = false
        }
    }

    private suspend fun syncPlatformsToCentralIfNeeded(fileId: Long, remoteId: String?) {
        val current = fileRepository.findById(fileId) ?: return
        runCatching {
            syncApi.updateFilePlatforms(
                UpdateFilePlatformsRequest(
                    fileName = current.fileName,
                    remoteLibraryVideoId = remoteId,
                    platforms = current.platforms.map { it.apiValue },
                    platformsDiscarded = current.platformsDiscarded.map { it.apiValue },
                ),
            )
        }
    }

    private suspend fun syncToRemoteIfNeeded(
        fileId: Long,
        remoteId: String?,
        links: List<RemoteLibraryPlatformLinkDto> = emptyList(),
    ) {
        if (remoteId == null) return
        val current = fileRepository.findById(fileId) ?: return
        runCatching {
            remoteLibraryApi.updatePlatforms(
                id = remoteId,
                body = UpdateRemoteLibraryPlatformsRequest(
                    platforms = current.platforms.map { it.apiValue },
                    platformsDiscarded = current.platformsDiscarded.map { it.apiValue },
                    platformLinks = links,
                ),
            )
        }.onFailure { _errorMessage.value = it.message ?: "No se pudo sincronizar con la nube." }
    }
}
