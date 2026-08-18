package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Extraído de LibraryViewModel (2026-08-18, ver
// UIEssePanel/PLAN_LAN_PICKER_Y_REPRODUCTOR-2026-08-18.md): acceso compartido
// al catálogo de Biblioteca LAN (PC autorizada por Bonjour + pendientes
// paginados), usado tanto por Biblioteca (feature:library) como por el
// selector de video de Subir (feature:upload) -- sin esto, cada feature
// tendría su propia copia de refreshLan()/authorizedLanBaseUrl con el riesgo
// real de que diverjan. @Singleton, mismo scope que LanPcDiscoveryStore (ya
// singleton): los dos consumidores ven el mismo estado, sin re-fetch
// duplicado si Biblioteca y Subir están vivas a la vez.
//
// Mirror de LANLibraryAccess.swift (iOS, mismo plan) -- misma política, sin
// el concepto de URL manual/preset verificada porque Android hoy solo tiene
// descubrimiento Bonjour (ver SettingsScreen.kt): si Android suma ese modo
// más adelante, este es el lugar único donde agregarlo.
@Singleton
class LanLibraryRepository @Inject constructor(
    private val lanDiscoveryStore: LanPcDiscoveryStore,
    private val localBackendApiFactory: LocalBackendApiFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val canSeeLanLibrary: StateFlow<Boolean> = lanDiscoveryStore.discovered
        .map { list -> list.any { it.authState == LanPcAuthState.AUTHORIZED } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun authorizedBaseUrl(): String? =
        lanDiscoveryStore.discovered.value.firstOrNull { it.authState == LanPcAuthState.AUTHORIZED }?.url

    private val _videos = MutableStateFlow<List<LocalPcVideoDto>>(emptyList())
    val videos: StateFlow<List<LocalPcVideoDto>> = _videos.asStateFlow()

    // Reactivo, en paralelo al getter síncrono authorizedBaseUrl() de abajo
    // (ese sigue existiendo para el combine() de LibraryViewModel, que ya
    // lee todo dentro de un mismo tick) -- consumidores que arman su propia
    // lista de tarjetas (ej. UploadScreen) necesitan el baseUrl que
    // corresponde EXACTAMENTE a `videos`, actualizado en el mismo momento
    // (ver refresh() más abajo), no un valor leído en un tick distinto.
    private val _activeBaseUrl = MutableStateFlow<String?>(null)
    val activeBaseUrl: StateFlow<String?> = _activeBaseUrl.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    // Ref-counted (mismo criterio que LanPcDiscoveryStore.start()/stop(), ya
    // ref-counted ahí adentro, y que LANLibraryAccess en iOS): dos
    // consumidores activos a la vez (Biblioteca + picker de Subir) no deben
    // pisarse el descubrimiento ni duplicar el watcher de abajo.
    private var activeObservers = 0
    private var watchJob: Job? = null

    fun start() {
        activeObservers += 1
        lanDiscoveryStore.start()
        if (activeObservers == 1) {
            watchJob = scope.launch {
                lanDiscoveryStore.discovered.collect { refresh() }
            }
        }
    }

    fun stop() {
        activeObservers = (activeObservers - 1).coerceAtLeast(0)
        lanDiscoveryStore.stop()
        if (activeObservers == 0) {
            watchJob?.cancel()
            watchJob = null
        }
    }

    // FIX 2026-08-17 (review externo, hallazgo real P1 en LibraryViewModel,
    // preservado acá): cancela cualquier carga anterior antes de arrancar una
    // nueva, y re-chequea que la PC siga siendo la vigente antes de aplicar
    // el resultado -- evita que la respuesta tardía de una PC vieja pise el
    // resultado de la PC nueva cuando cambia la autorizada a mitad de un fetch.
    private var refreshJob: Job? = null

    fun refresh() {
        val baseUrl = authorizedBaseUrl()
        refreshJob?.cancel()
        if (baseUrl == null) {
            _videos.value = emptyList()
            _activeBaseUrl.value = null
            _loadError.value = null
            return
        }
        refreshJob = scope.launch {
            _loadError.value = null
            runCatching { withContext(Dispatchers.IO) { fetchAll(baseUrl) } }
                .onSuccess { results ->
                    if (authorizedBaseUrl() == baseUrl) {
                        _videos.value = results.filter(::isPending)
                        _activeBaseUrl.value = baseUrl
                    }
                }
                .onFailure { error ->
                    if (authorizedBaseUrl() == baseUrl) _loadError.value = error.message ?: "No se pudo leer la biblioteca LAN."
                }
        }
    }

    // FIX 2026-08-17 (preservado de LibraryViewModel): local-backend pagina
    // GET /api/videos -- se sigue nextPage hasta agotarlo, tope de 50 páginas
    // (5000 videos) puramente defensivo.
    private suspend fun fetchAll(baseUrl: String): List<LocalPcVideoDto> {
        val api = localBackendApiFactory.create(baseUrl)
        val all = mutableListOf<LocalPcVideoDto>()
        var page = 1
        while (page <= 50) {
            val response = api.listVideos(limit = 100, page = page)
            all += response.results
            val nextPage = response.info?.nextPage ?: break
            if (nextPage <= page) break
            page = nextPage
        }
        return all
    }
}

private fun isPending(video: LocalPcVideoDto): Boolean =
    Platform.publishable.any { it.apiValue !in video.platforms && it.apiValue !in video.platforms_discarded }
