package com.esseanalytics.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.WorkflowMode
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

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
        viewModelScope.launch { settingsStore.setServerUrl(value) }
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
            runCatching { platformAuthApi.disconnect(platform.apiValue) }
            refreshConnections()
        }
    }

    suspend fun connectUrl(platform: Platform): String? = runCatching {
        when (platform) {
            Platform.YOUTUBE -> platformAuthApi.youtubeAuthUrl().url
            Platform.INSTAGRAM -> platformAuthApi.instagramAuthUrl().url
            Platform.TIKTOK -> platformAuthApi.tiktokAuthUrl().url
            else -> null
        }
    }.getOrNull()

    // AuthState.LoggedOut dispara solo (TokenStore.authState) -- EsseAnalyticsNavHost
    // ya reacciona mostrando LoginScreen, no hace falta navegar a mano.
    fun logout() {
        tokenStore.clear()
    }
}
