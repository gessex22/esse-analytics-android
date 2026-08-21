package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.LocalPcVideosResponse
import com.esseanalytics.android.core.network.dto.LocalPcInstagramUploadRequest
import com.esseanalytics.android.core.network.dto.LocalPcTiktokUploadRequest
import com.esseanalytics.android.core.network.dto.LocalPcYoutubeUploadRequest
import com.esseanalytics.android.core.network.dto.LocalPcPlatformLinksDto
import com.esseanalytics.android.core.network.dto.LocalPcSetPlatformLinkRequest
import com.esseanalytics.android.core.network.dto.LocalPcUpdatePlatformsRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

// Contrato de local-backend (la PC del usuario, no la central) consumido
// directo por LAN -- ver docs/lan-library-auto-switch-design-2026-08-16.md.
// Instanciado on-demand contra la IP de la PC descubierta (ver
// LocalBackendApiFactory), nunca contra @CentralRetrofit. streamUrl/
// thumbnailUrl NO están acá -- son URLs "peladas" que ExoPlayer/Coil cargan
// directo (ver LocalPcUrls.kt), mismo criterio que remoteLibraryStreamUrl.
//
// Las respuestas de upload se leen como Response<ResponseBody> crudo, sin
// forzar un shape de éxito -- el contrato exacto de {ok, ...} de cada
// controller no está confirmado campo por campo; isSuccessful (2xx) alcanza
// para saber si la PC aceptó la publicación, y errorBody() para mostrar el
// motivo si no.
interface LocalBackendApi {
    // limit default del server es 10 (video.controller.ts) -- se manda 100
    // explícito. content_status default del server ya es "no_completo" (lo
    // mismo que Videos en Electron/web usa por default), alcanza para traer
    // los pendientes sin mandar el filtro a mano.
    @Headers("Cache-Control: no-cache")
    @GET("api/videos")
    suspend fun listVideos(
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 1,
    ): LocalPcVideosResponse

    @POST("api/youtube/upload")
    suspend fun uploadYoutube(@Body body: LocalPcYoutubeUploadRequest): Response<ResponseBody>

    @POST("api/instagram/upload")
    suspend fun uploadInstagram(@Body body: LocalPcInstagramUploadRequest): Response<ResponseBody>

    @POST("api/tiktok/upload")
    suspend fun uploadTiktok(@Body body: LocalPcTiktokUploadRequest): Response<ResponseBody>

    @PATCH("api/videos/{fileId}/platforms")
    suspend fun updatePlatforms(
        @Path("fileId") fileId: String,
        @Body body: LocalPcUpdatePlatformsRequest,
    ): Response<ResponseBody>

    @GET("api/videos/{fileId}/platform-links")
    suspend fun getPlatformLinks(@Path("fileId") fileId: String): LocalPcPlatformLinksDto

    @PATCH("api/videos/{fileId}/platform-link/{platform}")
    suspend fun setPlatformLink(
        @Path("fileId") fileId: String,
        @Path("platform") platform: String,
        @Body body: LocalPcSetPlatformLinkRequest,
    ): Response<ResponseBody>
}
