package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.DashboardSummaryDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Collections
import java.io.IOException
import kotlinx.coroutines.CancellationException

class DashboardContractTest {
    @Test fun `unmatched confirmed publication preserves unavailable metrics flag`() {
        val video = Json.decodeFromString<com.esseanalytics.android.core.network.dto.DashboardVideoDto>("""{
            "platform":"youtube","platformId":"latest","platformUrl":"","title":"Latest unmatched",
            "thumbnail":"","views":0,"publishedAt":"2026-10-17T10:00:00Z","metricsAvailable":false
        }""")
        assertEquals(false, video.metricsAvailable)
    }
    private val payload = """{
      "generatedAt":"2026-10-17T17:00:00Z","timeZone":"America/Chicago",
      "followers":{"total":3000000000,"partial":true,"platforms":[
        {"platform":"youtube","count":3000000000,"weeklyGain":-20,"status":"ready"},
        {"platform":"instagram","count":null,"weeklyGain":null,"status":"syncing"},
        {"platform":"tiktok","count":null,"weeklyGain":null,"status":"reconnect"}]},
      "week":{"start":"2026-10-12T05:00:00Z","publications":0,"views":null,"viewsDeltaPercent":null},
      "pending":{"total":0,"toPublish":0,"drafts":0,"failed":0},
      "latest":[{"platform":"youtube","video":null},{"platform":"instagram","video":null},{"platform":"tiktok","video":null}]
    }"""

    @Test fun `calendar tasks and collecting history status decode through the existing summary endpoint`() = runTest {
        val modern = payload.replace("\"failed\":0}", """"failed":0,"source":"calendar","items":[{"platform":"youtube","fileId":"same-video","title":"Del calendario","dueDate":"2026-10-16","overdue":true}]}""")
            .replace("\"viewsDeltaPercent\":null}", """"viewsDeltaPercent":null,"viewsStatus":"collecting_history"}""")
        val requests = mutableListOf<Request>()
        val result = SyncRepository(fixtureApi { request -> requests += request; 200 to modern }).getDashboardSummary("America/Chicago")
        assertEquals("calendar", result.data.pending?.source)
        assertEquals("youtube", result.data.pending?.items?.first()?.platform)
        assertEquals(true, result.data.pending?.items?.first()?.overdue)
        assertEquals("collecting_history", result.data.week.viewsStatus)
        assertNull(result.data.week.views)
        assertEquals(listOf("/api/sync/dashboard-summary"), requests.map { it.url.encodedPath })
    }

    @Test fun `contract preserves unknown counters empty rows signed gains and 64 bit totals`() {
        val summary = Json.decodeFromString<DashboardSummaryDto>(payload)
        assertEquals(3_000_000_000L, summary.followers.total)
        assertEquals(-20L, summary.followers.platforms[0].weeklyGain)
        assertNull(summary.week.views)
        assertEquals(3, summary.latest.size)
        assertTrue(summary.latest.all { it.video == null })
        assertEquals("reconnect", summary.followers.platforms[2].status)
    }

    @Test fun `dashboard uses dedicated endpoint with encoded local timezone without stats or calendar calls`() = runTest {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            assertEquals("no-cache", chain.request().header("Cache-Control"))
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://central.invalid/").client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(SyncApi::class.java)
        val result = SyncRepository(api).getDashboardSummary("America/Chicago")
        assertEquals(0, result.data.pending?.total)
        assertFalse(result.compatibilityMode)
        assertEquals(listOf("https://central.invalid/api/sync/dashboard-summary?timeZone=America%2FChicago"), requests)
    }

    private fun fixtureApi(response: (Request) -> Pair<Int, String>): SyncApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = response(chain.request())
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://central.invalid/").client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(SyncApi::class.java)
    }

    private fun legacyVideo(platform: String) = """{"items":[{
        "fileId":"file-$platform","fileName":"$platform.mp4","fecha_creacion":null,
        "platforms":{"$platform":{"platformId":"id-$platform","platformUrl":"https://example.invalid/$platform",
        "title":"Video $platform","thumbnail":"","views":72,"likes":1,"comments":0}}
    }]}"""

    @Test fun `missing summary route uses existing per platform stats without inventing weekly or pending totals`() = runTest {
        for (status in listOf(404, 405)) {
            val requests = Collections.synchronizedList(mutableListOf<Request>())
            val api = fixtureApi { request ->
                requests += request
                if (request.url.encodedPath.endsWith("dashboard-summary")) status to "{}"
                else {
                    assertEquals("/api/sync/group-stats", request.url.encodedPath)
                    assertEquals("1", request.url.queryParameter("limit"))
                    assertEquals("recent", request.url.queryParameter("mode"))
                    val platform = request.url.queryParameter("platform")!!
                    200 to if (platform == "tiktok") "{\"items\":[]}" else legacyVideo(platform)
                }
            }
            val result = SyncRepository(api).getDashboardSummary("America/Chicago")
            assertTrue(result.compatibilityMode)
            assertEquals(4, requests.size)
            assertNull(result.data.followers.total)
            assertTrue(result.data.followers.platforms.all { it.count == null })
            assertNull(result.data.week.publications)
            assertNull(result.data.week.views)
            assertNull(result.data.pending)
            assertEquals(listOf("youtube", "instagram", "tiktok"), result.data.latest.map { it.platform })
            assertEquals("id-youtube", result.data.latest[0].video?.platformId)
            assertEquals("id-instagram", result.data.latest[1].video?.platformId)
            assertNull(result.data.latest[0].video?.publishedAt)
            assertNull(result.data.latest[2].video)
        }
    }

    @Test fun `authentication and service failures never trigger legacy fallback`() = runTest {
        for (status in listOf(401, 403, 429, 500, 503)) {
            val requests = mutableListOf<Request>()
            val api = fixtureApi { request -> requests += request; status to "{}" }
            try {
                SyncRepository(api).getDashboardSummary("UTC")
                fail("Expected HTTP $status")
            } catch (error: HttpException) {
                assertEquals(status, error.code())
                assertEquals(1, requests.size)
            }
        }
    }

    @Test fun `upgrade is detected on next refresh instead of sticking to compatibility mode`() = runTest {
        var upgraded = false
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val api = fixtureApi { request ->
            requests += request
            if (request.url.encodedPath.endsWith("dashboard-summary")) {
                assertEquals("no-cache", request.header("Cache-Control"))
                if (upgraded) 200 to payload else 404 to "{}"
            } else 200 to "{\"items\":[]}"
        }
        val repository = SyncRepository(api)
        assertTrue(repository.getDashboardSummary("UTC").compatibilityMode)
        upgraded = true
        assertFalse(repository.getDashboardSummary("UTC").compatibilityMode)
        assertEquals(5, requests.size)
    }

    @Test fun `failed legacy reads propagate instead of replacing unavailable platforms with empty rows`() = runTest {
        val api = fixtureApi { request ->
            if (request.url.encodedPath.endsWith("dashboard-summary")) 404 to "{}" else 503 to "{}"
        }
        try {
            SyncRepository(api).getDashboardSummary("UTC")
            fail("Expected legacy failure")
        } catch (error: HttpException) { assertEquals(503, error.code()) }
    }

    @Test fun `network failures and cancellation propagate without requesting legacy data`() = runTest {
        for (failure in listOf(IOException("fixture offline"), CancellationException("fixture cancelled"))) {
            val api = object : FakeSyncApi() {
                override suspend fun getDashboardSummary(timeZone: String): DashboardSummaryDto = throw failure
            }
            try {
                SyncRepository(api).getDashboardSummary("UTC")
                fail("Expected original failure")
            } catch (error: Exception) { assertSame(failure, error) }
        }
    }
}
