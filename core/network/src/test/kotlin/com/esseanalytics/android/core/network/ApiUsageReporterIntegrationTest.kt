package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.interceptor.PlatformUsageInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue

class ApiUsageReporterIntegrationTest {
    @Test
    fun providerCallsReachTheIsolatedLabUsingTheRealReporter() {
        val base = System.getenv("API_USAGE_E2E_URL")
        assumeTrue("Run scripts/test-api-usage-integration.mjs for this integration test", !base.isNullOrBlank())
        require(base!!.startsWith("http://127.0.0.1:")) { "This test requires an isolated loopback fixture" }
        val token = requireNotNull(System.getenv("API_USAGE_E2E_TOKEN"))
        val expiredToken = requireNotNull(System.getenv("API_USAGE_E2E_EXPIRED_TOKEN"))
        val userId = requireNotNull(System.getenv("API_USAGE_E2E_USER_ID"))
        val statuses = ConcurrentLinkedQueue<Int>()
        val delivered = CountDownLatch(10)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sender = OkHttpClient.Builder().addInterceptor { chain ->
            require(chain.request().url.toString() == "$base/api/api-usage/client-snapshot")
            val response = chain.proceed(chain.request())
            statuses.add(response.code)
            delivered.countDown()
            response
        }.build()
        val reporter = ClientApiUsageReporter({ token to userId }, { base }, sender, scope)
        val expiredReporter = ClientApiUsageReporter({ expiredToken to (System.getenv("API_USAGE_E2E_EXPIRED_USER_ID") ?: userId) }, { base }, sender, scope)
        try {
            for (host in listOf("www.googleapis.com", "graph.facebook.com", "open.tiktokapis.com")) {
                var call = 0
                val client = OkHttpClient.Builder()
                    .addInterceptor(PlatformUsageInterceptor(reporter::begin))
                    .addInterceptor { chain ->
                        call++
                        if (call == 3) throw IOException("simulated provider network failure")
                        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(if (call == 1) 200 else 503).message("simulated provider")
                            .body("simulated response".toResponseBody()).build()
                    }.build()
                val request = Request.Builder().url("https://$host/request?access_token=synthetic-secret").build()
                client.newCall(request).execute().use { assertEquals(200, it.code) }
                client.newCall(request).execute().use { assertEquals(503, it.code) }
                try {
                    client.newCall(request).execute()
                    fail("Expected the simulated network error")
                } catch (_: IOException) { /* Functional error is preserved. */ }
            }
            // Una sesión vencida no impide la respuesta del proveedor ni borra
            // otra sesión: este cliente de telemetría no usa AuthAuthenticator.
            expiredReporter.begin("youtube")(false)
            assertTrue("All telemetry requests must finish", delivered.await(15, TimeUnit.SECONDS))
            assertEquals(9, statuses.count { it == 200 })
            assertEquals(1, statuses.count { it == 401 })
        } finally {
            scope.cancel()
            sender.dispatcher.executorService.shutdown()
            sender.connectionPool.evictAll()
        }
    }
}
