package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.dto.TiktokTokenResponse
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiktokUploaderCompletionTest {
    private fun auth() = Proxy.newProxyInstance(
        PlatformAuthApi::class.java.classLoader,
        arrayOf(PlatformAuthApi::class.java),
    ) { _, method, _ ->
        check(method.name == "tiktokToken")
        TiktokTokenResponse("fixture-token", "fixture-open-id")
    } as PlatformAuthApi

    private fun response(request: okhttp3.Request, code: Int, body: String = "") =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .body(body.toResponseBody())
            .build()

    private fun withFile(block: suspend (File) -> Unit) = runBlocking {
        val file = File.createTempFile("tiktok-final-", ".mp4")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            block(file)
        } finally {
            file.delete()
        }
    }

    private fun uploader(client: OkHttpClient, pollDelay: suspend (Long) -> Unit = {}) =
        TiktokUploader(auth(), client, pollDelay)

    @Test
    fun `processing then complete returns the public 64 bit id`() = withFile { file ->
        var polls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/video/init/") -> response(
                    request, 200,
                    """{"data":{"publish_id":"operation-1","upload_url":"https://upload.example/put"}}""",
                )
                request.method == "PUT" -> response(request, 206)
                request.url.encodedPath.endsWith("/status/fetch/") -> {
                    polls++
                    if (polls == 1) response(request, 200, """{"data":{"status":"PROCESSING_UPLOAD"}}""")
                    else response(request, 200, """{"data":{"status":"PUBLISH_COMPLETE","publicaly_available_post_id":[9223372036854775000]}}""")
                }
                else -> error("Unexpected TikTok request: ${request.method} ${request.url}")
            }
        }.build()

        val result = uploader(client).upload(file, UploadMetadata(title = "fixture")) {}

        assertEquals(2, polls)
        assertTrue(result is UploadResult.Success)
        assertEquals("9223372036854775000", (result as UploadResult.Success).platformId)
    }

    @Test
    fun `failed processing preserves provider reason and is not retryable`() = withFile { file ->
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/video/init/") -> response(request, 200, """{"data":{"publish_id":"operation-2","upload_url":"https://upload.example/put"}}""")
                request.method == "PUT" -> response(request, 206)
                else -> response(request, 200, """{"data":{"status":"FAILED","fail_reason":"unsupported_codec"}}""")
            }
        }.build()

        val result = uploader(client).upload(file, UploadMetadata(title = "fixture")) {}

        assertTrue(result is UploadResult.Failure)
        assertFalse((result as UploadResult.Failure).retryable)
        assertTrue(result.message.contains("unsupported_codec"))
    }

    @Test
    fun `status timeout after accepted bytes stays ambiguous and is not retryable`() = withFile { file ->
        var polls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/video/init/") -> response(request, 200, """{"data":{"publish_id":"operation-3","upload_url":"https://upload.example/put"}}""")
                request.method == "PUT" -> response(request, 206)
                else -> { polls++; response(request, 503) }
            }
        }.build()

        val result = uploader(client).upload(file, UploadMetadata(title = "fixture")) {}

        assertEquals(60, polls)
        assertTrue(result is UploadResult.Failure)
        assertFalse((result as UploadResult.Failure).retryable)
        assertTrue(result.message.contains("Revisá en la app de TikTok"))
    }

    @Test
    fun `lost status response after accepted bytes is ambiguous and not retryable`() = withFile { file ->
        var acceptedPut = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/video/init/") -> response(request, 200, """{"data":{"publish_id":"operation-5","upload_url":"https://upload.example/put"}}""")
                request.method == "PUT" -> { acceptedPut = true; response(request, 206) }
                request.url.encodedPath.endsWith("/status/fetch/") -> throw java.io.IOException("lost poll response")
                else -> error("Unexpected TikTok request: ${request.method} ${request.url}")
            }
        }.build()

        val result = uploader(client).upload(file, UploadMetadata(title = "fixture")) {}

        assertTrue(acceptedPut)
        assertTrue(result is UploadResult.Failure)
        assertFalse((result as UploadResult.Failure).retryable)
        assertTrue(result.message.contains("no confirmó el resultado"))
    }

    @Test
    fun `cancellation during final polling propagates after bytes are accepted`() = withFile { file ->
        var acceptedPut = false
        var statusRequests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/video/init/") -> response(request, 200, """{"data":{"publish_id":"operation-4","upload_url":"https://upload.example/put"}}""")
                request.method == "PUT" -> { acceptedPut = true; response(request, 206) }
                else -> { statusRequests++; response(request, 200, """{"data":{"status":"PROCESSING_UPLOAD"}}""") }
            }
        }.build()

        try {
            uploader(client) { throw CancellationException("fixture cancelled") }
                .upload(file, UploadMetadata(title = "fixture")) {}
            throw AssertionError("CancellationException should propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("fixture cancelled", cancelled.message)
        }

        assertTrue(acceptedPut)
        assertEquals(0, statusRequests)
    }

    @Test
    fun `init rejection remains retryable before provider accepts bytes`() = withFile { file ->
        var putRequests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.encodedPath.endsWith("/video/init/")) response(request, 503)
            else { putRequests++; response(request, 500) }
        }.build()

        val result = uploader(client).upload(file, UploadMetadata(title = "fixture")) {}

        assertEquals(0, putRequests)
        assertTrue(result is UploadResult.Failure)
        assertTrue((result as UploadResult.Failure).retryable)
    }
}
