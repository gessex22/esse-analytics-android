package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.AuthState
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.LanLibraryRepository
import com.esseanalytics.android.core.network.CatalogRegistrationOutbox
import com.esseanalytics.android.core.network.LocalBackendApiFactory
import com.esseanalytics.android.core.network.upload.UploadConcurrencyGate
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.api.BackupApi
import com.esseanalytics.android.core.network.util.CanonicalPlatformState
import com.esseanalytics.android.core.network.util.matchCanonicalPlatformState
import com.esseanalytics.android.core.network.util.withCanonicalPlatformState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface LanPublishResult {
    data object Pending : LanPublishResult
    data object InProgress : LanPublishResult
    data object Success : LanPublishResult
    data class Failure(val message: String) : LanPublishResult
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val backupApi: BackupApi,
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val localBackendApiFactory: LocalBackendApiFactory,
    private val uploadConcurrencyGate: UploadConcurrencyGate,
    // FIX 2026-08-18: reemplaza al acceso directo a LanPcDiscoveryStore (ver
    // UIEssePanel/PLAN_LAN_PICKER_Y_REPRODUCTOR-2026-08-18.md) -- el catálogo
    // LAN (discovery + fetch paginado) se comparte con UploadViewModel a
    // través de este repositorio, en vez de que cada ViewModel reimplemente
    // su propio refreshLan()/authorizedLanBaseUrl.
    private val lanLibraryRepository: LanLibraryRepository,
    private val catalogRegistrationOutbox: CatalogRegistrationOutbox,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {

    private val _displayNameScope = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(tokenStore.authState, settingsStore.serverUrl) { auth, server ->
                val userId = (auth as? AuthState.LoggedIn)?.user?.id ?: return@combine null
                "${CatalogRegistrationOutbox.canonicalServer(server)}|$userId"
            }.collect { _displayNameScope.value = it }
        }
    }

    suspend fun renameInSharedLibrary(file: com.esseanalytics.android.core.model.VideoFile, rawName: String): String? {
        val name = rawName.trim()
        require(name.isNotEmpty() && name.length <= 120 && name.none { it.isISOControl() })
        val session = tokenStore.currentSession() ?: error("Inicia sesión para cambiar el nombre de biblioteca.")
        val server = CatalogRegistrationOutbox.canonicalServer(settingsStore.serverUrl.first())
        val scopedFile = file.copy(displayName = name, displayNameState = "pending", displayNameOwnerId = session.second, displayNameServerUrl = server)
        catalogRegistrationOutbox.enqueueDisplayName(scopedFile, name)
        if (tokenStore.currentSession() != session || CatalogRegistrationOutbox.canonicalServer(settingsStore.serverUrl.first()) != server) error("La sesión cambió mientras se guardaba el nombre. Vuelve a abrir la biblioteca.")
        return catalogRegistrationOutbox.displayNameConflict(scopedFile)?.currentName
    }

    suspend fun keepMySharedName(file: com.esseanalytics.android.core.model.VideoFile) {
        catalogRegistrationOutbox.keepDisplayNameProposal(file)
    }

    suspend fun useCurrentSharedName(file: com.esseanalytics.android.core.model.VideoFile): String? {
        return catalogRegistrationOutbox.acceptServerDisplayName(file)
    }

    suspend fun pendingSharedNameConflict(file: com.esseanalytics.android.core.model.VideoFile): String? =
        catalogRegistrationOutbox.displayNameConflict(file)?.currentName

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
    // existe, no degrada a un mirror. FIX 2026-08-18: delegado a
    // LanLibraryRepository (compartido con Subir, ver comentario en el
    // constructor).
    val canSeeLanLibrary: StateFlow<Boolean> = lanLibraryRepository.canSeeLanLibrary
    val lanVideos: StateFlow<List<LocalPcVideoDto>> = lanLibraryRepository.videos
    val lanLoadError: StateFlow<String?> = lanLibraryRepository.loadError

    private val _remoteVideos = MutableStateFlow<List<RemoteLibraryVideoDto>>(emptyList())
    private val _canonicalPlatformStates = MutableStateFlow<Map<Long, CanonicalPlatformState>>(emptyMap())
    private var canonicalStatusOwner: String? = null

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

    // FIX 2026-08-18: la carga/reconciliación en vivo del catálogo LAN (PC
    // que aparece/se pierde/pasa a "autorizada") ya la maneja
    // LanLibraryRepository internamente (su propio watcher arranca con
    // start(), ver startLanDiscovery() más abajo) -- acá solo queda la parte
    // que es específica de ESTA pantalla: si el chip "Biblioteca LAN" estaba
    // seleccionado y la PC se pierde, canSeeLanLibrary pasa a false y el
    // chip desaparece de visibleFilters, pero _filter podía seguir apuntando
    // a LAN sin ningún chip para volver a ALL a mano (FIX 2026-08-17
    // original, preservado acá).
    init {
        viewModelScope.launch {
            lanLibraryRepository.canSeeLanLibrary.collect { canSee ->
                if (_filter.value == LibraryFilter.LAN && !canSee) {
                    _filter.value = LibraryFilter.ALL
                }
            }
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
        lanLibraryRepository.videos,
        _filter,
        _canonicalPlatformStates,
    ) { local, remote, lan, filter, canonical ->
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
        val currentScope = _displayNameScope.value
        val resolvedLocal = local.map { file ->
            val isOwnName = file.displayNameOwnerId != null
                && file.displayNameOwnerId == currentScope?.substringAfterLast('|')
                && file.displayNameServerUrl == currentScope?.substringBeforeLast('|')
            val scoped = if (isOwnName) file else file.copy(displayName = null, displayNameRevision = 0, displayNameState = null)
            scoped.withCanonicalPlatformState(canonical[file.id])
        }
        val localRemoteIds = resolvedLocal.mapNotNull { it.remoteLibraryVideoId }.toSet()
        val localFileNames = resolvedLocal.map { it.fileName }.toSet()
        val remoteFileNames = remote.map { it.fileName }.toSet()

        fun isAlreadyLocal(video: RemoteLibraryVideoDto) =
            video._id in localRemoteIds || video.fileName in localFileNames

        val lanBaseUrl = lanLibraryRepository.authorizedBaseUrl()

        val merged = buildList {
            if (filter == LibraryFilter.ALL || filter == LibraryFilter.LOCAL) {
                addAll(resolvedLocal.map { LibraryListItem.Local(it) })
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

    /** Actualiza los badges locales con el catálogo de publicación compartido desde PC/UIEsse. */
    fun refreshPlatformStates() {
        val user = tokenStore.currentUser
        if (user == null) {
            canonicalStatusOwner = null
            _canonicalPlatformStates.value = emptyMap()
            return
        }
        if (canonicalStatusOwner != user.id) {
            canonicalStatusOwner = user.id
            _canonicalPlatformStates.value = emptyMap()
        }
        viewModelScope.launch { catalogRegistrationOutbox.refreshCatalogDisplayNames() }
        if (!user.isPremium) return
        viewModelScope.launch {
            runCatching {
                val catalog = withContext(Dispatchers.IO) { backupApi.listFiles(includeResolved = true).files }
                val localFiles = fileRepository.observeAll().first()
                localFiles.mapNotNull { file ->
                    matchCanonicalPlatformState(file, catalog, localFiles)?.let { file.id to it }
                }.toMap()
            }.onSuccess { states ->
                if (tokenStore.currentUser?.id == user.id) _canonicalPlatformStates.value = states
            }
        }
    }

    // FIX 2026-08-18: delegado a LanLibraryRepository, ref-counted ahí (dos
    // consumidores activos a la vez -- Biblioteca y el picker de Subir -- no
    // se pisan entre sí, ver ese archivo).
    fun startLanDiscovery() = lanLibraryRepository.start()

    fun stopLanDiscovery() = lanLibraryRepository.stop()

    // La PC lee y sube su propio archivo; Android envía un comando por red.
    // El mismo cupo que las subidas del teléfono permite hasta tres comandos
    // simultáneos por Wi-Fi y uno fuera de Wi-Fi, con resultados independientes.
    // Una nueva sesión cancela la espera anterior para no escribir resultados
    // sobre la hoja de otro video; no se reenvía automáticamente ningún comando.
    private var publishLanJob: Job? = null

    fun publishLan(
        item: LibraryListItem.LanVideo,
        platforms: Set<Platform>,
        title: String,
        description: String,
        tiktokPublic: Boolean,
        crossPostFacebook: Boolean = false,
    ) {
        if (platforms.isEmpty()) return
        publishLanJob?.cancel()
        _lanPublishState.update { previous -> previous + platforms.associateWith { LanPublishResult.Pending } }
        publishLanJob = viewModelScope.launch {
            val api = localBackendApiFactory.create(item.baseUrl)
            supervisorScope {
                platforms.forEach { platform ->
                    launch {
                        uploadConcurrencyGate.withPermit {
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
                                            LocalPcInstagramUploadRequest(
                                                fileId = item.video._id,
                                                caption = description,
                                                crossPostFacebook = crossPostFacebook,
                                            ),
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
                            }.onFailure { error ->
                                if (error is CancellationException) throw error
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
                    }
                }
            }
            lanLibraryRepository.refresh()
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
