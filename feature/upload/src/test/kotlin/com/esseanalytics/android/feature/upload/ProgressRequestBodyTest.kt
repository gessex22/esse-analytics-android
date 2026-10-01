package com.esseanalytics.android.feature.upload

import kotlin.math.ceil
import okio.Buffer
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// ProgressRequestBody puro, sin socket: se escribe en un Buffer de okio y se
// verifica el contenido enviado (nada del prefijo cuando hay offset) y el
// progreso reportado (global: offset+written sobre file.length, no desde 0).
class ProgressRequestBodyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `offset por defecto 0 mantiene el comportimiento anterior`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(100) { it.toByte() })
        val progress = mutableListOf<Float>()

        val body = ProgressRequestBody(file, null, progress::add)

        assertEquals(100L, body.contentLength())
        val out = Buffer()
        body.writeTo(out)
        assertEquals(ByteString.of(*file.readBytes()), out.readByteString())
        // Progreso 0..1 completo, terminando en 1f.
        assertEquals(1f, progress.last(), 0.0001f)
        assertTrue(progress.all { it in 0f..1f })
    }

    @Test
    fun `con offset el contentLength excluye el prefijo`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(100) { it.toByte() })

        val body = ProgressRequestBody(file, null, {}, offsetBytes = 60L)

        assertEquals(40L, body.contentLength())
    }

    @Test
    fun `writeTo no reenvia el prefijo y reporta progreso global`() {
        val total = 100
        val offset = 60
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(total) { it.toByte() })
        val progress = mutableListOf<Float>()

        val body = ProgressRequestBody(file, null, progress::add, offsetBytes = offset.toLong())
        val out = Buffer()
        body.writeTo(out)

        // Solo los bytes desde el offset.
        val expectedSuffix = ByteArray(total - offset) { (it + offset).toByte() }
        assertEquals(ByteString.of(*expectedSuffix), out.readByteString())

        // El progreso es GLOBAL: cada callback vale (offset + written) / total.
        val chunk = 8192L
        val chunks = ceil((total - offset).toDouble() / chunk).toInt()
        assertEquals(chunks, progress.size)
        progress.forEachIndexed { index, value ->
            val expected = (offset + (index + 1) * chunk).toFloat() / total
            // El último callback llega a 1f exacto; los anteriores arrancan
            // arriba del offset/total (nunca reenvían el prefijo desde 0).
            assertEquals(expected.coerceAtMost(1f), value, 0.0001f)
        }
        assertEquals(1f, progress.last(), 0.0001f)
        assertTrue(progress.first() > offset.toFloat() / total)
    }

    @Test
    fun `offset igual al tamano del archivo es valido y no envia nada`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(10) { it.toByte() })
        val progress = mutableListOf<Float>()

        val body = ProgressRequestBody(file, null, progress::add, offsetBytes = 10L)
        val out = Buffer()
        body.writeTo(out)

        assertEquals(0L, body.contentLength())
        assertEquals(0L, out.size)
        assertTrue(progress.isEmpty())
    }

    @Test
    fun `offset invalido lanza IllegalArgumentException`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(10) { it.toByte() })

        assertThrows(IllegalArgumentException::class.java) {
            ProgressRequestBody(file, null, {}, offsetBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgressRequestBody(file, null, {}, offsetBytes = 11L)
        }
    }
}
