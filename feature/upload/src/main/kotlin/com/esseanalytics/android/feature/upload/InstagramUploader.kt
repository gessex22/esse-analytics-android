package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Instagram Reels vía Graph API Content Publishing + Resumable Upload --
// mismo flujo que local-backend/src/controllers/instagram-upload.controller.ts:
// 1) crear el contenedor (form-urlencoded), 2) UN solo POST con el archivo
// completo a la "uri" que devuelve (Meta rechaza subida en chunks acá --
// tira ProcessingFailedError, confirmado en el código de escritorio),
// 3) poll de status_code hasta FINISHED, 4) publish, 5) permalink real.
//
// NOTA: los headers exactos del POST del paso 2 (Authorization: OAuth
// <token> + file_offset: 0) siguen el protocolo de Resumable Upload de Meta
// tal como está documentado -- no se pudo probar contra una cuenta real en
// este entorno, conviene confirmarlo con una subida de prueba real.
@Singleton
class InstagramUploader @Inject constructor(
    private val platformAuthApi: PlatformAuthApi,
    @field:PlatformOkHttp private val httpClient: OkHttpClient,
) : PlatformUploader {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        return try {
            val tokenInfo = platformAuthApi.instagramToken()
            val token = tokenInfo.access_token
            val igUserId = tokenInfo.instagram_user_id

            val container = withContext(Dispatchers.IO) {
                createContainer(igUserId, token, metadata)
            } ?: return UploadResult.Failure("Instagram no devolvió un contenedor de subida.", retryable = true)

            val uploaded = withContext(Dispatchers.IO) {
                uploadFileToContainer(container.uri, token, file, onProgress)
            }
            if (!uploaded) {
                return UploadResult.Failure("Instagram rechazó la subida del archivo.", retryable = true)
            }

            val pollResult = pollUntilFinished(container.id, token)
            if (pollResult is PollResult.Timeout) {
                return UploadResult.Failure(
                    "Instagram sigue procesando el video después de varios minutos.",
                    retryable = true,
                )
            } else if (pollResult is PollResult.Error) {
                return UploadResult.Failure("Instagram reportó un error al procesar el video.", retryable = false)
            }

            val mediaId = withContext(Dispatchers.IO) {
                publish(igUserId, container.id, token)
            } ?: return UploadResult.Failure("Instagram no confirmó la publicación.", retryable = true)

            val permalink = withContext(Dispatchers.IO) {
                fetchPermalink(mediaId, token) ?: ""
            }
            UploadResult.Success(platformId = mediaId, platformUrl = permalink)
        } catch (e: IOException) {
            UploadResult.Failure(e.message ?: "Error de red al subir a Instagram.", retryable = true)
        }
    }

    private fun createContainer(igUserId: String, token: String, metadata: UploadMetadata): IgContainerResponse? {
        val form = FormBody.Builder()
            .add("media_type", "REELS")
            .add("upload_type", "resumable")
            .add("caption", metadata.title + if (metadata.description.isNotBlank()) "\n\n${metadata.description}" else "")
            .add("share_to_feed", "true")
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("https://graph.facebook.com/v22.0/$igUserId/media")
            .post(form)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            runCatching { json.decodeFromString<IgContainerResponse>(bodyText) }.getOrNull()
        }
    }

    private fun uploadFileToContainer(uri: String, token: String, file: File, onProgress: (Float) -> Unit): Boolean {
        val requestBody = ProgressRequestBody(file, "video/*".toMediaType(), onProgress)
        val request = Request.Builder()
            .url(uri)
            .addHeader("Authorization", "OAuth $token")
            .addHeader("file_offset", "0")
            .post(requestBody)
            .build()

        return httpClient.newCall(request).execute().use { response -> response.isSuccessful }
    }

    private suspend fun pollUntilFinished(containerId: String, token: String): PollResult {
        repeat(MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            val request = Request.Builder()
                .url("https://graph.facebook.com/v22.0/$containerId?fields=status_code,status&access_token=$token")
                .get()
                .build()

            val status = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bodyText = response.body?.string().orEmpty()
                    runCatching { json.decodeFromString<IgStatusResponse>(bodyText) }.getOrNull()
                }
            }

            when (status?.status_code) {
                "FINISHED" -> return PollResult.Finished
                "ERROR" -> return PollResult.Error
                else -> Unit // IN_PROGRESS / PUBLISHED (todavía no) -- seguir esperando
            }
        }
        return PollResult.Timeout
    }

    private fun publish(igUserId: String, containerId: String, token: String): String? {
        val form = FormBody.Builder()
            .add("creation_id", containerId)
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("https://graph.facebook.com/v22.0/$igUserId/media_publish")
            .post(form)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            runCatching { json.decodeFromString<IgPublishResponse>(bodyText) }.getOrNull()?.id
        }
    }

    private fun fetchPermalink(mediaId: String, token: String): String? {
        val request = Request.Builder()
            .url("https://graph.facebook.com/v22.0/$mediaId?fields=permalink&access_token=$token")
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyText = response.body?.string().orEmpty()
            runCatching { json.decodeFromString<IgPermalinkResponse>(bodyText) }.getOrNull()?.permalink
        }
    }

    private sealed interface PollResult {
        data object Finished : PollResult
        data object Error : PollResult
        data object Timeout : PollResult
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_POLL_ATTEMPTS = 60 // ~5 minutos
    }
}

@Serializable
private data class IgContainerResponse(val id: String, val uri: String)

@Serializable
private data class IgStatusResponse(val status_code: String? = null, val status: String? = null)

@Serializable
private data class IgPublishResponse(val id: String)

@Serializable
private data class IgPermalinkResponse(val permalink: String? = null)
