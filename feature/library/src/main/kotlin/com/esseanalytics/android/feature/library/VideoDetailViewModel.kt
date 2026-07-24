package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PlatformVideoRepository
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import com.esseanalytics.android.core.network.dto.RemoteLibraryPlatformLinkDto
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// Editor manual de "publicado + link real" para un archivo local (VideoDetailSheet) --
// mirror de VideoDetailView.swift en iOS, MISMA corrección que ahí: togglear el
// badge o guardar un link no puede quedar solo en Room. Si el archivo viene de
// Nube (remoteLibraryVideoId != null) se propaga también a
// RemoteLibraryVideoModel vía RemoteLibraryApi.updatePlatforms -- ANTES esa
// función no existía del lado de Android, así que esto no era "un bug", era
// una función entera sin construir.
@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val platformVideoRepository: PlatformVideoRepository,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val syncApi: SyncApi,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun existingLink(fileId: Long, platform: Platform): String? =
        platformVideoRepository.findByLinkedFileAndPlatform(fileId, platform)?.platformUrl

    // Ciclo pendiente -> publicado -> descartado -> pendiente. 100% local
    // (Room) -- si el archivo está vinculado a Nube, se sincroniza best-effort
    // de vuelta, sin bloquear el toggle si la red falla.
    fun togglePlatform(file: VideoFile, platform: Platform) {
        viewModelScope.launch {
            fileRepository.cyclePlatformStatus(file.id, platform)
            syncToRemoteIfNeeded(file.id, file.remoteLibraryVideoId)
        }
    }

    // Pegar un link acá marca la plataforma como publicada (igual que
    // setPlatformLink en el desktop) y manda todo lo necesario para que
    // Estadísticas lo vea (recordPublish) sin que nadie tenga que ir a
    // completarlo en otro lado -- mismo comentario que la versión de iOS.
    fun saveLink(file: VideoFile, platform: Platform, rawUrl: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()
            // truncatedTo(MILLIS) -- Instant.toString() sin truncar puede
            // llevar precisión de nanosegundos, y el parseo de Date en Node
            // (new Date(str)) no es confiable con más de 3 dígitos decimales.
            val nowIso = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
            val remoteLink: RemoteLibraryPlatformLinkDto

            if (trimmed.isEmpty()) {
                // Mismo criterio que la versión de iOS para el caso vacío:
                // conserva el platformId que ya hubiera (si lo hay) y manda
                // platformUrl null, en vez de omitir el link por completo --
                // así la central también se entera de que se borró.
                val previousPlatformId = platformVideoRepository.findByLinkedFileAndPlatform(file.id, platform)?.platformId
                platformVideoRepository.deleteForFile(file.id, platform)
                fileRepository.removePlatform(file.id, platform)
                remoteLink = RemoteLibraryPlatformLinkDto(
                    platform = platform.apiValue,
                    platformId = previousPlatformId ?: "",
                    platformUrl = null,
                    publishedAt = nowIso,
                )
            } else {
                val platformId = extractPlatformId(platform, trimmed)
                platformVideoRepository.upsertPublished(
                    platform = platform,
                    platformId = platformId,
                    platformUrl = trimmed,
                    linkedFileId = file.id,
                )
                fileRepository.addPlatform(file.id, platform)
                remoteLink = RemoteLibraryPlatformLinkDto(
                    platform = platform.apiValue,
                    platformId = platformId,
                    platformUrl = trimmed,
                    publishedAt = nowIso,
                )

                runCatching {
                    syncApi.recordPublish(
                        RecordPublishRequest(
                            platform = platform.apiValue,
                            platformId = platformId,
                            platformUrl = trimmed,
                            fileName = file.fileName,
                            publishedAt = nowIso,
                        ),
                    )
                }
            }

            syncToRemoteIfNeeded(file.id, file.remoteLibraryVideoId, listOf(remoteLink))
            _isSaving.value = false
        }
    }

    // platformLinks vacío por default -- togglePlatform (ciclo de estado sin
    // tocar el link) no tiene un link nuevo que mandar; saveLink sí lo arma y
    // lo pasa acá. Relee el archivo de Room en vez de confiar en el parámetro
    // `file` de arriba -- el toggle/save ya escribió el estado nuevo antes de
    // llegar acá, y este es justamente el que hay que propagar.
    private suspend fun syncToRemoteIfNeeded(
        fileId: Long,
        remoteLibraryVideoId: String?,
        platformLinks: List<RemoteLibraryPlatformLinkDto> = emptyList(),
    ) {
        if (remoteLibraryVideoId == null) return
        val current = fileRepository.findById(fileId) ?: return
        runCatching {
            remoteLibraryApi.updatePlatforms(
                id = remoteLibraryVideoId,
                body = UpdateRemoteLibraryPlatformsRequest(
                    platforms = current.platforms.map { it.apiValue },
                    platformsDiscarded = current.platformsDiscarded.map { it.apiValue },
                    platformLinks = platformLinks,
                ),
            )
        }.onFailure {
            _errorMessage.value = it.message ?: "No se pudo sincronizar con la nube."
        }
    }

    // Best-effort, no perfecto -- mismo espíritu que extractPlatformId del
    // desktop (video.controller.ts) y de iOS: si no matchea ningún patrón
    // conocido, usa el link completo como id (sigue siendo único, solo que
    // Estadísticas no podrá pedirle stats a la API real con eso).
    private fun extractPlatformId(platform: Platform, url: String): String {
        val pattern = when (platform) {
            Platform.YOUTUBE -> Regex("""(?:v=|youtu\.be/|shorts/)([\w-]{6,})""")
            Platform.INSTAGRAM -> Regex("""(?:reel|p)/([\w-]+)""")
            Platform.TIKTOK -> Regex("""video/(\d+)""")
            Platform.FACEBOOK -> return url
        }
        return pattern.find(url)?.groupValues?.getOrNull(1) ?: url
    }
}
