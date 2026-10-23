package com.esseanalytics.android.core.network.tus

import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TUSUploadClientTest {
    @Test
    fun `cancellation during retry backoff propagates without sending another patch`() {
        val file = File.createTempFile("tus-cancel-", ".mp4")
        try {
            file.writeBytes(byteArrayOf(1, 2))
            var patches = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") response(request, 201, location = "https://uploads.example/cancel")
                else { patches++; response(request, 503) }
            }.build()
            val failure = runCatching {
                kotlinx.coroutines.runBlocking {
                    TUSUploadClient.upload(file, "https://central.example/tus", client, emptyMap(),
                        retryDelay = { throw kotlinx.coroutines.CancellationException("cancelled") })
                }
            }.exceptionOrNull()
            assertTrue(failure is kotlinx.coroutines.CancellationException)
            assertEquals(1, patches)
        } finally { file.delete() }
    }

    @Test
    fun `last 204 without final document remains incomplete`() {
        val file = File.createTempFile("tus-no-document-", ".mp4")
        try {
            file.writeBytes(byteArrayOf(1, 2))
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") response(request, 201, location = "https://uploads.example/incomplete")
                else response(request, 204)
            }.build()
            val failure = runCatching {
                kotlinx.coroutines.runBlocking {
                    TUSUploadClient.upload(file, "https://central.example/tus", client, emptyMap(), retryDelay = {})
                }
            }.exceptionOrNull()
            assertTrue(failure is TUSException.IncompleteUpload)
        } finally { file.delete() }
    }

    private fun response(request: okhttp3.Request, code: Int, body: String = "", location: String? = null) =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .body(body.toResponseBody())
            .apply { if (location != null) header("Location", location) }
            .build()

    @Test
    fun `created session sends chunks at consecutive offsets and accepts final body`() {
        val file = File.createTempFile("tus-chunks-", ".mp4")
        try {
            val size = 5 * 1024 * 1024 + 17
            file.outputStream().use { out ->
                val block = ByteArray(64 * 1024) { 0x5A }
                var remaining = size
                while (remaining > 0) {
                    val count = minOf(block.size, remaining)
                    out.write(block, 0, count)
                    remaining -= count
                }
            }
            val offsets = mutableListOf<String?>()
            val lengths = mutableListOf<Long>()
            val progress = mutableListOf<Pair<Long, Long>>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                when (request.method) {
                    "POST" -> {
                        assertEquals("1.0.0", request.header("Tus-Resumable"))
                        assertEquals(size.toString(), request.header("Upload-Length"))
                        assertEquals("filename Y2xpcC5tcDQ=", request.header("Upload-Metadata"))
                        response(request, 201, location = "https://uploads.example/session/1")
                    }
                    "PATCH" -> {
                        offsets += request.header("Upload-Offset")
                        lengths += request.body!!.contentLength()
                        if (offsets.size == 1) response(request, 204)
                        else response(request, 200, "{\"id\":\"remote-1\"}")
                    }
                    else -> error("Unexpected TUS request ${request.method}")
                }
            }.build()

            val result = kotlinx.coroutines.runBlocking {
                TUSUploadClient.upload(
                    file, "https://central.example/tus", client, mapOf("filename" to "clip.mp4"),
                    onProgress = { sent, total -> progress += sent to total },
                    retryDelay = {},
                )
            }

            assertEquals("{\"id\":\"remote-1\"}", result)
            assertEquals(listOf("0", (5 * 1024 * 1024).toString()), offsets)
            assertEquals(listOf(5 * 1024 * 1024L, 17L), lengths)
            assertEquals(listOf(5 * 1024 * 1024L to size.toLong(), size.toLong() to size.toLong()), progress)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `lost patch response followed by offset conflict fails without false completion`() {
        val file = File.createTempFile("tus-lost-ack-", ".mp4")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            var patches = 0
            val offsets = mutableListOf<String?>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") {
                    response(request, 201, location = "https://uploads.example/session/2")
                } else {
                    patches++
                    offsets += request.header("Upload-Offset")
                    if (patches == 1) throw IOException("ack was lost after server accepted bytes")
                    response(request, 409)
                }
            }.build()

            val failure = try {
                kotlinx.coroutines.runBlocking {
                    TUSUploadClient.upload(file, "https://central.example/tus", client, emptyMap(), retryDelay = {})
                }
                null
            } catch (error: TUSException) {
                error
            }

            assertEquals(3, patches)
            assertEquals(listOf("0", "0", "0"), offsets)
            assertTrue(failure is TUSException.ChunkFailed)
            assertTrue(failure is TUSException.ChunkFailed && failure.message!!.contains("409"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `creation rejection preserves server detail and never patches`() {
        val file = File.createTempFile("tus-quota-", ".mp4")
        try {
            file.writeBytes(byteArrayOf(1))
            var patches = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") response(request, 403, "quota reached")
                else { patches++; response(request, 204) }
            }.build()

            val failure = try {
                kotlinx.coroutines.runBlocking { TUSUploadClient.upload(file, "https://central.example/tus", client, emptyMap(), retryDelay = {}) }
                null
            } catch (error: TUSException) {
                error
            }

            assertTrue(failure is TUSException.CreateFailed)
            assertEquals("quota reached", failure?.message)
            assertEquals(0, patches)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `premature 200 before final chunk is retried but never reported as completion`() {
        val file = File.createTempFile("tus-premature-200-", ".mp4")
        try {
            file.outputStream().use { out ->
                val block = ByteArray(64 * 1024) { 0x2A }
                var remaining = 5 * 1024 * 1024 + 1
                while (remaining > 0) {
                    val count = minOf(block.size, remaining)
                    out.write(block, 0, count)
                    remaining -= count
                }
            }
            val offsets = mutableListOf<String?>()
            val progress = mutableListOf<Pair<Long, Long>>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") {
                    response(request, 201, location = "https://uploads.example/session/premature")
                } else {
                    offsets += request.header("Upload-Offset")
                    response(request, 200, "{\"id\":\"must-not-complete\"}")
                }
            }.build()

            val failure = try {
                kotlinx.coroutines.runBlocking {
                    TUSUploadClient.upload(
                        file,
                        "https://central.example/tus",
                        client,
                        emptyMap(),
                        onProgress = { sent, total -> progress += sent to total },
                        retryDelay = {},
                    )
                }
                null
            } catch (error: TUSException) {
                error
            }

            assertTrue(failure is TUSException.IncompleteUpload)
            assertEquals(listOf("0", "0", "0"), offsets)
            assertTrue(progress.isEmpty())
        } finally {
            file.delete()
        }
    }
}
