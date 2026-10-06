package com.esseanalytics.android.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ApiUsageOutboxTest {
    @Test
    fun restartRetainsIdentityCountsAndOnlyAcknowledgesTheSentVersion() {
        val directory = Files.createTempDirectory("usage-outbox-").toFile()
        val file = directory.resolve("queue.json")
        val hour = "2026-10-06T12:00:00.000Z"
        var now = java.time.Instant.parse("2026-10-06T12:20:00Z").toEpochMilli()
        try {
            var queue = ApiUsageOutbox(file) { now }
            queue.record("https://central.example/private?token=secret", "a", "youtube", hour, false)
            val sent = queue.record("https://central.example", "a", "youtube", hour, true)
            queue.record("https://other.example", "a", "youtube", hour, false)
            queue.record("https://central.example", "b", "youtube", hour, false)
            queue = ApiUsageOutbox(file) { now }
            assertEquals(sent, queue.pending("https://central.example", "a").single())
            queue.record("https://central.example", "a", "youtube", hour, true)
            queue.acknowledge(sent)
            val later = queue.pending("https://central.example", "a").single()
            assertEquals(3, later.count)
            assertEquals(sent.clientSessionId, later.clientSessionId)
            queue.acknowledge(later)
            queue = ApiUsageOutbox(file) { now }
            assertTrue(queue.pending("https://central.example", "a").isEmpty())
            val increment = queue.record("https://central.example", "a", "youtube", hour, false)
            assertEquals(4, increment.count)
            assertEquals(sent.clientSessionId, increment.clientSessionId)
            assertFalse(file.readText().contains("secret"))
            assertFalse(file.readText().contains("private"))
            now += 91L * 86_400_000
            assertTrue(queue.pending("https://central.example", "a").isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun realReporterReplaysAfterRestartWithNewTokenOnlyForMatchingScope() = runBlocking {
        val directory = Files.createTempDirectory("usage-reporter-").toFile()
        val file = directory.resolve("queue.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = mutableListOf<String>()
        var status = 503
        var credentials = "old-secret" to "a"
        var base = "https://central.example"
        val sender = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            requests.add(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("fixture").body("".toResponseBody()).build()
        }.build()
        try {
            var reporter = ClientApiUsageReporter({ credentials }, { base }, sender, scope, file)
            reporter.begin("tiktok")(true)
            assertEquals(1, requests.size)
            reporter = ClientApiUsageReporter({ credentials }, { base }, sender, scope, file)
            credentials = "other-secret" to "b"
            status = 200
            reporter.flushPending()
            assertEquals(1, requests.size)
            credentials = "renewed-secret" to "a"
            base = "https://other.example"
            reporter.flushPending()
            assertEquals(1, requests.size)
            base = "https://central.example"
            reporter.flushPending()
            assertEquals(2, requests.size)
            assertEquals(requests[0], requests[1])
            reporter.flushPending()
            assertEquals(2, requests.size)
            assertFalse(file.readText().contains("secret"))
        } finally {
            scope.cancel(); sender.dispatcher.executorService.shutdown(); sender.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun diskFailureDoesNotBreakRecording() {
        val directory = Files.createTempDirectory("usage-outbox-").toFile()
        try {
            val blocked = directory.resolve("blocked").apply { writeText("not a directory") }
            val queue = ApiUsageOutbox(blocked.resolve("queue.json"))
            assertEquals(1, queue.record("https://central.example", "a", "youtube", "2026-10-06T12:00:00.000Z", false).count)
        } finally { directory.deleteRecursively() }
    }
}
