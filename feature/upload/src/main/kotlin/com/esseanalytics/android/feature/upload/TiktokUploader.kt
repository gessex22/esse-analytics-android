package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

// TikTok Content Posting API v2, FILE_UPLOAD directo por chunks (NO
// PULL_FROM_URL, que necesitaría un link público) -- mismo flujo que ya
// corre bien en local-backend/src/controllers/tiktok-upload.controller.ts
// (a diferencia del de la central, que usa PULL_FROM_URL): init declarando
// chunk_size/total_chunk_count, PUT por chunk con Content-Range, poll de
// status hasta PUBLISH_COMPLETE.
@Singleton
class TiktokUploader @Inject constructor(
    private val platformAuthApi: PlatformAuthApi,
    @field:PlatformOkHttp private val httpClient: OkHttpClient,
) : PlatformUploader {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        return try {
            val tokenInfo = platformAuthApi.tiktokToken()
            val token = tokenInfo.access_token

            val init = withContext(Dispatchers.IO) {
                initUpload(token, file, metadata)
            } ?: return UploadResult.Failure("TikTok no devolvió una sesión de subida.", retryable = true)

            val uploaded = withContext(Dispatchers.IO) {
                uploadChunks(init.uploadUrl, file, onProgress)
            }
            if (!uploaded) {
                return UploadResult.Failure("TikTok rechazó la subida de uno de los chunks.", retryable = true)
            }

            val finalStatus = pollUntilComplete(token, init.publishId)
                ?: return UploadResult.Failure(
                    "TikTok sigue procesando el video después de varios minutos.",
                    retryable = true,
                )

            if (finalStatus.status == "FAILED") {
                return UploadResult.Failure(
                    finalStatus.failReason ?: "TikTok rechazó el video al procesarlo.",
                    retryable = false,
                )
            }

            // TikTok no siempre devuelve un link directo utilizable acá (hace
            // falta el username, que no viene en /api/tiktok/token) -- queda
            // vacío, igual que Instagram cuando no hay permalink; la
            // resolución real de link pasa por el Sync/cross-match que ya
            // existe en desktop.
            UploadResult.Success(platformId = init.publishId, platformUrl = "")
        } catch (e: IOException) {
            UploadResult.Failure(e.message ?: "Error de red al subir a TikTok.", retryable = true)
        }
    }

    private fun initUpload(token: String, file: File, metadata: UploadMetadata): TiktokInitResponseData? {
        val videoSize = file.length()
        val chunkSize = minOf(CHUNK_SIZE_BYTES, videoSize)
        val totalChunks = ceil(videoSize.toDouble() / chunkSize).toInt().coerceAtLeast(1)

        val body = json.encodeToString(
            TiktokInitRequest(
                postInfo = TiktokInitRequest.PostInfo(
                    title = metadata.title,
                    privacyLevel = mapPrivacyLevel(metadata.privacyStatus),
                ),
                sourceInfo = TiktokInitRequest.SourceInfo(
                    videoSize = videoSize,
                    chunkSize = chunkSize,
                    totalChunkCount = totalChunks,
                ),
            ),
        )

        val request = Request.Builder()
            .url("https://open.tiktokapis.com/v2/post/publish/video/init/")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            runCatching { json.decodeFromString<TiktokInitResponse>(bodyText) }.getOrNull()?.data
        }
    }

    private fun uploadChunks(uploadUrl: String, file: File, onProgress: (Float) -> Unit): Boolean {
        val videoSize = file.length()
        val chunkSize = minOf(CHUNK_SIZE_BYTES, videoSize)
        val totalChunks = ceil(videoSize.toDouble() / chunkSize).toInt().coerceAtLeast(1)

        RandomAccessFile(file, "r").use { raf ->
            for (chunkIndex in 0 until totalChunks) {
                val start = chunkIndex * chunkSize
                val end = minOf(start + chunkSize, videoSize) - 1
                val length = (end - start + 1).toInt()
                val buffer = ByteArray(length)
                raf.seek(start)
                raf.readFully(buffer)

                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Content-Range", "bytes $start-$end/$videoSize")
                    .addHeader("Content-Type", "video/mp4")
                    .put(buffer.toRequestBody("video/mp4".toMediaType()))
                    .build()

                val chunkSuccess = httpClient.newCall(request).execute().use { response -> response.isSuccessful }
                if (!chunkSuccess) return false
                onProgress((chunkIndex + 1).toFloat() / totalChunks)
            }
        }
        return true
    }

    private suspend fun pollUntilComplete(token: String, publishId: String): TiktokStatusData? {
        repeat(MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            val body = json.encodeToString(TiktokStatusRequest(publishId))
            val request = Request.Builder()
                .url("https://open.tiktokapis.com/v2/post/publish/status/fetch/")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val status = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bodyText = response.body?.string().orEmpty()
                    runCatching { json.decodeFromString<TiktokStatusResponse>(bodyText) }.getOrNull()?.data
                }
            }
            if (status?.status == "PUBLISH_COMPLETE" || status?.status == "FAILED") return status
        }
        return null
    }

    private fun mapPrivacyLevel(privacyStatus: String): String =
        if (privacyStatus == "public") "PUBLIC_TO_EVERYONE" else "SELF_ONLY"

    private companion object {
        const val CHUNK_SIZE_BYTES = 10L * 1024 * 1024 // 10MB, igual que desktop
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_POLL_ATTEMPTS = 60 // ~5 minutos
    }
}

@Serializable
private data class TiktokInitRequest(
    @SerialName("post_info") val postInfo: PostInfo,
    @SerialName("source_info") val sourceInfo: SourceInfo,
) {
    @Serializable
    data class PostInfo(
        val title: String,
        @SerialName("privacy_level") val privacyLevel: String,
    )

    @Serializable
    data class SourceInfo(
        val source: String = "FILE_UPLOAD",
        @SerialName("video_size") val videoSize: Long,
        @SerialName("chunk_size") val chunkSize: Long,
        @SerialName("total_chunk_count") val totalChunkCount: Int,
    )
}

@Serializable
private data class TiktokInitResponse(val data: TiktokInitResponseData? = null)

@Serializable
private data class TiktokInitResponseData(
    @SerialName("publish_id") val publishId: String,
    @SerialName("upload_url") val uploadUrl: String,
)

@Serializable
private data class TiktokStatusRequest(@SerialName("publish_id") val publishId: String)

@Serializable
private data class TiktokStatusResponse(val data: TiktokStatusData? = null)

@Serializable
private data class TiktokStatusData(
    val status: String? = null,
    @SerialName("fail_reason") val failReason: String? = null,
)
