package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.RemoteLibraryListResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryUploadResponse
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
import okhttp3.MultipartBody
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
// central, no un mirror de metadata como SyncApi/backup. El video se sube por
// TUS resumable (ver core/network/tus/TUSUploadClient.kt), no por acá -- la
// central migró de multipart single-shot a TUS y este método quedó apuntando
// a una ruta que ya no existe (bug real: subir a Nube estaba roto). La
// miniatura sí sigue siendo multipart simple, aparte, DESPUÉS de crear el
// video (mismo orden que RemoteLibraryAPI.upload en iOS).
interface RemoteLibraryApi {
    @GET("api/remote-library/videos")
    suspend fun listVideos(): RemoteLibraryListResponse

    @Multipart
    @POST("api/remote-library/videos/{id}/thumbnail")
    suspend fun uploadThumbnail(
        @Path("id") id: String,
        @Part thumbnail: MultipartBody.Part,
    ): RemoteLibraryUploadResponse

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
