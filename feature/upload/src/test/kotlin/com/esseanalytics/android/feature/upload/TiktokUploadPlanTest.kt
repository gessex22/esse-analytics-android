package com.esseanalytics.android.feature.upload

import org.junit.Assert.*
import org.junit.Test

class TiktokUploadPlanTest {
    private val mib = 1024L * 1024

    @Test fun singleChunkDeclaresTheWholeFileIncludingPhp() {
        for (size in listOf(1L, 4 * mib, 10 * mib, 10 * mib + 123, 17_301_900L, 20 * mib - 1)) {
            val plan = TiktokUploadPlan(size)
            assertEquals(1, plan.totalChunks)
            assertEquals(size, plan.chunkSize)
            assertEquals(0L..size - 1, plan.range(0))
        }
    }

    @Test fun multipleChunksAbsorbTrailingBytesWithoutGaps() {
        for (size in listOf(20 * mib, 20 * mib + 123, 25 * mib + 123, 64 * mib + 1, 4 * 1024 * mib)) {
            val plan = TiktokUploadPlan(size)
            assertEquals((size / (10 * mib)).toInt(), plan.totalChunks)
            assertEquals(10 * mib, plan.chunkSize)
            var next = 0L
            for (index in 0 until plan.totalChunks) {
                val range = plan.range(index)
                assertEquals(next, range.first)
                assertTrue(range.last >= range.first)
                next = range.last + 1
            }
            assertEquals(size, next)
            assertTrue(plan.range(plan.totalChunks - 1).count() >= plan.chunkSize)
        }
    }

    @Test fun emptyFileIsRejected() {
        for (size in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { TiktokUploadPlan(size) }
        }
    }
}
