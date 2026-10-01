package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.database.PublishOperationPolicy
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

// Desenlace explícito de cada llamada al protocolo resumible de YouTube
// (uploadType=resumable). Seis variantes distintas -- el llamador (journal +
// worker) decide el siguiente paso SOLO por tipo, sin re-parsear mensajes:
//   - Created:  el POST de apertura devolvió sesión usable (Location validada
//               con PublishOperationPolicy.isValidHttpsUrl).
//   - Completed: YouTube devolvió el JSON del video con id no vacío (200/201).
//   - Incomplete: 308 con Range `bytes=0-N` sano; confirmedBytes = N+1.
//   - Gone: la sesión ya no existe (404/410) -- solo una transición explícita
//           (startNewAttempt) puede abrir otra.
//   - RetryableFailure: red/5xx, o 2xx/308 ambiguos (JSON inválido, Range
//               ausente/malformado/no monótono, o confirmado >= total): pudo
//               pasar algo en YouTube, así que se reintenta la MISMA sesión.
//   - PermanentFailure: 4xx de protocolo o 2xx malformado en el POST de
//               apertura -- reintentar igual no sirve.
sealed interface YoutubeResumableResult {
    data class Created(val sessionUrl: String) : YoutubeResumableResult
    data class Completed(val platformId: String) : YoutubeResumableResult
    data class Incomplete(val confirmedBytes: Long) : YoutubeResumableResult
    data class Gone(val message: String) : YoutubeResumableResult
    data class RetryableFailure(val message: String) : YoutubeResumableResult
    data class PermanentFailure(val message: String) : YoutubeResumableResult
}

// Transporte síncrono (bloqueante, envolver en Dispatchers.IO) sobre el
// protocolo resumible de YouTube Data API v3 -- mismo endpoint que ya usa
// desktop (local-backend/src/controllers/youtube-upload.controller.ts).
// Separa el protocolo puro de YoutubeUploader para poder probarlo en tests
// JVM con un Interceptor fake, sin socket ni Android.
interface YoutubeResumableTransport {

    fun createSession(token: String, metadata: UploadMetadata, totalBytes: Long): YoutubeResumableResult

    fun querySession(sessionUrl: String, totalBytes: Long): YoutubeResumableResult

    fun uploadFromOffset(
        sessionUrl: String,
        file: File,
        offsetBytes: Long,
        totalBytes: Long,
        onProgress: (Float) -> Unit = {},
    ): YoutubeResumableResult
}

class OkHttpYoutubeResumableTransport(
    private val httpClient: OkHttpClient,
) : YoutubeResumableTransport {

    private val json = Json { ignoreUnknownKeys = true }

    override fun createSession(token: String, metadata: UploadMetadata, totalBytes: Long): YoutubeResumableResult {
        val body = json.encodeToString(
            ResumableSessionPayload(
                snippet = ResumableSessionPayload.Snippet(
                    title = metadata.title,
                    description = metadata.description,
                    tags = metadata.tags,
                ),
                status = ResumableSessionPayload.Status(privacyStatus = metadata.privacyStatus),
            ),
        )

        val request = Request.Builder()
            .url(UPLOAD_ENDPOINT)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Upload-Content-Type", VIDEO_CONTENT_TYPE)
            .addHeader("X-Upload-Content-Length", totalBytes.toString())
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code >= 500 -> YoutubeResumableResult.RetryableFailure(
                        "YouTube respondió ${response.code} al abrir la sesión de subida.",
                    )
                    response.code in 200..299 -> {
                        val location = response.header("Location")
                        if (location != null && PublishOperationPolicy.isValidHttpsUrl(location)) {
                            YoutubeResumableResult.Created(location)
                        } else {
                            YoutubeResumableResult.PermanentFailure(
                                "YouTube abrió la sesión (HTTP ${response.code}) pero sin una Location de sesión válida.",
                            )
                        }
                    }
                    else -> YoutubeResumableResult.PermanentFailure(
                        "YouTube rechazó la apertura de sesión con HTTP ${response.code}.",
                    )
                }
            }
        } catch (e: IOException) {
            YoutubeResumableResult.RetryableFailure(e.message ?: "Error de red al abrir la sesión de YouTube.")
        }
    }

    override fun querySession(sessionUrl: String, totalBytes: Long): YoutubeResumableResult {
        val request = Request.Builder()
            .url(sessionUrl)
            .addHeader("Content-Range", queryContentRange(totalBytes))
            .put(ByteArray(0).toRequestBody())
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                classifySessionResponse(response, totalBytes)
            }
        } catch (e: IOException) {
            YoutubeResumableResult.RetryableFailure(e.message ?: "Error de red al consultar la sesión de YouTube.")
        }
    }

    override fun uploadFromOffset(
        sessionUrl: String,
        file: File,
        offsetBytes: Long,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ): YoutubeResumableResult {
        require(totalBytes == file.length()) {
            "totalBytes ($totalBytes) no coincide con el tamaño real del archivo (${file.length()})."
        }
        require(offsetBytes in 0 until totalBytes) {
            "offsetBytes ($offsetBytes) fuera de 0 until $totalBytes."
        }

        val requestBody = ProgressRequestBody(
            file,
            VIDEO_CONTENT_TYPE.toMediaType(),
            onProgress,
            offsetBytes = offsetBytes,
        )
        val request = Request.Builder()
            .url(sessionUrl)
            .addHeader("Content-Range", "bytes $offsetBytes-${totalBytes - 1}/$totalBytes")
            .put(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                classifySessionResponse(response, totalBytes)
            }
        } catch (e: IOException) {
            YoutubeResumableResult.RetryableFailure(e.message ?: "Error de red al subir a YouTube.")
        }
    }

    // Clasificación compartida por querySession y uploadFromOffset: los dos
    // son PUT contra la URL de sesión y YouTube responde igual (200/201 con el
    // JSON del video, 308 con Range, 404/410 sesión muerta).
    private fun classifySessionResponse(response: Response, totalBytes: Long): YoutubeResumableResult {
        return when (response.code) {
            200, 201 -> parseCompleted(response) ?: YoutubeResumableResult.RetryableFailure(
                "YouTube respondió ${response.code} pero sin un JSON de video válido e id no vacío -- desenlace ambiguo.",
            )
            308 -> parseConfirmedRange(response.header("Range"), totalBytes)?.let {
                YoutubeResumableResult.Incomplete(it)
            } ?: YoutubeResumableResult.RetryableFailure(
                "YouTube respondió 308 con un Range ausente, malformado o no monótono -- desenlace ambiguo.",
            )
            404, 410 -> YoutubeResumableResult.Gone(
                "La sesión de subida de YouTube ya no existe (HTTP ${response.code}).",
            )
            else -> when {
                response.code >= 500 -> YoutubeResumableResult.RetryableFailure(
                    "YouTube respondió ${response.code} en la sesión de subida.",
                )
                else -> YoutubeResumableResult.PermanentFailure(
                    "YouTube respondió HTTP ${response.code} en la sesión de subida.",
                )
            }
        }
    }

    // 200/201 solo es Completed con JSON válido e id no vacío. El JSON se
    // parsea de una vez a la DTO -- un cuerpo inválido NO se convierte en
    // "mapa vacío" que pareciera un video sin id, vuelve ambiguo arriba.
    private fun parseCompleted(response: Response): YoutubeResumableResult.Completed? {
        val bodyText = response.body?.string() ?: return null
        val id = runCatching { json.decodeFromString<YoutubeVideoPayload>(bodyText) }.getOrNull()?.id
        return if (id.isNullOrEmpty()) null else YoutubeResumableResult.Completed(id)
    }

    // Range del 308: exactamente `bytes=0-N` (empieza en 0, monotono, sin
    // overflow). confirmed = N+1 y tiene que quedar estrictamente por debajo
    // del total -- si ya cubre el archivo el 308 contradice el estado real y
    // el desenlace es ambiguo.
    private fun parseConfirmedRange(rangeHeader: String?, totalBytes: Long): Long? {
        if (rangeHeader.isNullOrBlank()) return null
        return runCatching {
            val match = RANGE_PATTERN.matchEntire(rangeHeader.trim()) ?: return null
            val start = match.groupValues[1].toLong()
            val end = match.groupValues[2].toLong()
            if (start != 0L || end < start) return null
            val confirmed = end + 1
            if (confirmed <= 0L || confirmed >= totalBytes) return null
            confirmed
        }.getOrNull()
    }

    private companion object {
        const val UPLOAD_ENDPOINT =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
        const val VIDEO_CONTENT_TYPE = "video/*"
        val RANGE_PATTERN = Regex("""bytes=(\d+)-(\d+)""")

        fun queryContentRange(totalBytes: Long) = "bytes */$totalBytes"
    }
}

@Serializable
private data class ResumableSessionPayload(
    val snippet: Snippet,
    val status: Status,
) {
    @Serializable
    data class Snippet(val title: String, val description: String, val tags: List<String>)

    @Serializable
    data class Status(val privacyStatus: String)
}

@Serializable
private data class YoutubeVideoPayload(val id: String)
