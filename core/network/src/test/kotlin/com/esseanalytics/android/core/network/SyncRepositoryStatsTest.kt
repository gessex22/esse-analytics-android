package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRepositoryStatsTest {
    @Test
    fun `stats cache is separated by mode platform and limit while force refreshes same key`() = runTest {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{\"items\":[]}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl("https://central.invalid/")
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncApi::class.java)
        val repository = SyncRepository(api)

        repository.getGroupStats(limit = 5)
        repository.getGroupStats(limit = 5)
        repository.getGroupStats(limit = 5, mode = "recent")
        repository.getGroupStats(limit = 10)
        repository.getGroupStats(limit = 5, platform = "youtube")
        repository.getGroupStats(limit = 5, platform = "youtube", mode = "recent")
        repository.getGroupStats(limit = 5, platform = "youtube", force = true, mode = "recent")

        assertEquals(6, requests.size)
        assertEquals("https://central.invalid/api/sync/group-stats?limit=5", requests[0])
        assertEquals("https://central.invalid/api/sync/group-stats?limit=5&mode=recent", requests[1])
        assertEquals("https://central.invalid/api/sync/group-stats?limit=10", requests[2])
        assertEquals("https://central.invalid/api/sync/group-stats?limit=5&platform=youtube", requests[3])
        assertEquals("https://central.invalid/api/sync/group-stats?limit=5&platform=youtube&mode=recent", requests[4])
        assertEquals(requests[4], requests[5])
    }

    @Test
    fun `group stats DTO accepts a missing recent date`() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<GroupStatsResponse>("""{"items":[{"fileId":"f1","fileName":"clip.mp4","fecha_creacion":null,"platforms":{"youtube":{"platformId":"yt1","platformUrl":"https://example.invalid/yt1","title":"clip","thumbnail":"","views":3,"likes":1,"comments":0}}}]}""")
        assertEquals(null, decoded.items.single().fecha_creacion)
        assertEquals("yt1", decoded.items.single().platforms["youtube"]?.platformId)
        assertEquals(null, decoded.items.single().platforms["tiktok"])
    }
}
