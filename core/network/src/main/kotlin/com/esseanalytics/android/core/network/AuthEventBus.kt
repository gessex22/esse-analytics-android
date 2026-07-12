package com.esseanalytics.android.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// El AuthAuthenticator (dentro de core:network) no puede navegar directo a la
// pantalla de login — no tiene noción de UI. Emite acá, y app/ (que sí tiene
// el NavHost) escucha este evento para forzar la vuelta a Login con el
// mensaje "tu sesión expiró".
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    suspend fun emitSessionExpired() = _events.emit(AuthEvent.SessionExpired)
}

sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}
