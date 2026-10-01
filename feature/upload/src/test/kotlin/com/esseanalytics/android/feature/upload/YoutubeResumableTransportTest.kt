package com.esseanalytics.android.feature.upload

import java.io.IOException
import java.io.InterruptedIOException
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Protocolo resumible de YouTube sin socket: un Interceptor fake responde lo
// que la prueba encola y graba cada request (método, URL, headers, body),
// así se verifica tanto la clasificación de desenlaces (200/201, 308, 404/410,
// 5xx, red) como la forma exacta de cada llamada.
class YoutubeResumableTransportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeInterceptor = FakeTransportInterceptor()
    private val transport = OkHttpYoutubeResumableTransport(
        OkHttpClient.Builder().addInterceptor(fakeInterceptor).build(),
    )

    private val metadata = UploadMetadata(
        title = "Mi video de prueba",
        description = "una descripcion",
        tags = listOf("tag1", "tag2"),
        privacyStatus = "unlisted",
    )

    @Test
    fun `createSession envia POST al endpoint resumible con headers correctos y 2xx con Location valida devuelve Created`() {
        fakeInterceptor.enqueue(200, headers = mapOf("Location" to VALID_SESSION_URL))

        val result = transport.createSession(token = "tok-123", metadata = metadata, totalBytes = 1234L)

        assertEquals(YoutubeResumableResult.Created(VALID_SESSION_URL), result)
        val request = fakeInterceptor.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status",
            request.url,
        )
        assertEquals("Bearer tok-123", request.headers["Authorization"])
        assertEquals("video/*", request.headers["X-Upload-Content-Type"])
        assertEquals("1234", request.headers["X-Upload-Content-Length"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("Mi video de prueba"))
        assertTrue(body.contains("\"privacyStatus\":\"unlisted\""))
        assertTrue(body.contains("tag1"))
    }

    @Test
    fun `createSession con Location invalida o ausente en 2xx devuelve PermanentFailure`() {
        fakeInterceptor.enqueue(200, headers = mapOf("Location" to "http://upload.example.com/sin-https"))
        assertTrue(
            transport.createSession("tok", metadata, 10L) is YoutubeResumableResult.PermanentFailure,
        )

        fakeInterceptor.enqueue(201)
        assertTrue(
            transport.createSession("tok", metadata, 10L) is YoutubeResumableResult.PermanentFailure,
        )
    }

    @Test
    fun `createSession con 5xx o error de red devuelve RetryableFailure`() {
        fakeInterceptor.enqueue(503)
        assertTrue(
            transport.createSession("tok", metadata, 10L) is YoutubeResumableResult.RetryableFailure,
        )

        fakeInterceptor.failure = InterruptedIOException("timeout simulado")
        assertTrue(
            transport.createSession("tok", metadata, 10L) is YoutubeResumableResult.RetryableFailure,
        )
    }

    @Test
    fun `createSession con 4xx devuelve PermanentFailure`() {
        fakeInterceptor.enqueue(403)
        assertTrue(
            transport.createSession("tok", metadata, 10L) is YoutubeResumableResult.PermanentFailure,
        )
    }

    @Test
    fun `querySession usa PUT con body vacio y Content-Range bytes asterisco total`() {
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=0-99"))

        val result = transport.querySession(VALID_SESSION_URL, totalBytes = 200L)

        assertEquals(YoutubeResumableResult.Incomplete(100L), result)
        val request = fakeInterceptor.requests.single()
        assertEquals("PUT", request.method)
        assertEquals(VALID_SESSION_URL, request.url)
        assertEquals("bytes */200", request.headers["Content-Range"])
        assertEquals(0L, request.body?.size ?: 0L)
    }

    @Test
    fun `querySession con 200 o 201 y JSON valido con id devuelve Completed`() {
        fakeInterceptor.enqueue(200, body = """{"id":"video-abc"}""")
        assertEquals(
            YoutubeResumableResult.Completed("video-abc"),
            transport.querySession(VALID_SESSION_URL, 200L),
        )

        fakeInterceptor.enqueue(201, body = """{"id":"video-abc","otro":1}""")
        assertEquals(
            YoutubeResumableResult.Completed("video-abc"),
            transport.querySession(VALID_SESSION_URL, 200L),
        )
    }

    @Test
    fun `querySession con 2xx sin JSON valido o sin id es RetryableFailure ambiguo`() {
        // Cuerpo que NO es JSON: no se convierte en "mapa vacío", queda ambiguo.
        fakeInterceptor.enqueue(200, body = "esto no es json")
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        // JSON válido pero sin id: tampoco es Completed demostrable.
        fakeInterceptor.enqueue(200, body = """{"kind":"youtube#video"}""")
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        // id vacío tampoco alcanza.
        fakeInterceptor.enqueue(200, body = """{"id":""}""")
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )
    }

    @Test
    fun `querySession con 308 parsea Range bytes 0-N como Incomplete con confirmed N mas uno`() {
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=0-0"))
        assertEquals(
            YoutubeResumableResult.Incomplete(1L),
            transport.querySession(VALID_SESSION_URL, 200L),
        )
    }

    @Test
    fun `querySession con 308 con Range ausente malformado o no monotono es RetryableFailure ambiguo`() {
        // Sin header Range.
        fakeInterceptor.enqueue(308)
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        // No empieza en 0.
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=50-149"))
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        // Formato distinto a bytes=A-B.
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "items=0-99"))
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        // No monótono: el fin queda antes del inicio.
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=0-0, bytes=5-2"))
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )
    }

    @Test
    fun `querySession con 308 cuyo confirmado cubre el total es RetryableFailure ambiguo`() {
        // confirmed == total: el 308 contradice un archivo ya completo.
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=0-199"))
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        // confirmed > total: Range imposible para el archivo declarado.
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=0-499"))
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )
    }

    @Test
    fun `querySession con 404 o 410 devuelve Gone`() {
        fakeInterceptor.enqueue(404)
        val gone404 = transport.querySession(VALID_SESSION_URL, 200L)
        assertTrue(gone404 is YoutubeResumableResult.Gone)

        fakeInterceptor.enqueue(410)
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.Gone,
        )
    }

    @Test
    fun `querySession con 5xx error de red u otro status`() {
        fakeInterceptor.enqueue(500)
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        fakeInterceptor.failure = IOException("socket cerrada")
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.RetryableFailure,
        )

        fakeInterceptor.failure = null
        fakeInterceptor.enqueue(400)
        assertTrue(
            transport.querySession(VALID_SESSION_URL, 200L) is YoutubeResumableResult.PermanentFailure,
        )
    }

    @Test
    fun `uploadFromOffset valida totalBytes contra el archivo y offset en rango`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(256) { it.toByte() })

        assertThrows(IllegalArgumentException::class.java) {
            transport.uploadFromOffset(VALID_SESSION_URL, file, offsetBytes = 0L, totalBytes = 255L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            transport.uploadFromOffset(VALID_SESSION_URL, file, offsetBytes = -1L, totalBytes = 256L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            transport.uploadFromOffset(VALID_SESSION_URL, file, offsetBytes = 256L, totalBytes = 256L)
        }
        assertEquals(0, fakeInterceptor.requests.size)
    }

    @Test
    fun `uploadFromOffset envia Content-Range desde offset y el body contiene solo bytes desde offset`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(256) { it.toByte() })
        fakeInterceptor.enqueue(200, body = """{"id":"video-final"}""")

        val result = transport.uploadFromOffset(
            sessionUrl = VALID_SESSION_URL,
            file = file,
            offsetBytes = 100L,
            totalBytes = 256L,
        )

        assertEquals(YoutubeResumableResult.Completed("video-final"), result)
        val request = fakeInterceptor.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("bytes 100-255/256", request.headers["Content-Range"])
        val expectedSuffix = ByteArray(156) { (it + 100).toByte() }
        assertEquals(ByteString.of(*expectedSuffix), request.body)
    }

    @Test
    fun `uploadFromOffset con offset cero arranca desde el principio`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(128) { it.toByte() })
        fakeInterceptor.enqueue(308, headers = mapOf("Range" to "bytes=0-63"))

        val result = transport.uploadFromOffset(
            sessionUrl = VALID_SESSION_URL,
            file = file,
            offsetBytes = 0L,
            totalBytes = 128L,
        )

        // Misma clasificación que querySession: 308 sano -> Incomplete.
        assertEquals(YoutubeResumableResult.Incomplete(64L), result)
        val request = fakeInterceptor.requests.single()
        assertEquals("bytes 0-127/128", request.headers["Content-Range"])
        assertEquals(ByteString.of(*file.readBytes()), request.body)
    }

    @Test
    fun `uploadFromOffset clasifica Gone y 5xx igual que querySession`() {
        val file = tempFolder.newFile("video.mp4")
        file.writeBytes(ByteArray(64) { it.toByte() })

        fakeInterceptor.enqueue(410)
        assertTrue(
            transport.uploadFromOffset(VALID_SESSION_URL, file, 0L, 64L) is YoutubeResumableResult.Gone,
        )

        fakeInterceptor.enqueue(503)
        assertTrue(
            transport.uploadFromOffset(VALID_SESSION_URL, file, 0L, 64L) is YoutubeResumableResult.RetryableFailure,
        )
    }

    private companion object {
        const val VALID_SESSION_URL = "https://upload.youtube.com/session/xyz"
    }
}

// Interceptor que nunca toca la red: encola la respuesta que la prueba pide,
// graba cada request saliente y puede simular un IOException en cadena.
private class FakeTransportInterceptor : Interceptor {

    data class RecordedRequest(
        val method: String,
        val url: String,
        val headers: Headers,
        val body: ByteString?,
    )

    val requests = mutableListOf<RecordedRequest>()
    var failure: IOException? = null
    private var canned: CannedResponse = CannedResponse(200, "", emptyMap())

    fun enqueue(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        canned = CannedResponse(code, body, headers)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        failure?.let { throw it }
        val request = chain.request()
        requests += RecordedRequest(
            method = request.method,
            url = request.url.toString(),
            headers = request.headers,
            body = request.body?.let { body ->
                Buffer().also { body.writeTo(it) }.readByteString()
            },
        )
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(canned.code)
            .message("canned ${canned.code}")
            .headers(
                Headers.headersOf(*canned.headers.flatMap { listOf(it.key, it.value) }.toTypedArray()),
            )
            .body(canned.body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private data class CannedResponse(val code: Int, val body: String, val headers: Map<String, String>)
}
