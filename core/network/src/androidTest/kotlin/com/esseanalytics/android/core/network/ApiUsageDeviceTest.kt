package com.esseanalytics.android.core.network

import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Host de biblioteca separado de la app instalada: sin TokenStore ni datos de usuario. */
class ApiUsageDeviceTest {
    @Test
    fun queueSurvivesRecreationOnTheAndroidFilesystem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.cacheDir, "usage-queue-${java.util.UUID.randomUUID()}")
        val file = java.io.File(directory, "queue.json")
        val now = java.time.Instant.parse("2026-10-06T12:20:00Z").toEpochMilli()
        val hour = "2026-10-06T12:00:00.000Z"
        try {
            var queue = ApiUsageOutbox(file) { now }
            queue.record("https://central.example/private?token=secret", "a", "youtube", hour, false)
            val sent = queue.record("https://central.example", "a", "youtube", hour, true)
            assertTrue("Atomic storage must exist on the phone", file.exists())
            queue = ApiUsageOutbox(file) { now }
            assertEquals(sent, queue.pending("https://central.example", "a").single())
            queue.record("https://central.example", "a", "youtube", hour, true)
            queue.acknowledge(sent)
            val latest = queue.pending("https://central.example", "a").single()
            assertEquals(3, latest.count)
            assertEquals(sent.clientSessionId, latest.clientSessionId)
            queue.acknowledge(latest)
            queue = ApiUsageOutbox(file) { now }
            assertTrue(queue.pending("https://central.example", "a").isEmpty())
            assertTrue(queue.pending("https://central.example", "b").isEmpty())
            assertFalse(file.readText().contains("secret"))
            assertFalse(file.readText().contains("private"))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun realReporterReachesIsolatedLabFromThePhone() {
        val args = InstrumentationRegistry.getArguments()
        val base = requireNotNull(args.getString("base"))
        require(base.matches(Regex("http://127\\.0\\.0\\.1:[0-9]+")))
        val token = requireNotNull(args.getString("token"))
        val expired = requireNotNull(args.getString("expiredToken"))
        val userId = requireNotNull(args.getString("userId"))
        val statuses = ConcurrentLinkedQueue<Int>()
        val delivered = CountDownLatch(10)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val healthClient = OkHttpClient()
        healthClient.newCall(Request.Builder().url("$base/api/health").build()).execute().use {
            assertEquals(200, it.code)
            val health = kotlinx.serialization.json.Json.parseToJsonElement(it.body!!.string())
            assertEquals("lab", (health as kotlinx.serialization.json.JsonObject)["environment"]?.let { value ->
                (value as kotlinx.serialization.json.JsonPrimitive).content
            })
        }
        val sender = OkHttpClient.Builder().addInterceptor { chain ->
            require(chain.request().url.toString() == "$base/api/api-usage/client-snapshot")
            val response = chain.proceed(chain.request())
            statuses.add(response.code)
            delivered.countDown()
            response
        }.build()
        val reporter = ClientApiUsageReporter({ token to userId }, { base }, sender, scope)
        val expiredReporter = ClientApiUsageReporter({ expired to (args.getString("expiredUserId") ?: userId) }, { base }, sender, scope)
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
                } catch (_: IOException) { /* Conserva el fallo funcional. */ }
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
            expiredReporter.begin("youtube")(false)
            assertTrue("All telemetry requests must finish", delivered.await(20, TimeUnit.SECONDS))
            assertEquals(9, statuses.count { it == 200 })
            assertEquals(1, statuses.count { it == 401 })
        } finally {
            scope.cancel()
            for (client in listOf(sender, healthClient)) {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }
}
