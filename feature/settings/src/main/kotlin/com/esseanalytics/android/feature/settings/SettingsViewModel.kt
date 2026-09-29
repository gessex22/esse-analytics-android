package com.esseanalytics.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.WorkflowMode
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.AuthEvent
import com.esseanalytics.android.core.network.AuthEventBus
import com.esseanalytics.android.core.network.LabModeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Junta acá los settings que ya existían sueltos (workflowMode y
// wifiOnlyUploads venían de Fase 0/1 sin pantalla propia) más el nuevo
// selector de tema — todo lee/escribe directo a SettingsStore. Logout usa
// TokenStore directo (no AuthRepository de feature:auth) para no crear una
// dependencia feature-a-feature -- ningún otro módulo de la app lo hace,
// y TokenStore.clear() es exactamente lo mismo que hace AuthRepository.logout().
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val platformAuthApi: PlatformAuthApi,
    private val localPcDiscovery: LocalPcDiscovery,
    private val authEventBus: AuthEventBus,
    private val labModeStatus: LabModeStatus,
    private val serverHealthChecker: ServerHealthChecker,
) : ViewModel() {

    // Ver Core/Network/LabModeStatus.swift (iOS) -- mismo criterio, chequeo en
    // vivo de GET /api/health, nunca heurística de URL. Se refresca al abrir
    // la pantalla y después de cada Guardar/Restablecer (ver SettingsScreen.kt).
    private val _isLabMode = MutableStateFlow(false)
    val isLabMode: StateFlow<Boolean> = _isLabMode

    fun refreshLabMode() {
        viewModelScope.launch { _isLabMode.value = labModeStatus.isActive() }
    }

    val colorTheme: StateFlow<String> = settingsStore.colorTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "rojo")

    val workflowMode: StateFlow<WorkflowMode> = settingsStore.workflowMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkflowMode.SIMPLE)

    val wifiOnlyUploads: StateFlow<Boolean> = settingsStore.wifiOnlyUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val serverUrl: StateFlow<String> = settingsStore.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _connections = kotlinx.coroutines.flow.MutableStateFlow<Map<Platform, Boolean>>(emptyMap())
    val connections: StateFlow<Map<Platform, Boolean>> = _connections
    private val _discoveredPc = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
    val discoveredPc: StateFlow<Pair<String, String>?> = _discoveredPc

    init {
        viewModelScope.launch {
            authEventBus.events.collect { event ->
                if (event is AuthEvent.PlatformSessionExpired) {
                    val platform = Platform.fromApiValue(event.platform) ?: return@collect
                    _connections.value = _connections.value + (platform to false)
                }
            }
        }
    }

    fun setColorTheme(value: String) {
        viewModelScope.launch { settingsStore.setColorTheme(value) }
    }

    fun setWorkflowMode(mode: WorkflowMode) {
        viewModelScope.launch { settingsStore.setWorkflowMode(mode) }
    }

    fun setWifiOnlyUploads(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWifiOnlyUploads(enabled) }
    }

    // Estado del flujo Guardar servidor (ver ServerSettingsContent): validación
    // → "Probando…" → éxito con environment del health / error. Si la prueba
    // falla, el valor guardado queda intacto.
    private val _serverSaveState = MutableStateFlow<ServerSaveState>(ServerSaveState.Idle)
    val serverSaveState: StateFlow<ServerSaveState> = _serverSaveState.asStateFlow()

    fun resetServerSaveState() {
        _serverSaveState.value = ServerSaveState.Idle
    }

    // Antes de persistir valida el candidato (vacío = central) y lo prueba en
    // vivo con GET {candidato}/api/health (timeout 5s) vía ServerHealthChecker
    // -- NUNCA HealthApi/Retrofit, cuyo singleton sigue pegado a la baseUrl con
    // la que arrancó el proceso (ver TODO de NetworkModule). Sólo ante HTTP 2xx
    // persiste con SettingsStore.setServerUrl. OJO: aunque se guarde, el
    // Retrofit singleton conserva la URL vieja hasta reiniciar la app (igual
    // que el resto de la red) -- el banner/badge de laboratorio puede quedar
    // desactualizado hasta el restart; comportamiento conocido, no nuevo.
    fun saveServerUrl(draft: String) {
        when (val validation = ServerUrlRules.validate(draft)) {
            is ServerUrlValidation.Invalid ->
                _serverSaveState.value = ServerSaveState.Error(validation.reason)

            is ServerUrlValidation.Valid -> {
                viewModelScope.launch {
                    _serverSaveState.value = ServerSaveState.Checking
                    when (val result = serverHealthChecker.check(validation.healthCheckBase)) {
                        is ServerHealthResult.Alive -> {
                            settingsStore.setServerUrl(validation.storedValue)
                            refreshLabMode()
                            _serverSaveState.value = ServerSaveState.Saved(result.environment)
                        }
                        is ServerHealthResult.Unreachable ->
                            _serverSaveState.value = ServerSaveState.Error(result.message)
                    }
                }
            }
        }
    }

    fun refreshConnections() {
        viewModelScope.launch {
            val result = Platform.publishable.associateWith { platform ->
                runCatching { platformAuthApi.status(platform.apiValue).connected }.getOrDefault(false)
            }
            _connections.value = result
        }
    }

    fun discoverPc() {
        localPcDiscovery.start { name, url -> _discoveredPc.value = name to url }
    }

    fun stopDiscoveringPc() = localPcDiscovery.stop()

    fun disconnect(platform: Platform) {
        viewModelScope.launch {
            val installationId = settingsStore.getOrCreateInstallId()
            val deviceName = settingsStore.getOrCreateDeviceName()
            runCatching {
                platformAuthApi.disconnect(platform.apiValue, installationId, deviceName, source = "android")
            }
            refreshConnections()
        }
    }

    suspend fun connectUrl(platform: Platform): String? = runCatching {
        val installationId = settingsStore.getOrCreateInstallId()
        val deviceName = settingsStore.getOrCreateDeviceName()
        when (platform) {
            Platform.YOUTUBE -> platformAuthApi.youtubeAuthUrl(installationId = installationId, deviceName = deviceName).url
            Platform.INSTAGRAM -> platformAuthApi.instagramAuthUrl(installationId = installationId, deviceName = deviceName).url
            Platform.TIKTOK -> platformAuthApi.tiktokAuthUrl(installationId = installationId, deviceName = deviceName).url
            else -> null
        }
    }.getOrNull()

    // AuthState.LoggedOut dispara solo (TokenStore.authState) -- EsseAnalyticsNavHost
    // ya reacciona mostrando LoginScreen, no hace falta navegar a mano.
    fun logout() {
        tokenStore.clear()
    }
}

sealed interface ServerSaveState {
    data object Idle : ServerSaveState
    data object Checking : ServerSaveState
    data class Saved(val environment: String?) : ServerSaveState
    data class Error(val message: String) : ServerSaveState
}
