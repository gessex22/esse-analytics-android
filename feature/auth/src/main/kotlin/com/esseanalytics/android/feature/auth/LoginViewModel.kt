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
    val email: String = "",
    val password: String = "",
    val registeredEmail: String? = null,
    val info: String? = null,
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
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

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

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || !email.contains('@') || password.length !in 15..128) {
            _uiState.update { it.copy(error = "Usuario, correo válido y contraseña de 15 a 128 caracteres requeridos") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            authRepository.register(username.trim(), password, email.trim())
                .onSuccess { destination -> _uiState.update { it.copy(loading = false, registeredEmail = destination, info = "Enviamos un enlace a $destination. Confirma tu correo antes de iniciar sesión.") } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message ?: "Error al registrar") } }
        }
    }

    fun resendVerification() {
        val email = _uiState.value.registeredEmail ?: _uiState.value.email
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            authRepository.resendVerification(email)
                .onSuccess { message -> _uiState.update { it.copy(loading = false, info = message) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message ?: "No se pudo reenviar") } }
        }
    }

    fun forgotPassword(email: String) {
        if (!email.contains('@')) { _uiState.update { it.copy(error = "Ingresa un correo válido") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, info = null) }
            authRepository.forgotPassword(email.trim())
                .onSuccess { message -> _uiState.update { it.copy(loading = false, info = message) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message ?: "No se pudo enviar la solicitud") } }
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
