package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.interceptor.PlatformUsageInterceptor
import com.esseanalytics.android.core.network.interceptor.providerForHost
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlatformUsageInterceptorTest {
    private fun response(request: Request, code: Int) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("stub")
        .body("provider response".toResponseBody()).build()

    @Test
    fun failedResponseKeepsIdentityCapturedBeforeTheCall() {
        var account = "account-a"
        val reports = mutableListOf<Triple<String, String, Boolean>>()
        val client = OkHttpClient.Builder()
            .addInterceptor(PlatformUsageInterceptor { provider ->
                val capturedAccount = account
                val complete: (Boolean) -> Unit = { failed ->
                    reports += Triple(capturedAccount, provider, failed)
                }
                complete
            })
            .addInterceptor { chain ->
                account = "account-b"
                response(chain.request(), 503)
            }.build()
        client.newCall(Request.Builder().url("https://graph.facebook.com/me?access_token=secret").build())
            .execute().use { assertEquals(503, it.code) }
        assertEquals(listOf(Triple("account-a", "instagram", true)), reports)
    }

    @Test
    fun brokenTelemetryDoesNotChangeProviderResponse() {
        val client = OkHttpClient.Builder()
            .addInterceptor(PlatformUsageInterceptor { throw IllegalStateException("telemetry failed") })
            .addInterceptor { chain -> response(chain.request(), 200) }.build()
        client.newCall(Request.Builder().url("https://www.googleapis.com/youtube/v3/videos").build())
            .execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun networkFailureStillCountsAndPreservesOriginalError() {
        var failures = 0
        val originalError = IOException("provider unavailable")
        val client = OkHttpClient.Builder()
            .addInterceptor(PlatformUsageInterceptor {
                val complete: (Boolean) -> Unit = { failed ->
                    if (failed) failures++
                    throw IllegalStateException("telemetry failed")
                }
                complete
            })
            .addInterceptor { throw originalError }.build()
        try {
            client.newCall(Request.Builder().url("https://open.tiktokapis.com/v2/video/list/").build()).execute()
            fail("Expected the provider error")
        } catch (error: IOException) {
            assertSame(originalError, error)
        }
        assertEquals(1, failures)
    }

    @Test
    fun providerAllowlistRejectsUnrelatedAndLookalikeHosts() {
        assertNull(providerForHost("api.esse-analytics.com"))
        assertNull(providerForHost("googleapis.com.evil.example"))
        assertNull(providerForHost("fakegoogleapis.com"))
        assertEquals("youtube", providerForHost("youtube.googleapis.com"))
        assertEquals("instagram", providerForHost("rupload.facebook.com"))
        assertEquals("tiktok", providerForHost("upload.tiktokapis.com"))
    }
}
