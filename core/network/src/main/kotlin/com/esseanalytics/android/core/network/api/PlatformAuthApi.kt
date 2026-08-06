package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.AuthUrlResponse
import com.esseanalytics.android.core.network.dto.ConnectionStatusDto
import com.esseanalytics.android.core.network.dto.InstagramTokenResponse
import com.esseanalytics.android.core.network.dto.SetYoutubeThumbnailRequest
import com.esseanalytics.android.core.network.dto.TiktokTokenResponse
import com.esseanalytics.android.core.network.dto.YoutubeTokenResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Endpoints "de token": la central los devuelve para que la app suba DIRECTO
// contra graph.facebook.com / open.tiktokapis.com / googleapis.com — los bytes
// del video nunca pasan por la central. Ver Parte A del plan para el
// ?client=android que hace que el callback de auth/url vuelva por deep link
// (essenalytics://oauth-callback) en vez de la página HTML pensada para popup.
interface PlatformAuthApi {
    @GET("api/{platform}/auth/status")
    suspend fun status(@Path("platform") platform: String): ConnectionStatusDto

    // installationId/deviceName (Fase 5, auditoría central): nulos por default
    // -- Retrofit omite un @Query nulo en vez de mandar "null" literal, así que
    // un caller que no los pase se comporta igual que antes (sin evento de
    // auditoría, no rompe nada). disconnect() los manda directo como query
    // params; los *AuthUrl() los reenvían en el `state` de OAuth (viaje hasta
    // el callback, que es un redirect del navegador/Custom Tab, no un request
    // directo de esta app -- ver oauth-state.ts del backend).
    @retrofit2.http.DELETE("api/{platform}/auth")
    suspend fun disconnect(
        @Path("platform") platform: String,
        @Query("installationId") installationId: String? = null,
        @Query("deviceName") deviceName: String? = null,
        @Query("source") source: String? = null,
    )
    @GET("api/instagram/token")
    suspend fun instagramToken(): InstagramTokenResponse

    @GET("api/instagram/auth/url")
    suspend fun instagramAuthUrl(
        @Query("client") client: String = "android",
        @Query("installationId") installationId: String? = null,
        @Query("deviceName") deviceName: String? = null,
    ): AuthUrlResponse

    @GET("api/tiktok/token")
    suspend fun tiktokToken(): TiktokTokenResponse

    @GET("api/tiktok/auth/url")
    suspend fun tiktokAuthUrl(
        @Query("client") client: String = "android",
        @Query("installationId") installationId: String? = null,
        @Query("deviceName") deviceName: String? = null,
    ): AuthUrlResponse

    @GET("api/youtube/token")
    suspend fun youtubeToken(): YoutubeTokenResponse

    @GET("api/youtube/auth/url")
    suspend fun youtubeAuthUrl(
        @Query("client") client: String = "android",
        @Query("installationId") installationId: String? = null,
        @Query("deviceName") deviceName: String? = null,
    ): AuthUrlResponse

    // Mismo endpoint que ya usa frontend/src/components/YoutubeUploadView.tsx
    // (ThumbnailScrubber) tras un upload exitoso -- youtube.thumbnails.set vive
    // en la CENTRAL (backend/), no en local-backend, a diferencia de las subidas
    // en sí. YouTube tarda unos segundos en aceptarla después de recién subido
    // el video -- YoutubeUploader reintenta con backoff, ver ese archivo.
    @POST("api/youtube/thumbnail/{videoId}")
    suspend fun setYoutubeThumbnail(@Path("videoId") videoId: String, @Body body: SetYoutubeThumbnailRequest)
}
