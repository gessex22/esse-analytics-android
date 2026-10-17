package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class StudioStatsContractTest {
    private val detail = """{"fileId":"file","fileName":"a.mp4","fecha_creacion":null,"platforms":{"youtube":{"platformId":"exact","platformUrl":null,"title":null,"thumbnail":null,"views":0,"likes":0,"comments":0}},"metricsAvailable":false,"contentId":"canonical","last24Hours":{"views":null,"likes":0,"comments":null,"estimated":true,"status":"collecting"}}"""
    private fun api(response: (Request) -> Pair<Int,String>): SyncApi {
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            val (status,body)=response(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Fixture").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://central.invalid/").client(client).addConverterFactory(Json { ignoreUnknownKeys=true }.asConverterFactory("application/json".toMediaType())).build().create(SyncApi::class.java)
    }
    @Test fun `detail preserves null date metadata unknown counters and observed daily zero`() {
        val item=Json.decodeFromString<GroupStatsItemDto>(detail)
        assertNull(item.fecha_creacion); assertNull(item.platforms["youtube"]?.thumbnail); assertEquals(false,item.metricsAvailable)
        assertNull(item.last24Hours?.views); assertEquals(0L,item.last24Hours?.likes); assertEquals("canonical",item.contentId)
    }
    @Test fun `legacy statistics remain decodable without additive fields`() {
        val item=Json.decodeFromString<GroupStatsItemDto>("""{"fileId":"old","fileName":"old.mp4","platforms":{}}""")
        assertNull(item.last24Hours); assertNull(item.metricsAvailable); assertNull(item.contentId)
    }
    @Test fun `detail sends exact identity with URL encoding and bypasses cache`() = runTest {
        val api=api { request ->
            assertEquals("/api/sync/video-detail",request.url.encodedPath)
            assertEquals("youtube",request.url.queryParameter("platform")); assertEquals("a/b ?",request.url.queryParameter("platformId"))
            assertNull(request.url.queryParameter("fileName")); assertEquals("no-cache",request.header("Cache-Control")); 200 to detail
        }
        assertEquals("file",api.getVideoDetail(platform="youtube",platformId="a/b ?").fileId)
    }
    @Test fun `weekly endpoint carries timezone and twelve closed weeks and nullable gains`() = runTest {
        val payload="""{"generatedAt":"2026-10-17T12:00:00Z","timeZone":"America/Chicago","weeks":[{"id":"w1","start":"2026-10-05T05:00:00Z","end":"2026-10-12T05:00:00Z","status":"partial","totals":{"views":3000000000,"likes":0,"comments":null,"subscribersGained":-12},"partialMetrics":["comments"],"platforms":[{"platform":"youtube","views":3000000000,"likes":0,"comments":null,"subscribersGained":-12}]}]}"""
        val result=api { request ->
            assertEquals("/api/sync/weekly-stats",request.url.encodedPath); assertEquals("America/Chicago",request.url.queryParameter("timeZone")); assertEquals("12",request.url.queryParameter("limit")); 200 to payload
        }.getWeeklyStats("America/Chicago")
        val w=result.weeks.single(); assertEquals(3000000000L,w.totals.views); assertEquals(0L,w.totals.likes); assertNull(w.totals.comments); assertEquals(-12L,w.totals.subscribersGained)
    }
    @Test fun `history opts into statistics without changing legacy parameters`() = runTest {
        val result=api { request ->
            assertEquals("true",request.url.queryParameter("includeStats")); assertEquals("30",request.url.queryParameter("offset")); assertEquals("instagram",request.url.queryParameter("platform"))
            200 to """{"total":31,"items":[{"id":"event","platform":"youtube","platformId":"exact","publishedAt":"2026-10-17T12:00:00Z","stats":$detail}]}"""
        }.getHistory(offset=30,platform="instagram",includeStats=true)
        assertEquals(31,result.total); assertEquals("canonical",result.items.single().stats?.contentId)
    }
    @Test fun `old history requests omit optional includeStats`() = runTest {
        api { request -> assertNull(request.url.queryParameter("includeStats")); 200 to """{"items":[],"total":0}""" }.getHistory()
    }
    @Test fun `new endpoints surface compatibility and authentication errors without fabricated data`() = runTest {
        for(code in listOf(401,404,405,503)) {
            val api=api { code to """{"message":"fixture"}""" }
            try { api.getWeeklyStats("UTC"); fail("Expected $code") } catch(e:HttpException) { assertEquals(code,e.code()) }
            try { api.getVideoDetail(platform="youtube",platformId="exact"); fail("Expected $code") } catch(e:HttpException) { assertEquals(code,e.code()) }
        }
    }
    @Test fun `dashboard actions decode causes without requiring legacy calendar rows`() {
        val pending=Json.decodeFromString<DashboardPendingDto>("""{"total":1,"toPublish":0,"drafts":0,"failed":1,"source":"tasks","actions":[{"id":"a","fileId":"file","fileName":"a.mp4","title":"A","contentId":"canonical","cause":"failed","missingPlatforms":["instagram"],"failedPlatforms":["youtube"]}]}""")
        assertEquals("canonical",pending.actions.single().contentId); assertTrue(pending.items.isEmpty()); assertEquals("youtube",pending.actions.single().failedPlatforms.single())
    }
    @Test fun `individual platform counters support values above signed 32 bit`() {
        val item=Json.decodeFromString<GroupStatsItemDto>(detail.replace("\"views\":0", "\"views\":3000000000"))
        assertEquals(3000000000L,item.platforms["youtube"]?.views)
    }
}
