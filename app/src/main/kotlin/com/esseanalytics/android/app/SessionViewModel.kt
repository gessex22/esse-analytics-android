package com.esseanalytics.android.app

import androidx.lifecycle.ViewModel
import com.esseanalytics.android.core.datastore.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Punto único que decide "mostrar Login o la app". TokenStore.authState ya
// refleja tanto un login exitoso como un logout forzado por AuthAuthenticator
// (core:network, ante un 401 sin refresh token posible) — no hace falta
// escuchar nada más acá.
@HiltViewModel
class SessionViewModel @Inject constructor(
    tokenStore: TokenStore,
) : ViewModel() {
    val authState = tokenStore.authState
}
