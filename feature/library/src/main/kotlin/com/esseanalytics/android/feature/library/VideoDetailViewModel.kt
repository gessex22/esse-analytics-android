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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val settingsStore: SettingsStore,
    @PlatformOkHttp private val platformOkHttpClient: OkHttpClient,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun existingLink(fileId: Long, platform: Platform): String? =
        platformVideoRepository.findByLinkedFileAndPlatform(fileId, platform)?.platformUrl

    // Ciclo pendiente -> publicado -> descartado -> pendiente. Room primero
    // (la UI nunca espera la red) -- en paralelo se sincroniza a la central
    // (best-effort en los dos casos, ver los dos syncXIfNeeded de abajo).
    fun togglePlatform(file: VideoFile, platform: Platform) {
        viewModelScope.launch {
            fileRepository.cyclePlatformStatus(file.id, platform)
            syncPlatformsToCentralIfNeeded(file.id, file.remoteLibraryVideoId)
            syncToRemoteIfNeeded(file.id, file.remoteLibraryVideoId)
        }
    }

    // Antes SOLO "publicar" llegaba a la central (syncApi.recordPublish) --
    // "descartar" era 100% local, sin ningún rastro fuera del teléfono, así
    // que desktop nunca se enteraba y había que repetirlo a mano ahí (bug
    // real reportado 2026-08-06). GET /api/backup/files en la central ya
    // mergea este mismo dato sobre lo que sube el push de desktop (ver
    // updateFilePlatforms en backup.controller.ts del backend), así que
    // alcanza con mandarlo -- el próximo pull() de desktop ya sabe traerlo
    // de vuelta sin ningún cambio ahí. Relee de Room en vez de confiar en el
    // `file` del caller -- mismo motivo que syncToRemoteIfNeeded de abajo,
    // el toggle ya escribió el estado nuevo antes de llegar acá.
    private suspend fun syncPlatformsToCentralIfNeeded(fileId: Long, remoteLibraryVideoId: String?) {
        val current = fileRepository.findById(fileId) ?: return
        runCatching {
            syncApi.updateFilePlatforms(
                UpdateFilePlatformsRequest(
                    fileName = current.fileName,
                    remoteLibraryVideoId = remoteLibraryVideoId,
                    platforms = current.platforms.map { it.apiValue },
                    platformsDiscarded = current.platformsDiscarded.map { it.apiValue },
                ),
            )
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
                val resolvedUrl = resolveShortLinkIfNeeded(platform, trimmed) ?: trimmed
                val platformId = extractPlatformId(platform, resolvedUrl)
                val previousPublication = platformVideoRepository.findByLinkedFileAndPlatform(file.id, platform)
                // Editar un link ya asociado no es una nueva publicación: usa
                // la fecha original tanto localmente como al reportar a la
                // central, para no alterar Historial ni el calendario.
                val publishedAt = previousPublication?.publishedAt ?: Instant.now().truncatedTo(ChronoUnit.MILLIS)
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

                // Sin .onFailure acá, un fallo (red, 401, etc.) quedaba mudo:
                // el archivo local mostraba "Publicado" pero la central nunca
                // se enteraba del link, y Estadísticas/Dashboard (que leen
                // PlatformVideoModel, no Room) quedaban en cero para siempre
                // sin ninguna señal de qué pasó -- mismo bug que ya se
                // corrigió en la versión de iOS.
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
                    _errorMessage.value = "El link se guardó en el celular pero no se pudo sincronizar con Estadísticas (${it.message}). Volvé a guardar el link para reintentar."
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

    // TikTok reparte por default un link CORTO al tocar "Compartir" -> "Copiar
    // enlace" (vm.tiktok.com/XXXX, vt.tiktok.com/XXXX, o tiktok.com/t/XXXX/)
    // que no trae el id numérico en la URL -- solo aparece tras seguir la
    // redirección HTTP hacia el link canónico (.../@usuario/video/123...). Sin
    // esto, extractPlatformId no matcheaba nada y guardaba la URL corta entera
    // como "id" -- inválido para la API de TikTok, así que las stats quedaban
    // en 0 para siempre sin ningún error visible (bug real detectado en la
    // cuenta de producción, mismo fix que en VideoDetailView.swift de iOS).
    private suspend fun resolveShortLinkIfNeeded(platform: Platform, url: String): String? {
        if (platform != Platform.TIKTOK) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        val isShortLink = parsed.host.contains("vm.tiktok.com") ||
            parsed.host.contains("vt.tiktok.com") ||
            parsed.encodedPath.startsWith("/t/")
        if (!isShortLink) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                platformOkHttpClient.newCall(Request.Builder().url(parsed).get().build()).execute().use { response ->
                    response.request.url.toString()
                }
            }.getOrNull()
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
