package com.esseanalytics.android.feature.stats
import com.esseanalytics.android.core.network.dto.*
import org.junit.Assert.*
import org.junit.Test

class StatsPolicyTest {
    private fun slot(views: Int = 5, likes: Int = 2, comments: Int = 1) = GroupStatsSlotDto("id", views = views.toLong(), likes = likes.toLong(), comments = comments.toLong())
    private fun video(id: String = "a", slots: Map<String,GroupStatsSlotDto> = mapOf("youtube" to slot())) = GroupStatsItemDto(id,"same.mp4",platforms = slots)
    private fun event(id: String, stats: GroupStatsItemDto? = null, content: String? = null) = UploadHistoryItemDto(id,"youtube",id,publishedAt = "2026-10-17T12:00:00Z",fileName = "same.mp4",contentId = content,stats = stats)
    @Test fun `totals ignore Facebook and use wide accumulators`() {
        val item = video(slots = mapOf("youtube" to slot(Int.MAX_VALUE), "instagram" to slot(Int.MAX_VALUE), "facebook" to slot(99)))
        assertEquals(4_294_967_294L,item.total(StatsMetric.VIEWS))
    }
    @Test fun `unavailable metrics are unknown and cannot win`() {
        val item = video().copy(metricsAvailable = false)
        assertNull(item.total(StatsMetric.VIEWS)); assertTrue(item.winners(StatsMetric.VIEWS).isEmpty())
    }
    @Test fun `observed zero is real zero without a winner`() {
        val item = video(slots = mapOf("youtube" to slot(0)))
        assertEquals(0L,item.total(StatsMetric.VIEWS)); assertTrue(item.winners(StatsMetric.VIEWS).isEmpty())
    }
    @Test fun `metric chooses independent winners and all positive ties`() {
        val item = video(slots = mapOf("youtube" to slot(12,1), "instagram" to slot(12,8), "tiktok" to slot(2,4)))
        assertEquals(setOf("youtube","instagram"),item.winners(StatsMetric.VIEWS)); assertEquals(setOf("instagram"),item.winners(StatsMetric.LIKES))
    }
    @Test fun `active metric sorts with unknown last`() {
        val a=video("a").copy(metricsAvailable=false); val b=video("b"); val c=video("c",mapOf("youtube" to slot(20)))
        assertEquals(listOf("c","b","a"), sortedVideos(listOf(a,b,c),StatsMetric.VIEWS).map { it.fileId })
    }
    @Test fun `same names never merge and different files stay separate`() {
        assertEquals(2,groupRecentHistory(listOf(event("1",video("a")),event("2",video("b")))).size)
        assertEquals(2,groupRecentHistory(listOf(event("1"),event("2"))).size)
    }
    @Test fun `proven file identity merges uploads without summing duplicate snapshots`() {
        val result = groupRecentHistory(listOf(event("1",video()),event("2",video())))
        assertEquals(1,result.size); assertEquals(5L,result.single().stats?.total(StatsMetric.VIEWS)); assertEquals(1,result.single().lastEventIndex)
    }
    @Test fun `canonical identity joins enriched and unenriched upload events transitively`() {
        val a=video().copy(contentId="canonical")
        val result=groupRecentHistory(listOf(event("1",a),event("2",content="canonical"),event("3",video())))
        assertEquals(1,result.size); assertEquals(2,result.single().lastEventIndex)
    }
    @Test fun `selection keeps exact platform identity and origin`() {
        val s=event("remote").detailSelection("Calendario")
        assertEquals("youtube",s.platform); assertEquals("remote",s.platformId); assertEquals("Calendario",s.origin); assertNull(s.fileId)
    }
    @Test fun `title uses caption before stable platform order and filename`() {
        val item=video(slots=mapOf("tiktok" to slot().copy(title="TikTok"),"youtube" to slot().copy(title="YouTube")))
        assertEquals("YouTube",item.displayTitle); assertEquals("Caption",item.copy(title="Caption").displayTitle)
    }
    @Test fun `mixed pending causes preserve failures and other missing destinations`() {
        val cause=DashboardPendingActionDto("a",title="A",cause="failed",missingPlatforms=listOf("youtube","instagram"),failedPlatforms=listOf("youtube")).causeText()
        assertTrue(cause.contains("Subida fallida · YouTube")); assertTrue(cause.contains("Falta publicar · Instagram"))
    }
}
