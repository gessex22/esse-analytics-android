package com.esseanalytics.android.feature.upload

import android.graphics.Bitmap
import android.util.Base64
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import com.esseanalytics.android.core.network.dto.SetYoutubeThumbnailRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
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
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
    @PlatformOkHttp private val httpClient: OkHttpClient,
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

            // Mejor esfuerzo, igual que desktop (YoutubeUploadView.tsx): el
            // video YA está publicado acá, si esto falla no se reintenta la
            // subida entera por una miniatura -- solo se pierde la portada
            // elegida a mano (queda la que YouTube generó sola).
            metadata.thumbnailOffsetMs?.let { offsetMs ->
                setCustomThumbnail(file, offsetMs, videoId, token)
            }

            UploadResult.Success(platformId = videoId, platformUrl = "https://youtube.com/shorts/$videoId")
        } catch (e: IOException) {
            UploadResult.Failure(e.message ?: "Error de red al subir a YouTube.", retryable = true)
        }
    }

    // youtube.thumbnails.set vive en la CENTRAL (backend/), no en la subida
    // directa -- YouTube tarda unos segundos en aceptar una miniatura recién
    // subido el video, por eso los reintentos con espera (mismos 3 intentos /
    // 2.5s que ya usa desktop).
    private suspend fun setCustomThumbnail(file: File, offsetMs: Long, videoId: String, token: String) {
        val frame = thumbnailGenerator.captureFrame(MediaSource.LocalFile(file), offsetMs) ?: return
        val base64 = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        }
        frame.recycle()

        repeat(THUMBNAIL_SET_ATTEMPTS) { attempt ->
            val success = runCatching {
                platformAuthApi.setYoutubeThumbnail(videoId, SetYoutubeThumbnailRequest("data:image/jpeg;base64,$base64"))
            }.isSuccess
            if (success) return
            if (attempt < THUMBNAIL_SET_ATTEMPTS - 1) delay(THUMBNAIL_SET_RETRY_DELAY_MS)
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

    private companion object {
        const val THUMBNAIL_SET_ATTEMPTS = 3
        const val THUMBNAIL_SET_RETRY_DELAY_MS = 2_500L
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
