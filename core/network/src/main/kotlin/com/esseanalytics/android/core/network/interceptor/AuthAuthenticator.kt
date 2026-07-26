package com.esseanalytics.android.core.network.interceptor

import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.AuthEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

// No hay endpoint de refresh (JWT vive 7 días, backend/src/controllers/
// auth.controller.ts) — así que a diferencia del patrón típico de
// Authenticator (conseguir una credencial nueva y reintentar), acá un 401
// significa "sesión vencida, punto": se limpia el token guardado y se avisa
// para volver a Login. Se implementa como Authenticator (no Interceptor) para
// que componga bien con los reintentos propios de OkHttp — devolver null le
// dice "no hay nada que reintentar, dejalo fallar".
class AuthAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authEventBus: AuthEventBus,
) : Authenticator {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun authenticate(route: Route?, response: Response): Request? {
        // Un 401 de estos endpoints significa que venció la cuenta de la
        // plataforma, no el JWT de Esse Analytics. No debemos cerrar la sesión
        // completa del usuario por eso.
        val platform = response.request.url.encodedPath
            .substringAfter("/api/", "")
            .substringBefore('/')
            .takeIf { it in setOf("youtube", "instagram", "tiktok") }
        if (platform != null) {
            scope.launch { authEventBus.emitPlatformSessionExpired(platform) }
            return null
        }
        tokenStore.clear()
        scope.launch { authEventBus.emitSessionExpired() }
        return null
    }
}
