package com.esseanalytics.android.feature.upload

import android.content.Context
import com.esseanalytics.android.core.media.TrimProcessor
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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
    @ApplicationContext private val context: Context,
    private val platformAuthApi: PlatformAuthApi,
    private val trimProcessor: TrimProcessor,
    @field:PlatformOkHttp private val httpClient: OkHttpClient,
) : PlatformUploader {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        var tempTrimmed: File? = null
        return try {
            val tokenInfo = platformAuthApi.instagramToken()
            val token = tokenInfo.access_token
            val igUserId = tokenInfo.instagram_user_id

            // Meta rechaza (ProcessingFailedError genérico, sin decir la causa
            // real) algunos videos sin explicar por qué -- confirmado en
            // desktop que la causa más común es la DURACIÓN: cuentas sin el
            // rollout de Reels extendido quedan topeadas a 60s vía la API, sin
            // importar el encoding. En vez de adivinar de antemano, se intenta
            // el original y, si falla, se recorta a 60s (sin recodificar,
            // AndroidTrimProcessor) y se reintenta UNA vez -- mismo criterio
            // que desktop, pero 2 etapas y no 3 (sin Normalize: hubiera hecho
            // falta libx264, que es GPL, y esta es una app comercial cerrada).
            var container = attemptContainer(igUserId, token, metadata, file, onProgress)
            var uploadedFile = file

            if (container == null) {
                val trimmed = File(context.cacheDir, "ig_trim_${System.currentTimeMillis()}.mp4")
                if (trimProcessor.trim(file, trimmed).isSuccess) {
                    tempTrimmed = trimmed
                    uploadedFile = trimmed
                    container = attemptContainer(igUserId, token, metadata, trimmed, onProgress)
                }
            }

            if (container == null) {
                return UploadResult.Failure(
                    "Instagram rechazó la subida (se probó el original y un recorte a 60s).",
                    retryable = false,
                )
            }

            val mediaId = withContext(Dispatchers.IO) {
                publish(igUserId, container.id, token)
            } ?: return UploadResult.Failure("Instagram no confirmó la publicación.", retryable = true)

            val permalink = withContext(Dispatchers.IO) {
                fetchPermalink(mediaId, token) ?: ""
            }

            // Crosspost NO-FATAL: si falla, Instagram ya está publicado --
            // mismo criterio que desktop (instagram-upload.controller.ts).
            // Usa uploadedFile (el que IG de verdad aceptó, original o
            // recortado), no siempre "file" -- igual que "usedPath" en desktop.
            val facebookResult = if (metadata.crossPostFacebook) {
                val pageId = tokenInfo.page_id
                if (pageId == null) {
                    FacebookCrossPostResult.Failed(
                        "La conexión no tiene una Página de Facebook asociada -- reconectá Instagram desde Ajustes.",
                    )
                } else {
                    publishReelToFacebookPage(uploadedFile, pageId, token, buildCaption(metadata))
                }
            } else {
                null
            }

            UploadResult.Success(platformId = mediaId, platformUrl = permalink, facebookCrossPost = facebookResult)
        } catch (e: IOException) {
            UploadResult.Failure(e.message ?: "Error de red al subir a Instagram.", retryable = true)
        } finally {
            tempTrimmed?.delete()
        }
    }

    // Crear contenedor -> subir bytes -> poll hasta FINISHED, como una sola
    // unidad que devuelve null si cualquier paso lo rechaza -- así upload()
    // puede reintentar con un archivo distinto (el recorte) sin duplicar la
    // secuencia de 3 llamadas. Un IOException de red NO se atrapa acá a
    // propósito: debe llegar sin filtrar hasta el catch de upload(), que
    // marca retryable=true (WorkManager reintenta solo) -- muy distinto de
    // un rechazo de contenido, donde reintentar el mismo archivo no cambia nada.
    private suspend fun attemptContainer(
        igUserId: String,
        token: String,
        metadata: UploadMetadata,
        file: File,
        onProgress: (Float) -> Unit,
    ): IgContainerResponse? {
        val container = withContext(Dispatchers.IO) {
            createContainer(igUserId, token, metadata)
        } ?: return null

        val uploaded = withContext(Dispatchers.IO) {
            uploadFileToContainer(container.uri, token, file, onProgress)
        }
        if (!uploaded) return null

        val pollResult = pollUntilFinished(container.id, token)
        return if (pollResult is PollResult.Finished) container else null
    }

    private fun buildCaption(metadata: UploadMetadata): String =
        metadata.title + if (metadata.description.isNotBlank()) "\n\n${metadata.description}" else ""

    private fun createContainer(igUserId: String, token: String, metadata: UploadMetadata): IgContainerResponse? {
        val formBuilder = FormBody.Builder()
            .add("media_type", "REELS")
            .add("upload_type", "resumable")
            .add("caption", buildCaption(metadata))
            .add("share_to_feed", "true")
            .add("access_token", token)

        // thumb_offset ya está en ms -- mismo campo que usa desktop
        // (instagram-upload.controller.ts), Meta saca el frame server-side.
        metadata.thumbnailOffsetMs?.let { formBuilder.add("thumb_offset", it.toString()) }

        val form = formBuilder.build()

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
            delay(POLL_INTERVAL_MS.milliseconds)
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

            when (status?.statusCode) {
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

    // Publica el Reel en la Página de Facebook vía la Reels Publishing API
    // (/{page_id}/video_reels: start -> subir bytes -> finish -> poll status).
    // MISMO archivo que Instagram ya aceptó -- evita el "no se puede reusar el
    // audio" que tira "Compartir a Facebook" manual desde la app de IG en
    // reels subidos por API (el audio queda registrado como original de ESE
    // reel). Puerto directo de publishReelToFacebookPage en
    // instagram-upload.controller.ts, confirmado funcionando ahí contra Meta.
    private suspend fun publishReelToFacebookPage(
        file: File,
        pageId: String,
        pageToken: String,
        description: String,
    ): FacebookCrossPostResult = withContext(Dispatchers.IO) {
        try {
            val start = startFacebookReel(pageId, pageToken)
            val videoId = start?.videoId
            val uploadUrl = start?.uploadUrl
            if (videoId == null || (uploadUrl == null)) {
                return@withContext FacebookCrossPostResult.Failed(
                    start?.error?.message ?: "No se pudo iniciar la subida a Facebook.",
                )
            }

            if (!streamFileToFacebook(uploadUrl, pageToken, file)) {
                return@withContext FacebookCrossPostResult.Failed("Facebook rechazó la subida del archivo.")
            }

            val finish = finishFacebookReel(pageId, pageToken, videoId, description)
            if (finish?.error != null) {
                return@withContext FacebookCrossPostResult.Failed(
                    finish.error.message ?: "Error al publicar el Reel en Facebook.",
                )
            }

            // Espera acotada -- si no llega a tiempo igual es éxito, el video
            // ya quedó encolado del lado de Meta (mismo criterio que desktop).
            repeat(FB_STATUS_POLL_ATTEMPTS) {
                delay(FB_STATUS_POLL_INTERVAL_MS.milliseconds)
                val status = fetchFacebookStatus(videoId, pageToken)?.status
                if (status?.videoStatus == "error") {
                    return@withContext FacebookCrossPostResult.Failed("Facebook rechazó el video.")
                }
                if (status?.publishingPhase?.status == "complete" || status?.videoStatus == "ready") {
                    return@withContext FacebookCrossPostResult.Published(videoId, "https://www.facebook.com/reel/$videoId")
                }
            }
            FacebookCrossPostResult.Published(videoId, "https://www.facebook.com/reel/$videoId")
        } catch (e: Exception) {
            FacebookCrossPostResult.Failed(e.message ?: "Error al publicar en Facebook.")
        }
    }

    private fun startFacebookReel(pageId: String, pageToken: String): FbReelsStartResponse? {
        val form = FormBody.Builder()
            .add("upload_phase", "start")
            .add("access_token", pageToken)
            .build()
        val request = Request.Builder()
            .url("https://graph.facebook.com/v22.0/$pageId/video_reels")
            .post(form)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            runCatching { json.decodeFromString<FbReelsStartResponse>(bodyText) }.getOrNull()
        }
    }

    // MISMO protocolo que la subida de bytes del contenedor de Instagram
    // (uploadFileToContainer), pero con los headers offset/file_size que usa
    // desktop para este endpoint puntual (streamFileToMeta) -- confirmado
    // funcionando contra rupload.facebook.com para video_reels.
    private fun streamFileToFacebook(uploadUrl: String, token: String, file: File): Boolean {
        val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", "OAuth $token")
            .addHeader("offset", "0")
            .addHeader("file_size", file.length().toString())
            .post(requestBody)
            .build()
        return httpClient.newCall(request).execute().use { it.isSuccessful }
    }

    private fun finishFacebookReel(pageId: String, pageToken: String, videoId: String, description: String): FbReelsFinishResponse? {
        val form = FormBody.Builder()
            .add("upload_phase", "finish")
            .add("video_id", videoId)
            .add("video_state", "PUBLISHED")
            .add("description", description)
            .add("access_token", pageToken)
            .build()
        val request = Request.Builder()
            .url("https://graph.facebook.com/v22.0/$pageId/video_reels")
            .post(form)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            runCatching { json.decodeFromString<FbReelsFinishResponse>(bodyText) }.getOrNull()
        }
    }

    private fun fetchFacebookStatus(videoId: String, pageToken: String): FbStatusResponse? {
        val request = Request.Builder()
            .url("https://graph.facebook.com/v22.0/$videoId?fields=status&access_token=$pageToken")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyText = response.body?.string().orEmpty()
            runCatching { json.decodeFromString<FbStatusResponse>(bodyText) }.getOrNull()
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
        const val FB_STATUS_POLL_INTERVAL_MS = 5_000L
        const val FB_STATUS_POLL_ATTEMPTS = 12 // ~1 minuto, igual que desktop
    }
}

@Serializable
private data class IgContainerResponse(val id: String, val uri: String)

@Serializable
private data class IgStatusResponse(
    @SerialName("status_code") val statusCode: String? = null,
    val status: String? = null,
)

@Serializable
private data class FbReelsStartResponse(
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("upload_url") val uploadUrl: String? = null,
    val error: FbError? = null,
)

@Serializable
private data class FbReelsFinishResponse(val error: FbError? = null)

@Serializable
private data class FbError(val message: String? = null)

@Serializable
private data class FbStatusResponse(val status: FbStatusDetail? = null)

@Serializable
private data class FbStatusDetail(
    @SerialName("video_status") val videoStatus: String? = null,
    @SerialName("publishing_phase") val publishingPhase: FbPublishingPhase? = null,
)

@Serializable
private data class FbPublishingPhase(val status: String? = null)

@Serializable
private data class IgPublishResponse(val id: String)

@Serializable
private data class IgPermalinkResponse(val permalink: String? = null)
