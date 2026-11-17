package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditHistoryCacheTest {
    private fun repository(requests: MutableList<String>): SyncRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            val body = if (chain.request().url.encodedPath.endsWith("history")) "{\"items\":[],\"total\":0}" else "[]"
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://audit.invalid/").client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(SyncApi::class.java)
        return SyncRepository(api)
    }
    @Test fun historyCacheMustRespectRequestedLimit() = runTest {
        val requests = mutableListOf<String>(); val repo = repository(requests)
        repo.getHistory(limit = 1)
        repo.getHistory(limit = 10)
        assertEquals("Different history limits require different cache entries", 2, requests.size)
    }
    @Test fun forcedHistoryRefreshBypassesCache() = runTest {
        val requests = mutableListOf<String>(); val repo = repository(requests)
        repo.getHistory(limit = 1)
        repo.getHistory(limit = 10, force = true)
        assertEquals(2, requests.size)
    }
    @Test fun forcedCalendarRefreshBypassesCache() = runTest {
        val requests = mutableListOf<String>(); val repo = repository(requests)
        repo.getCalendarConfig()
        repo.getCalendarConfig()
        repo.getCalendarConfig(force = true)
        assertEquals(2, requests.size)
    }
}
