package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.AuthState
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.LanPcAuthState
import com.esseanalytics.android.core.network.LanPcDiscoveryStore
import com.esseanalytics.android.core.network.LocalBackendApiFactory
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.LocalPcInstagramUploadRequest
import com.esseanalytics.android.core.network.dto.LocalPcTiktokUploadRequest
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import com.esseanalytics.android.core.network.dto.LocalPcYoutubeUploadRequest
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.util.localPcStreamUrl
import com.esseanalytics.android.core.network.util.localPcThumbnailUrl
import com.esseanalytics.android.core.network.util.remoteLibraryStreamUrl
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import retrofit2.Retrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface LanPublishResult {
    data object InProgress : LanPublishResult
    data object Success : LanPublishResult
    data class Failure(val message: String) : LanPublishResult
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    fileRepository: FileRepository,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore,
    private val localBackendApiFactory: LocalBackendApiFactory,
    private val lanDiscoveryStore: LanPcDiscoveryStore,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {

    // Mirror de RemoteLibraryAPI.thumbnailURL en iOS -- ver
    // remoteLibraryThumbnailUrl. Null si el video no tiene miniatura.
    fun thumbnailUrl(video: RemoteLibraryVideoDto): String? =
        remoteLibraryThumbnailUrl(retrofit.baseUrl(), video._id, video.thumbnailStoredFileName, tokenStore.token)

    // Mirror de RemoteLibraryAPI.streamURL en iOS -- usada por el reproductor
    // (ver VideoPlayerDialog) para videos de la cola remota mostrados acá en
    // "Todos"/"Cola remota".
    fun streamUrl(video: RemoteLibraryVideoDto): String? =
        remoteLibraryStreamUrl(retrofit.baseUrl(), video._id, tokenStore.token)

    fun lanThumbnailUrl(item: LibraryListItem.LanVideo): String? =
        localPcThumbnailUrl(item.baseUrl, item.video._id, tokenStore.token)

    fun lanStreamUrl(item: LibraryListItem.LanVideo): String? =
        localPcStreamUrl(item.baseUrl, item.video._id, tokenStore.token)

    // Premium + entitlement de storage aparte (ver requireCloudStorage en la
    // central) -- reactivo a tokenStore.authState para que un refreshUser()
    // que cambie el entitlement se refleje sin tener que reloguear la UI.
    val canUseCloudStorage: StateFlow<Boolean> = tokenStore.authState
        .map { (it as? AuthState.LoggedIn)?.user?.canUseCloudStorage == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // FIX 2026-08-17 (mismo criterio que LibraryView.swift en iOS, mismo
    // día): reemplaza a canSeeBackupCatalog (isPremium, mirror sin bytes).
    // Gratis -- sin isPremium, mismo criterio que ya usa "PC local" en
    // Ajustes: los bytes nunca salen de la LAN del usuario. Gateado por
    // reachability REAL (hay al menos una PC autorizada ahora mismo), no por
    // el plan de la cuenta -- sin PC alcanzable, el chip directamente no
    // existe, no degrada a un mirror.
    val canSeeLanLibrary: StateFlow<Boolean> = lanDiscoveryStore.discovered
        .map { list -> list.any { it.authState == LanPcAuthState.AUTHORIZED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun authorizedLanBaseUrl(): String? =
        lanDiscoveryStore.discovered.value.firstOrNull { it.authState == LanPcAuthState.AUTHORIZED }?.url

    private val _remoteVideos = MutableStateFlow<List<RemoteLibraryVideoDto>>(emptyList())
    private val _lanVideos = MutableStateFlow<List<LocalPcVideoDto>>(emptyList())

    // FIX 2026-08-17: antes el fetch del mirror era runCatching puro -- un
    // error real (PC caída a mitad de sesión, JWT rechazado) quedaba
    // indistinguible de "no hay nada pendiente". Se guarda el error de
    // verdad para poder mostrarlo (mismo motivo que documenta
    // LibraryView.swift en iOS sobre el bug real de "facebook" en
    // BackupCatalogAPI).
    private val _lanLoadError = MutableStateFlow<String?>(null)
    val lanLoadError: StateFlow<String?> = _lanLoadError.asStateFlow()

    private val _lanPublishState = MutableStateFlow<Map<Platform, LanPublishResult>>(emptyMap())
    val lanPublishState: StateFlow<Map<Platform, LanPublishResult>> = _lanPublishState.asStateFlow()

    // Título (file_name) del "próximo" video a publicar por plataforma, según
    // la central (GET /api/sync/calendar-config, ya usado por Calendario). Se
    // matchea contra LibraryListItem.displayName -- mismo criterio de nombre
    // que usa el resto de la app cuando no hay un id en común entre el
    // catálogo local (Room, Long) y FileModel de la central (ObjectId).
    private val _nextUploads = MutableStateFlow<Map<Platform, String>>(emptyMap())
    val nextUploads: StateFlow<Map<Platform, String>> = _nextUploads.asStateFlow()

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

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    // Reconciliación en vivo (sección 4.4 del diseño): la PC puede aparecer,
    // perderse, o pasar de "verificando" a "autorizada" mientras el usuario
    // está mirando la lista -- reintenta la carga cada vez que cambia el
    // conjunto de PCs descubiertas, no solo una vez al entrar.
    init {
        viewModelScope.launch {
            lanDiscoveryStore.discovered.collect { refreshLan() }
        }
    }

    // flowOn(Default): con la Biblioteca LAN esto fusiona y ordena hasta
    // ~1000+ ítems -- sin esto corría en el dispatcher del colector
    // (viewModelScope = Main), y CADA emisión de CUALQUIERA de las 4 fuentes
    // (incluido Room re-emitiendo por cambios que no tocan la lista en sí)
    // volvía a ordenar todo en el hilo principal, trabando la UI. La UI sigue
    // viendo el StateFlow normal, solo el cómputo se mueve.
    val items: StateFlow<List<LibraryListItem>> = combine(
        fileRepository.observeAll(),
        _remoteVideos,
        _lanVideos,
        _filter,
    ) { local, remote, lan, filter ->
        // Mismo criterio que LibraryView.swift (iOS): un video real puede
        // existir en más de una de las 3 fuentes (local + cola remota, o cola
        // remota + biblioteca LAN) -- sin este cruce, "Todos" lo mostraba una
        // fila por fuente, como si fueran videos distintos. Match por
        // remoteLibraryVideoId primero (link explícito, ver
        // ImportUseCase.importFromRemoteLibrary) y fileName como respaldo
        // para lo que se importó antes de que existiera ese link. Local
        // siempre gana (tiene bytes reproducibles y es dueño del registro);
        // remoto le gana a LAN (tiene miniatura real cacheada, sin depender
        // de que la PC siga viva en este instante). Cada chip de filtro
        // individual sigue mostrando su fuente completa sin filtrar.
        val localRemoteIds = local.mapNotNull { it.remoteLibraryVideoId }.toSet()
        val localFileNames = local.map { it.fileName }.toSet()
        val remoteFileNames = remote.map { it.fileName }.toSet()

        fun isAlreadyLocal(video: RemoteLibraryVideoDto) =
            video._id in localRemoteIds || video.fileName in localFileNames

        val lanBaseUrl = authorizedLanBaseUrl()

        val merged = buildList {
            if (filter == LibraryFilter.ALL || filter == LibraryFilter.LOCAL) {
                addAll(local.map { LibraryListItem.Local(it) })
            }
            if (filter == LibraryFilter.ALL || filter == LibraryFilter.REMOTE) {
                val videos = if (filter == LibraryFilter.ALL) {
                    remote.filter { !isAlreadyLocal(it) }
                } else {
                    remote
                }
                addAll(videos.map { LibraryListItem.Remote(it) })
            }
            if (lanBaseUrl != null && (filter == LibraryFilter.ALL || filter == LibraryFilter.LAN)) {
                val videos = if (filter == LibraryFilter.ALL) {
                    lan.filter { it.fileName !in localFileNames && it.fileName !in remoteFileNames }
                } else {
                    lan
                }
                addAll(videos.map { LibraryListItem.LanVideo(it, lanBaseUrl) })
            }
        }
        merged.sortedByDescending { it.sortInstant }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    // Se llama desde la pantalla apenas canUseCloudStorage es true (ver
    // LibraryScreen) -- si falla (sin red, entitlement recién revocado) se
    // traga el error y deja la lista remota como estaba, el usuario igual ve
    // sus locales sin interrupción. withContext(IO): la respuesta del
    // catálogo puede traer cientos de objetos -- parsear el JSON explícitamente
    // fuera de Main evita depender de en qué hilo reanuda Retrofit la corrutina.
    fun refreshRemote() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remoteLibraryApi.listVideos().videos } }
                .onSuccess { _remoteVideos.value = it }
        }
    }

    fun startLanDiscovery() = lanDiscoveryStore.start()

    fun stopLanDiscovery() = lanDiscoveryStore.stop()

    // Reemplaza a refreshBackupCatalog(). Sin PC autorizada ahora mismo, no
    // hay nada que traer -- el chip ni aparece (ver canSeeLanLibrary), pero
    // igual se limpia _lanVideos para no dejar filas viejas de una PC que ya
    // se perdió (ver onServiceLost en LanPcDiscoveryStore).
    fun refreshLan() {
        val baseUrl = authorizedLanBaseUrl()
        if (baseUrl == null) {
            _lanVideos.value = emptyList()
            _lanLoadError.value = null
            return
        }
        viewModelScope.launch {
            _lanLoadError.value = null
            runCatching {
                withContext(Dispatchers.IO) { localBackendApiFactory.create(baseUrl).listVideos(limit = 100).results }
            }
                .onSuccess { results -> _lanVideos.value = results.filter(::isLanVideoPending) }
                .onFailure { error -> _lanLoadError.value = error.message ?: "No se pudo leer la biblioteca LAN." }
        }
    }

    // Server-side y sin bytes -- la PC lee su propio archivo del disco y lo
    // sube ella misma (mismo mecanismo que LocalBackendUploadAPI en iOS), el
    // celular solo manda un comando JSON por plataforma. Se publican en
    // secuencia (no en paralelo) para que el estado por plataforma
    // (lanPublishState) sea legible mientras corre, y porque local-backend
    // ya serializa sus propias subidas del lado de la PC.
    fun publishLan(item: LibraryListItem.LanVideo, platforms: Set<Platform>, title: String, description: String, tiktokPublic: Boolean) {
        viewModelScope.launch {
            val api = localBackendApiFactory.create(item.baseUrl)
            for (platform in platforms) {
                _lanPublishState.update { it + (platform to LanPublishResult.InProgress) }
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        when (platform) {
                            Platform.YOUTUBE -> api.uploadYoutube(
                                LocalPcYoutubeUploadRequest(
                                    fileId = item.video._id,
                                    title = title.ifBlank { item.video.fileName },
                                    description = description,
                                ),
                            )
                            Platform.INSTAGRAM -> api.uploadInstagram(
                                LocalPcInstagramUploadRequest(fileId = item.video._id, caption = description),
                            )
                            Platform.TIKTOK -> api.uploadTiktok(
                                LocalPcTiktokUploadRequest(
                                    fileId = item.video._id,
                                    title = title,
                                    privacyLevel = if (tiktokPublic) "PUBLIC_TO_EVERYONE" else "SELF_ONLY",
                                ),
                            )
                            Platform.FACEBOOK -> error("Facebook no es una plataforma publicable directa")
                        }
                    }
                }
                val result = outcome.fold(
                    onSuccess = { response ->
                        if (response.isSuccessful) {
                            LanPublishResult.Success
                        } else {
                            val message = runCatching { response.errorBody()?.string() }.getOrNull()?.take(200)
                            LanPublishResult.Failure(message ?: "Error ${response.code()}")
                        }
                    },
                    onFailure = { error -> LanPublishResult.Failure(error.message ?: "Error de red") },
                )
                _lanPublishState.update { it + (platform to result) }
            }
            refreshLan()
        }
    }

    fun resetLanPublishState() {
        _lanPublishState.value = emptyMap()
    }

    fun delete(item: LibraryListItem) {
        viewModelScope.launch {
            when (item) {
                is LibraryListItem.Local -> deleteVideoUseCase.delete(item.file)
                is LibraryListItem.Remote -> {
                    runCatching { remoteLibraryApi.deleteVideo(item.video._id) }
                    refreshRemote()
                }
                // Sin acción de borrado -- el archivo vive en la PC, no hay
                // endpoint para borrarlo desde acá.
                is LibraryListItem.LanVideo -> Unit
            }
        }
    }
}

private fun isLanVideoPending(video: LocalPcVideoDto): Boolean =
    Platform.publishable.any { it.apiValue !in video.platforms && it.apiValue !in video.platforms_discarded }
