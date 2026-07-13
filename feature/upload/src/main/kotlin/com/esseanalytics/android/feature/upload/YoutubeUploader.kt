package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Resumable upload de YouTube Data API v3 -- mismo endpoint que ya usa
// desktop (local-backend/src/controllers/youtube-upload.controller.ts), pero
// acá streamea desde disco en un solo PUT (ProgressRequestBody), a
// diferencia de desktop que carga el archivo entero a memoria -- ver el
// plan. Un PUT completo a la URL de sesión resumable es válido igual (no
// hace falta partirlo en chunks); chunking real con reanudación tras un
// fallo a mitad de subida queda para más adelante si el uso real lo pide.
@Singleton
class YoutubeUploader @Inject constructor(
    private val platformAuthApi: PlatformAuthApi,
    @field:PlatformOkHttp private val httpClient: OkHttpClient,
) : PlatformUploader {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        return try {
            val token = platformAuthApi.youtubeToken().access_token

            val sessionUrl = withContext(Dispatchers.IO) {
                startResumableSession(token, file, metadata)
            } ?: return UploadResult.Failure("YouTube no devolvió una sesión de subida.", retryable = true)

            val videoId = withContext(Dispatchers.IO) {
                uploadFileToSession(sessionUrl, file, onProgress)
            } ?: return UploadResult.Failure("YouTube no confirmó la subida.", retryable = true)

            UploadResult.Success(platformId = videoId, platformUrl = "https://youtube.com/shorts/$videoId")
        } catch (e: IOException) {
            UploadResult.Failure(e.message ?: "Error de red al subir a YouTube.", retryable = true)
        }
    }

    private fun startResumableSession(token: String, file: File, metadata: UploadMetadata): String? {
        val body = json.encodeToString(
            YoutubeUploadRequest(
                snippet = YoutubeUploadRequest.Snippet(
                    title = metadata.title,
                    description = metadata.description,
                    tags = metadata.tags,
                ),
                status = YoutubeUploadRequest.Status(privacyStatus = metadata.privacyStatus),
            ),
        )

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Upload-Content-Type", "video/*")
            .addHeader("X-Upload-Content-Length", file.length().toString())
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.header("Location")
        }
    }

    private fun uploadFileToSession(sessionUrl: String, file: File, onProgress: (Float) -> Unit): String? {
        val requestBody = ProgressRequestBody(file, "video/*".toMediaType(), onProgress)
        val request = Request.Builder()
            .url(sessionUrl)
            .put(requestBody)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            runCatching { json.decodeFromString<YoutubeUploadResponse>(bodyText) }.getOrNull()?.id
        }
    }
}

@Serializable
private data class YoutubeUploadRequest(
    val snippet: Snippet,
    val status: Status,
) {
    @Serializable
    data class Snippet(val title: String, val description: String, val tags: List<String>)

    @Serializable
    data class Status(val privacyStatus: String)
}

@Serializable
private data class YoutubeUploadResponse(val id: String)
