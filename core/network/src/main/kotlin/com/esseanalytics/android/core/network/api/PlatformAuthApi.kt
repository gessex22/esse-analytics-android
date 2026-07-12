package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.AuthUrlResponse
import com.esseanalytics.android.core.network.dto.InstagramTokenResponse
import com.esseanalytics.android.core.network.dto.TiktokTokenResponse
import com.esseanalytics.android.core.network.dto.YoutubeTokenResponse
import retrofit2.http.GET
import retrofit2.http.Query

// Endpoints "de token": la central los devuelve para que la app suba DIRECTO
// contra graph.facebook.com / open.tiktokapis.com / googleapis.com — los bytes
// del video nunca pasan por la central. Ver Parte A del plan para el
// ?client=android que hace que el callback de auth/url vuelva por deep link
// (essenalytics://oauth-callback) en vez de la página HTML pensada para popup.
interface PlatformAuthApi {
    @GET("api/instagram/token")
    suspend fun instagramToken(): InstagramTokenResponse

    @GET("api/instagram/auth/url")
    suspend fun instagramAuthUrl(@Query("client") client: String = "android"): AuthUrlResponse

    @GET("api/tiktok/token")
    suspend fun tiktokToken(): TiktokTokenResponse

    @GET("api/tiktok/auth/url")
    suspend fun tiktokAuthUrl(@Query("client") client: String = "android"): AuthUrlResponse

    @GET("api/youtube/token")
    suspend fun youtubeToken(): YoutubeTokenResponse

    @GET("api/youtube/auth/url")
    suspend fun youtubeAuthUrl(@Query("client") client: String = "android"): AuthUrlResponse
}
