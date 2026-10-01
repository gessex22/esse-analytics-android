package com.esseanalytics.android.feature.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileInputStream

// RequestBody que streamea el archivo desde disco -- NO lo carga entero a
// memoria, importante para videos de varios cientos de MB -- reportando
// progreso 0f..1f a medida que se van escribiendo bytes al socket.
//
// offsetBytes (default 0) permite reanudar una subida resumible: el stream se
// posiciona en el offset SIN leer los bytes previos (FileChannel.position,
// nada en memoria), contentLength es file.length - offset y el progreso
// reportado es GLOBAL -- (offset + written) / file.length --, así el callback
// continúa donde quedó la subida anterior en vez de reiniciar en 0.
class ProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit,
    private val offsetBytes: Long = 0L,
) : RequestBody() {
    init {
        require(offsetBytes in 0..file.length()) {
            "offsetBytes ($offsetBytes) fuera de 0..${file.length()} para ${file.name}."
        }
    }

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = file.length() - offsetBytes

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var written = 0L
        val buffer = Buffer()
        FileInputStream(file).use { input ->
            input.channel.position(offsetBytes)
            input.source().use { source ->
                var read: Long
                while (source.read(buffer, CHUNK_SIZE_BYTES).also { read = it } != -1L) {
                    sink.write(buffer, read)
                    written += read
                    if (total > 0) onProgress(((offsetBytes + written).toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE_BYTES = 8192L
    }
}
