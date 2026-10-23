package com.esseanalytics.android.feature.upload

import android.content.Context
import android.content.ContextWrapper
import com.esseanalytics.android.core.media.NormalizeProcessor
import com.esseanalytics.android.core.media.TrimProcessor
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.dto.InstagramTokenResponse
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramUploaderFlowTest {
    private fun auth(pageId: String? = null) = Proxy.newProxyInstance(
        PlatformAuthApi::class.java.classLoader,
        arrayOf(PlatformAuthApi::class.java),
    ) { _, method, _ ->
        check(method.name == "instagramToken")
        InstagramTokenResponse("fixture-token", "ig-user", pageId)
    } as PlatformAuthApi

    private fun response(request: Request, code: Int, body: String = "") =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
            .message("fixture").body(body.toResponseBody()).build()

    private fun context(cacheDir: File) = object : ContextWrapper(null) {
        override fun getCacheDir(): File = cacheDir
        override fun getApplicationContext(): Context = this
    }

    private fun uploader(
        cacheDir: File,
        client: OkHttpClient,
        pageId: String? = null,
        trim: TrimProcessor = object : TrimProcessor {
            override suspend fun trim(input: File, output: File, startSec: Double, maxDurationSec: Double) =
                Result.failure<Unit>(AssertionError("Unexpected trim"))
        },
        normalize: NormalizeProcessor = object : NormalizeProcessor {
            override suspend fun normalize(input: File, output: File) =
                Result.failure<Unit>(AssertionError("Unexpected normalize"))
        },
        pollDelay: suspend (Long) -> Unit = {},
    ) = InstagramUploader(context(cacheDir), auth(pageId), trim, normalize, client, pollDelay)

    private fun withFiles(block: suspend (File, File) -> Unit) = runBlocking {
        val root = kotlin.io.path.createTempDirectory("instagram-flow-").toFile()
        try {
            val source = File(root, "source.mp4").apply { writeBytes(byteArrayOf(0x41)) }
            block(root, source)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun instagramSuccessResponse(request: Request, path: String): Response = when {
        path.endsWith("/media") -> response(request, 200, """{"id":"container-1","uri":"https://upload.example/container-1"}""")
        request.url.host == "upload.example" -> response(request, 200)
        path.endsWith("/container-1") -> response(request, 200, """{"status_code":"FINISHED","status":"ok"}""")
        path.endsWith("/media_publish") -> response(request, 200, """{"id":"published-1"}""")
        path.endsWith("/published-1") -> response(request, 200, """{"permalink":"https://instagram.example/reel/published-1"}""")
        else -> error("Unexpected Instagram request ${request.method} ${request.url}")
    }

    @Test
    fun `container upload waits for finished then publishes once and resolves permalink`() = withFiles { root, source ->
        val steps = mutableListOf<String>()
        var polls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            when {
                path.endsWith("/media") -> { steps += "create"; instagramSuccessResponse(request, path) }
                request.url.host == "upload.example" -> {
                    steps += "upload"
                    assertEquals("OAuth fixture-token", request.header("Authorization"))
                    assertEquals("0", request.header("file_offset"))
                    instagramSuccessResponse(request, path)
                }
                path.endsWith("/container-1") -> {
                    steps += "poll"
                    polls++
                    response(request, 200, if (polls == 1) """{"status_code":"IN_PROGRESS"}""" else """{"status_code":"FINISHED"}""")
                }
                path.endsWith("/media_publish") -> { steps += "publish"; instagramSuccessResponse(request, path) }
                path.endsWith("/published-1") -> { steps += "permalink"; instagramSuccessResponse(request, path) }
                else -> error("Unexpected Instagram request ${request.method} ${request.url}")
            }
        }.build()

        val result = uploader(root, client).upload(source, UploadMetadata("Reel", "caption")) {}

        assertEquals(listOf("create", "upload", "poll", "poll", "publish", "permalink"), steps)
        assertTrue(result is UploadResult.Success)
        assertEquals("published-1", (result as UploadResult.Success).platformId)
        assertEquals("https://instagram.example/reel/published-1", result.platformUrl)
    }

    @Test
    fun `lost media publish response is ambiguous and never retryable`() = withFiles { root, source ->
        var publishCalls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            when {
                path.endsWith("/media_publish") -> { publishCalls++; throw IOException("response lost after Meta accepted request") }
                else -> instagramSuccessResponse(request, path)
            }
        }.build()

        val result = uploader(root, client).upload(source, UploadMetadata("Reel")) {}

        assertEquals(1, publishCalls)
        assertTrue(result is UploadResult.Failure)
        assertFalse((result as UploadResult.Failure).retryable)
        assertTrue(result.message.contains("Revisá tu perfil"))
    }

    @Test
    fun `permalink network failure preserves confirmed Instagram success`() = withFiles { root, source ->
        var publishCalls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/media_publish") -> {
                    publishCalls++
                    instagramSuccessResponse(request, request.url.encodedPath)
                }
                request.url.encodedPath.endsWith("/published-1") -> throw IOException("permalink lookup unavailable")
                else -> instagramSuccessResponse(request, request.url.encodedPath)
            }
        }.build()

        val result = uploader(root, client).upload(source, UploadMetadata("Reel")) {}

        assertEquals(1, publishCalls)
        assertTrue(result is UploadResult.Success)
        assertEquals("published-1", (result as UploadResult.Success).platformId)
        assertEquals("", result.platformUrl)
    }

    @Test
    fun `cancellation during permalink lookup propagates after one confirmed publish`() = withFiles { root, source ->
        var publishCalls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/media_publish") -> {
                    publishCalls++
                    instagramSuccessResponse(request, request.url.encodedPath)
                }
                request.url.encodedPath.endsWith("/published-1") -> throw CancellationException("permalink cancelled")
                else -> instagramSuccessResponse(request, request.url.encodedPath)
            }
        }.build()

        try {
            uploader(root, client).upload(source, UploadMetadata("Reel")) {}
            throw AssertionError("CancellationException should propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("permalink cancelled", cancelled.message)
        }

        assertEquals(1, publishCalls)
    }

    @Test
    fun `lost processing response after bytes upload is ambiguous without a fallback container`() = withFiles { root, source ->
        var creates = 0
        var polls = 0
        var publishes = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            when {
                path.endsWith("/media") -> {
                    creates++
                    response(request, 200, """{"id":"container-1","uri":"https://upload.example/container-1"}""")
                }
                request.url.host == "upload.example" -> response(request, 200)
                path.endsWith("/container-1") -> { polls++; throw IOException("lost processing response") }
                path.endsWith("/media_publish") -> { publishes++; response(request, 200, """{"id":"published"}""") }
                else -> instagramSuccessResponse(request, path)
            }
        }.build()

        val result = uploader(root, client).upload(source, UploadMetadata("Reel")) {}

        assertEquals(1, creates)
        assertEquals(1, polls)
        assertEquals(0, publishes)
        assertTrue(result is UploadResult.Failure)
        assertFalse((result as UploadResult.Failure).retryable)
        assertTrue(result.message.contains("no confirmó el procesamiento"))
    }

    @Test
    fun `provider rejection tries original then trimmed then normalized bytes`() = withFiles { root, source ->
        val stageUploaded = mutableListOf<Byte>()
        val creates = mutableListOf<Int>()
        val trim = object : TrimProcessor {
            override suspend fun trim(input: File, output: File, startSec: Double, maxDurationSec: Double): Result<Unit> {
                output.writeBytes(byteArrayOf(0x42))
                return Result.success(Unit)
            }
        }
        val normalize = object : NormalizeProcessor {
            override suspend fun normalize(input: File, output: File): Result<Unit> {
                output.writeBytes(byteArrayOf(0x43))
                return Result.success(Unit)
            }
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            when {
                path.endsWith("/media") -> {
                    creates += 1
                    if (creates.size < 3) response(request, 400, "rejected")
                    else response(request, 200, """{"id":"container-3","uri":"https://upload.example/container-3"}""")
                }
                request.url.host == "upload.example" -> {
                    val body = Buffer()
                    request.body!!.writeTo(body)
                    stageUploaded += body.readByte()
                    response(request, 200)
                }
                path.endsWith("/container-3") -> response(request, 200, """{"status_code":"FINISHED"}""")
                path.endsWith("/media_publish") -> response(request, 200, """{"id":"published-3"}""")
                path.endsWith("/published-3") -> response(request, 200, """{"permalink":"https://instagram.example/reel/3"}""")
                else -> error("Unexpected Instagram request ${request.method} ${request.url}")
            }
        }.build()

        val result = uploader(root, client, trim = trim, normalize = normalize)
            .upload(source, UploadMetadata("Reel")) {}

        assertEquals(3, creates.size)
        assertEquals(listOf(0x43.toByte()), stageUploaded)
        assertTrue(result is UploadResult.Success)
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("ig_trim_") || it.name.startsWith("ig_normalized_") })
    }

    @Test
    fun `cancellation while waiting for Meta processing propagates before publish`() = withFiles { root, source ->
        var publishCalls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            when {
                path.endsWith("/media_publish") -> { publishCalls++; response(request, 200, """{"id":"published"}""") }
                path.endsWith("/container-1") -> response(request, 200, """{"status_code":"IN_PROGRESS"}""")
                else -> instagramSuccessResponse(request, path)
            }
        }.build()

        try {
            uploader(root, client, pollDelay = { throw CancellationException("fixture cancelled") })
                .upload(source, UploadMetadata("Reel")) {}
            throw AssertionError("CancellationException should propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("fixture cancelled", cancelled.message)
        }

        assertEquals(0, publishCalls)
    }

    @Test
    fun `Facebook crosspost failure keeps confirmed Instagram success`() = withFiles { root, source ->
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            when {
                path.endsWith("/page-1/video_reels") -> response(request, 400, """{"error":{"message":"page rejected"}}""")
                else -> instagramSuccessResponse(request, path)
            }
        }.build()

        val result = uploader(root, client, pageId = "page-1")
            .upload(source, UploadMetadata("Reel", crossPostFacebook = true)) {}

        assertTrue(result is UploadResult.Success)
        assertEquals("published-1", (result as UploadResult.Success).platformId)
        assertTrue(result.facebookCrossPost is FacebookCrossPostResult.Failed)
    }
}
