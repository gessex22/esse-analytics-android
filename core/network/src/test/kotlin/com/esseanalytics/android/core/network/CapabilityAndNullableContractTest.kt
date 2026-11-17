package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import com.esseanalytics.android.core.network.interceptor.CapabilityInterceptor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapabilityAndNullableContractTest {
    @Test
    fun `la central recibe la capacidad de metricas en cada peticion`() {
        var seen: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(CapabilityInterceptor())
            .addInterceptor { chain ->
                seen = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{}".toResponseBody(null)).build()
            }
            .build()
        client.newCall(Request.Builder().url("https://central.invalid/api/sync/group-stats").build()).execute().close()
        assertEquals("metrics-nullable-v1", seen!!.header("X-Esse-Capabilities"))
    }

    @Test
    fun `null es dato desconocido y el formato antiguo con cero sigue siendo cero observado`() {
        val json = Json { ignoreUnknownKeys = true }
        val unknown = json.decodeFromString<GroupStatsResponse>(
            """{"items":[{"fileId":"f","fileName":"a.mp4","platforms":{"instagram":{"platformId":"ig","views":4,"likes":null,"comments":null,"likesAvailable":false,"commentsAvailable":false}}}]}""",
        )
        val slot = unknown.items.single().platforms.getValue("instagram")
        assertNull(slot.likes)
        assertNull(slot.comments)
        assertEquals(false, slot.likesAvailable)

        val legacy = json.decodeFromString<GroupStatsResponse>(
            """{"items":[{"fileId":"f","fileName":"a.mp4","platforms":{"youtube":{"platformId":"yt","views":4,"likes":0,"comments":0}}}]}""",
        )
        val zero = legacy.items.single().platforms.getValue("youtube")
        assertEquals(0L, zero.likes)
        assertNull(zero.likesAvailable)
    }
}
