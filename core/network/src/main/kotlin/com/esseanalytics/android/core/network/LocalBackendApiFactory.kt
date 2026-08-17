package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.LocalBackendApi
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

// Segundo cliente Retrofit, apuntando a la IP de la PC en la LAN en vez de a
// la central -- on-demand (no @Provides/@Singleton de NetworkModule) porque
// la IP cambia por sesión de descubrimiento, a diferencia de CENTRAL_BASE_URL
// que es fija. Mismo criterio que APIClient.lan(baseURL:) en iOS.
//
// baseClient (el OkHttpClient sin @PlatformOkHttp, inyectado por default)
// YA tiene AuthInterceptor -- se reusa vía newBuilder() porque el mismo JWT
// de la central sirve para local-backend (JWT_SECRET compartido, ver
// docs/lan-library-auto-switch-design-2026-08-16.md sección 1). Pero:
// - .authenticator(Authenticator.NONE) reemplaza a AuthAuthenticator: un 401
//   de una PC ajena (dueño distinto, ver requireOwnerOrNoOwnerSet en
//   local-backend) NO debe desloguear la sesión principal de la app, y
//   tampoco debe disparar el heurístico de "reconectá YouTube/Instagram/
//   TikTok" de AuthAuthenticator (que interpretaría mal el path
//   /api/{youtube,instagram,tiktok}/upload, IDÉNTICO al que usan las 3
//   plataformas reales).
// - .cache(null) evita que el network interceptor de NetworkModule (fuerza
//   max-age=60 a cualquier GET sin Cache-Control propio) cachee el listado
//   de la PC en el mismo disco que la central -- local-backend no manda
//   Cache-Control, así que sin esto GET /api/videos quedaría pegado 60s.
@Singleton
class LocalBackendApiFactory @Inject constructor(
    private val baseClient: OkHttpClient,
    private val json: Json,
) {
    fun create(baseUrl: String): LocalBackendApi {
        val normalized = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        val lanClient = baseClient.newBuilder()
            .authenticator(Authenticator.NONE)
            .cache(null)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(lanClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LocalBackendApi::class.java)
    }
}
