package com.esseanalytics.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Label del TextButton inferior de LoginScreen ("Servidor: X"): vacío =
    // "Central"; con URL custom guardada se muestra host:puerto/path, sin
    // esquema ni barra final. Igual que el resto de la red, el cambio recién
    // aplica al reiniciar la app (ver TODO de NetworkModule).
    val serverLabel: StateFlow<String> = settingsStore.serverUrl
        .map(::serverDisplayLabel)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Central")

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Completá usuario y contraseña") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            authRepository.login(state.username.trim(), state.password)
                .onSuccess { _uiState.update { it.copy(loading = false, loggedIn = true) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message ?: "Error al iniciar sesión") } }
        }
    }

    fun register(username: String, password: String) {
        if (username.isBlank() || password.length < 6) {
            _uiState.update { it.copy(error = "Usuario requerido y contraseña de al menos 6 caracteres") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            authRepository.register(username.trim(), password)
                .onSuccess { _uiState.update { it.copy(loading = false, loggedIn = true) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message ?: "Error al registrar") } }
        }
    }

    fun setPasswordMismatch() {
        _uiState.update { it.copy(error = "Las contraseñas no coinciden") }
    }
}

private fun serverDisplayLabel(url: String): String =
    url.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .ifBlank { "Central" }
