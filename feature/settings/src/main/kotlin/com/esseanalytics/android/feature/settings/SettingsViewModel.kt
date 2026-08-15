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

    fun setServerUrl(value: String) {
        viewModelScope.launch {
            settingsStore.setServerUrl(value)
            // OJO: NetworkModule ya construyó el Retrofit singleton con la URL
            // vieja al arrancar el proceso (ver el TODO ahí) -- este chequeo
            // sigue pegándole a esa baseUrl fija hasta que se reinicie la app,
            // igual que el resto de la red. La etiqueta puede quedar
            // desactualizada hasta el restart -- comportamiento ya conocido,
            // no nuevo de este cambio.
            refreshLabMode()
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
