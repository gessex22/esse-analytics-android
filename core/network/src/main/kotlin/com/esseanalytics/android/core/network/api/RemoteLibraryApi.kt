package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.RemoteLibraryListResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryUploadResponse
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

// Biblioteca remota (owner-only, ver Parte C del plan) -- storage real en la
// central, no un mirror de metadata como SyncApi/backup. thumbnail es
// opcional (Retrofit omite un @Part null del body multipart, no hace falta
// una sobrecarga aparte); duration/resolution/formato los manda ESTE cliente
// porque la central no tiene ffmpeg -- ver AndroidMediaProber.
interface RemoteLibraryApi {
    @Multipart
    @POST("api/remote-library/videos")
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part,
        @Part thumbnail: MultipartBody.Part? = null,
        @Part("fileName") fileName: RequestBody,
        @Part("durationSeconds") durationSeconds: RequestBody? = null,
        @Part("resolution") resolution: RequestBody? = null,
        @Part("formato") formato: RequestBody? = null,
    ): RemoteLibraryUploadResponse

    @GET("api/remote-library/videos")
    suspend fun listVideos(): RemoteLibraryListResponse

    // @Streaming: evita que OkHttp lea el body entero a memoria antes de
    // devolverlo -- RemoteUploadWorker copia byteStream() a un temporal en
    // cacheDir en vez de necesitar el video completo en RAM.
    @Streaming
    @GET("api/remote-library/videos/{id}/stream")
    suspend fun streamVideo(@Path("id") id: String): ResponseBody

    @PATCH("api/remote-library/videos/{id}")
    suspend fun updatePlatforms(
        @Path("id") id: String,
        @Body body: UpdateRemoteLibraryPlatformsRequest,
    ): RemoteLibraryUploadResponse

    @DELETE("api/remote-library/videos/{id}")
    suspend fun deleteVideo(@Path("id") id: String)
}
