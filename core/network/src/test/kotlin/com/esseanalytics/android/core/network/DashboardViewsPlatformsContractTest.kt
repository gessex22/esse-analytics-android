package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.dto.DashboardSummaryDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardViewsPlatformsContractTest {
    @Test
    fun readsPlatformSignatureFromWeek() {
        val summary = Json { ignoreUnknownKeys = true }.decodeFromString<DashboardSummaryDto>("""
            {"generatedAt":"2026-10-20T14:00:00.000Z","timeZone":"America/Chicago",
             "followers":{"total":0,"partial":false,"platforms":[]},
             "week":{"start":"2026-10-19T05:00:00.000Z","publications":0,"views":123,
                     "viewsDeltaPercent":null,"viewsEstimated":true,"viewsStatus":"ready",
                     "viewsPlatforms":["youtube","tiktok"]},
             "pending":null,"latest":[]}
        """.trimIndent())

        assertEquals(listOf("youtube", "tiktok"), summary.week.viewsPlatforms)
        assertEquals("ready", summary.week.viewsStatus)
    }
}
