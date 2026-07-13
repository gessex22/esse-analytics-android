package com.esseanalytics.android.feature.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File

// RequestBody que streamea el archivo desde disco -- NO lo carga entero a
// memoria, importante para videos de varios cientos de MB -- reportando
// progreso 0f..1f a medida que se van escribiendo bytes al socket.
class ProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        val buffer = Buffer()
        file.source().use { source ->
            var read: Long
            while (source.read(buffer, CHUNK_SIZE_BYTES).also { read = it } != -1L) {
                sink.write(buffer, read)
                written += read
                if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE_BYTES = 8192L
    }
}
