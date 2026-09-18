package com.esseanalytics.android.core.network.interceptor

import com.esseanalytics.android.core.datastore.TokenSessionGuard
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
//
// Cuidado con carrera de sesión: OkHttp puede llamar a authenticate() para un
// 401 de una request que salió con el Bearer de una sesión vieja (A), después
// de que el usuario ya inició sesión con otra cuenta (B) en el tiempo que
// tardó esa request en volver. Por eso NUNCA se decide leyendo tokenStore.token
// "tarde" (en el momento de procesar la respuesta): se extrae el Bearer exacto
// que la propia request fallida mandó (response.request) y se compara contra
// la sesión vigente de forma atómica vía TokenStore.clearIfCurrent — si no
// matchea, es un 401 tardío de una sesión ya reemplazada y se ignora.
class AuthAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authEventBus: AuthEventBus,
) : Authenticator {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun authenticate(route: Route?, response: Response): Request? {
        handleUnauthorized(response, tokenStore, authEventBus, scope)
        return null
    }
}

private val PLATFORM_PATHS = setOf("youtube", "instagram", "tiktok")
private const val BEARER_PREFIX = "Bearer "

// Lógica pura (sin Android/Hilt) del manejo de un 401, separada de
// AuthAuthenticator para poder testearla con un TokenSessionGuard fake y sin
// depender de EncryptedSharedPreferences/Context.
internal fun handleUnauthorized(
    response: Response,
    tokenGuard: TokenSessionGuard,
    authEventBus: AuthEventBus,
    scope: CoroutineScope,
) {
    val bearer = response.request.header("Authorization")
        ?.takeIf { it.startsWith(BEARER_PREFIX) }
        ?.removePrefix(BEARER_PREFIX)
        ?.takeIf { it.isNotBlank() }
        ?: return

    // Un 401 de estos endpoints significa que venció la cuenta de la
    // plataforma, no el JWT de Esse Analytics. No debemos cerrar la sesión
    // completa del usuario por eso -- y nunca se limpia la central acá.
    val platform = response.request.url.encodedPath
        .substringAfter("/api/", "")
        .substringBefore('/')
        .takeIf { it in PLATFORM_PATHS }

    if (platform != null) {
        if (bearer == tokenGuard.token) {
            scope.launch { authEventBus.emitPlatformSessionExpired(platform) }
        }
        return
    }

    if (tokenGuard.clearIfCurrent(bearer)) {
        scope.launch { authEventBus.emitSessionExpired() }
    }
}
