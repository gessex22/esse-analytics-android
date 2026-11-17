package com.esseanalytics.android.feature.stats

import com.esseanalytics.android.core.network.dto.*
import org.junit.Assert.*
import org.junit.Test

class MetricsUnknownPolicyTest {
    private fun known(likes: Long, comments: Long) = GroupStatsSlotDto("yt", views = 10, likes = likes, comments = comments)
    private fun unknown() = GroupStatsSlotDto("ig", views = 10, likes = null, comments = null, likesAvailable = false, commentsAvailable = false)
    private fun item(vararg slots: Pair<String, GroupStatsSlotDto>) = GroupStatsItemDto("f", "a.mp4", platforms = mapOf(*slots))

    @Test
    fun `un me gusta desconocido no gana ni se suma como cero`() {
        val item = item("youtube" to known(3, 1), "instagram" to unknown())
        assertEquals(setOf("youtube"), item.winners(StatsMetric.LIKES))
        assertNull(item.total(StatsMetric.LIKES))
        assertEquals(20L, item.total(StatsMetric.VIEWS))
    }

    @Test
    fun `si solo hay datos desconocidos no hay ganador ni total`() {
        val item = item("instagram" to unknown())
        assertEquals(emptySet<String>(), item.winners(StatsMetric.LIKES))
        assertNull(item.total(StatsMetric.COMMENTS))
    }

    @Test
    fun `un cero explicito sigue siendo cero`() {
        val item = item("youtube" to known(0, 0))
        assertEquals(0L, item.total(StatsMetric.LIKES))
        assertEquals(emptySet<String>(), item.winners(StatsMetric.LIKES))
    }

    @Test
    fun `el formato antiguo sigue leyendose como numeros`() {
        assertEquals(2L, GroupStatsSlotDto("yt", views = 1, likes = 2, comments = 0).value(StatsMetric.LIKES))
    }
}
