package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.dto.TiktokTokenResponse
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class TiktokUploaderTest {
    @Test fun initAndPutAgreeForPhpAndMultiChunkFiles() = runBlocking {
        val auth = Proxy.newProxyInstance(PlatformAuthApi::class.java.classLoader, arrayOf(PlatformAuthApi::class.java)) { _, method, _ ->
            check(method.name == "tiktokToken")
            TiktokTokenResponse("fixture", "fixture")
        } as PlatformAuthApi
        for (size in listOf(4 * 1024 * 1024L, 17_301_900L, 20 * 1024 * 1024L + 123)) {
            val file = File.createTempFile("tiktok-chunks-", ".mp4")
            try {
                file.outputStream().use { it.write(ByteArray(size.toInt()) { 0xAB.toByte() }) }
                val count = (size / (10 * 1024 * 1024)).coerceAtLeast(1).toInt()
                val chunkSize = if (count == 1) size else 10 * 1024 * 1024L
                var inits = 0
                var puts = 0
                var bytes = 0L
                val client = OkHttpClient.Builder().addInterceptor { chain ->
                    val request = chain.request()
                    val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("fixture")
                    if (request.url.encodedPath.endsWith("/video/init/")) {
                        inits++
                        val buffer = Buffer()
                        request.body!!.writeTo(buffer)
                        val source = Json.parseToJsonElement(buffer.readUtf8()).jsonObject.getValue("source_info").jsonObject
                        assertEquals(size, source.getValue("video_size").jsonPrimitive.long)
                        assertEquals(chunkSize, source.getValue("chunk_size").jsonPrimitive.long)
                        assertEquals(count, source.getValue("total_chunk_count").jsonPrimitive.int)
                        response.code(200).body("""{"data":{"publish_id":"fixture","upload_url":"https://upload.example.invalid/put"}}""".toResponseBody()).build()
                    } else {
                        assertEquals("https://upload.example.invalid/put", request.url.toString())
                        assertEquals("PUT", request.method)
                        val start = puts * chunkSize
                        val end = if (puts == count - 1) size - 1 else start + chunkSize - 1
                        assertEquals("bytes $start-$end/$size", request.header("Content-Range"))
                        assertEquals(end - start + 1, request.body!!.contentLength())
                        bytes += request.body!!.contentLength()
                        puts++
                        // Sin polling ni publicación: el último PUT falla de forma simulada.
                        response.code(if (puts == count) 500 else 206).body("".toResponseBody()).build()
                    }
                }.build()
                val result = TiktokUploader(auth, client).upload(file, UploadMetadata(title = "fixture")) {}
                assertTrue(result is UploadResult.Failure)
                assertEquals(1, inits)
                assertEquals(count, puts)
                assertEquals(size, bytes)
            } finally { file.delete() }
        }
    }
}
