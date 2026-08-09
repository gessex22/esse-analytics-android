package com.esseanalytics.android.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.LabModeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Punto único que decide "mostrar Login o la app". TokenStore.authState ya
// refleja tanto un login exitoso como un logout forzado por AuthAuthenticator
// (core:network, ante un 401 sin refresh token posible) — no hace falta
// escuchar nada más acá.
@HiltViewModel
class SessionViewModel @Inject constructor(
    tokenStore: TokenStore,
    private val labModeStatus: LabModeStatus,
) : ViewModel() {
    val authState = tokenStore.authState

    // Ver core/network/LabModeStatus.kt -- chequeo en vivo de GET /api/health,
    // nunca heurística de URL. Se dispara una sola vez al loguearse (el
    // Retrofit singleton ya fija el servidor al arrancar el proceso, ver el
    // TODO en NetworkModule.kt -- cambiar de servidor requiere reiniciar la
    // app, así que revisar más de una vez por sesión no aporta nada).
    private val _isLabMode = MutableStateFlow(false)
    val isLabMode: StateFlow<Boolean> = _isLabMode

    init {
        viewModelScope.launch { _isLabMode.value = labModeStatus.isActive() }
    }
}
