package com.esseanalytics.android.core.network.tus

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.RandomAccessFile
import java.io.File

sealed class TUSException(message: String) : Exception(message) {
    class CreateFailed(status: Int) : TUSException("No se pudo iniciar la subida ($status).")
    object MissingLocation : TUSException("El servidor no devolvió la URL de subida.")
    class ChunkFailed(status: Int) : TUSException("Falló el envío de un fragmento del video (código $status).")
    class ChunkNetworkError(detail: String) : TUSException("Falló el envío de un fragmento del video: $detail")
    object IncompleteUpload : TUSException("La subida no terminó correctamente.")
}

// Cliente TUS 1.0.0 manual (sin dependencia externa) -- mismo protocolo que
// TUSUploadClient.swift (iOS) y tus-js-client del frontend de escritorio. La
// central migró Biblioteca remota de un POST multipart single-shot a TUS
// resumable porque un corte de conexión a mitad de un video pesado obligaba a
// reintentar desde cero -- acá se sube en fragmentos de CHUNK_SIZE (no un solo
// PATCH gigante) para que un hiccup de red solo repita el fragmento actual.
// El OkHttpClient inyectado debe ser el default de NetworkModule (con
// AuthInterceptor) -- así el Bearer token se agrega solo, sin manejarlo acá.
object TUSUploadClient {
    private const val CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB, igual que iOS
    private const val MAX_RETRIES_PER_CHUNK = 3
    private val offsetOctetStream = "application/offset+octet-stream".toMediaType()

    suspend fun upload(
        file: File,
        endpoint: String,
        okHttpClient: OkHttpClient,
        metadata: Map<String, String>,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val totalSize = file.length()

        val createRequest = Request.Builder()
            .url(endpoint)
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .header("Tus-Resumable", "1.0.0")
            .header("Upload-Length", totalSize.toString())
            .header("Upload-Metadata", encodeMetadata(metadata))
            .build()

        val uploadUrl = okHttpClient.newCall(createRequest).execute().use { response ->
            if (response.code != 201) throw TUSException.CreateFailed(response.code)
            response.header("Location") ?: throw TUSException.MissingLocation
        }

        RandomAccessFile(file, "r").use { raf ->
            var offset = 0L
            while (offset < totalSize) {
                val chunkLength = minOf(CHUNK_SIZE.toLong(), totalSize - offset).toInt()
                val buffer = ByteArray(chunkLength)
                raf.seek(offset)
                raf.readFully(buffer)

                var succeeded = false
                var finishedBody: String? = null
                var lastError: Exception = TUSException.IncompleteUpload

                for (attempt in 1..MAX_RETRIES_PER_CHUNK) {
                    try {
                        val patchRequest = Request.Builder()
                            .url(uploadUrl)
                            .patch(buffer.toRequestBody(offsetOctetStream))
                            .header("Tus-Resumable", "1.0.0")
                            .header("Upload-Offset", offset.toString())
                            .build()
                        okHttpClient.newCall(patchRequest).execute().use { response ->
                            when (response.code) {
                                // onUploadFinish (último fragmento, offset llega a size) responde
                                // 200 con el documento creado en el body en vez del 204 default.
                                200 -> { finishedBody = response.body?.string(); succeeded = true }
                                204 -> succeeded = true
                                else -> lastError = TUSException.ChunkFailed(response.code)
                            }
                        }
                    } catch (e: IOException) {
                        lastError = TUSException.ChunkNetworkError(e.message ?: "error de red")
                    }
                    if (succeeded) break
                    delay(700)
                }
                if (!succeeded) throw lastError

                finishedBody?.let {
                    onProgress(totalSize, totalSize)
                    return@withContext it
                }

                offset += chunkLength
                onProgress(offset, totalSize)
            }
        }

        throw TUSException.IncompleteUpload
    }

    private fun encodeMetadata(metadata: Map<String, String>): String =
        metadata.entries.joinToString(",") { (key, value) ->
            "$key ${Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
        }
}
