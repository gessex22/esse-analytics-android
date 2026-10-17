package com.esseanalytics.android.feature.stats

import com.esseanalytics.android.core.network.dto.*

internal val statsPlatforms = listOf("youtube", "instagram", "tiktok")
enum class StatsMetric(val label: String) { VIEWS("Vistas"), LIKES("Me gusta"), COMMENTS("Comentarios") }
internal fun GroupStatsSlotDto.value(metric: StatsMetric): Long = when(metric) {
    StatsMetric.VIEWS -> views.toLong(); StatsMetric.LIKES -> likes.toLong(); StatsMetric.COMMENTS -> comments.toLong()
}
internal fun GroupStatsItemDto.total(metric: StatsMetric): Long? =
    if (metricsAvailable == false || platforms.keys.none { it in statsPlatforms }) null
    else platforms.filterKeys { it in statsPlatforms }.values.sumOf { it.value(metric) }
internal fun GroupStatsItemDto.winners(metric: StatsMetric): Set<String> {
    if (metricsAvailable == false) return emptySet()
    val slots = platforms.filterKeys { it in statsPlatforms }
    val maximum = slots.values.maxOfOrNull { it.value(metric) } ?: 0
    return if (maximum <= 0) emptySet() else slots.filterValues { it.value(metric) == maximum }.keys
}
internal val GroupStatsItemDto.displayTitle: String get() = title?.takeIf(String::isNotBlank)
    ?: statsPlatforms.firstNotNullOfOrNull { platforms[it]?.title?.takeIf(String::isNotBlank) }
    ?: fileName.substringBeforeLast('.').ifBlank { "Video sin título" }
internal val GroupStatsItemDto.identity: String get() = contentId?.takeIf(String::isNotBlank)?.let { "content:$it" }
    ?: fileId.takeIf(String::isNotBlank)?.let { "file:$it" }
    ?: platforms.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.platformId}" }
internal val GroupStatsItemDto.thumbnail: String? get() = statsPlatforms.firstNotNullOfOrNull { platforms[it]?.thumbnail?.takeIf(String::isNotBlank) }
internal fun VideoDailyGainDto.value(metric: StatsMetric) = when(metric) { StatsMetric.VIEWS -> views; StatsMetric.LIKES -> likes; StatsMetric.COMMENTS -> comments }
internal fun sortedVideos(items: List<GroupStatsItemDto>, metric: StatsMetric) = items.sortedWith(
    compareByDescending<GroupStatsItemDto> { it.total(metric) ?: Long.MIN_VALUE }.thenBy { it.identity })

/** Group only proven canonical identities, never titles, names or local Room row ids. */
internal fun groupRecentHistory(events: List<UploadHistoryItemDto>): List<RecentVideo> {
    val parent = mutableMapOf<String, String>()
    fun root(key: String): String { val p = parent.getOrPut(key) { key }; return if (p == key) key else root(p).also { parent[key] = it } }
    fun keys(e: UploadHistoryItemDto): List<String> = listOfNotNull(
        e.stats?.fileId?.takeIf(String::isNotBlank)?.let { "file:$it" },
        (e.stats?.contentId ?: e.contentId)?.takeIf(String::isNotBlank)?.let { "content:$it" },
    ).ifEmpty { listOf("event:${e.id}") }
    events.forEach { e -> val keys = keys(e); val first = root(keys.first()); keys.drop(1).forEach { parent[root(it)] = first } }
    return events.withIndex().groupBy { root(keys(it.value).first()) }.map { (key, indexed) ->
        val rows = indexed.map { it.value }
        val known = rows.mapNotNull { it.stats }.sortedByDescending { it.platforms.size }
        // Whole canonical snapshot, not a sum of repeated upload events.
        RecentVideo(key, rows.first(), known.firstOrNull(), indexed.maxOf { it.index })
    }
}
internal data class RecentVideo(val key: String, val event: UploadHistoryItemDto, val stats: GroupStatsItemDto?, val lastEventIndex: Int)

data class VideoStatsSelection(
    val platform: String? = null, val platformId: String? = null,
    val fileId: String? = null, val fileName: String? = null,
    val seed: GroupStatsItemDto? = null, val origin: String = "Datos",
)
internal fun GroupStatsItemDto.selection(origin: String = "Datos"): VideoStatsSelection {
    val slot = statsPlatforms.firstNotNullOfOrNull { p -> platforms[p]?.let { p to it } }
    return VideoStatsSelection(slot?.first, slot?.second?.platformId, fileId.takeIf(String::isNotBlank), fileName.takeIf(String::isNotBlank), this, origin)
}
fun DashboardVideoDto.detailSelection() = VideoStatsSelection(platform, platformId, fileId, fileName, origin = "Inicio")
fun UploadHistoryItemDto.detailSelection(origin: String = "Calendario") = VideoStatsSelection(platform, platformId, stats?.fileId, fileName, stats, origin)

internal fun DashboardPendingActionDto.causeText(): String = buildList {
    if (cause == "draft") add("Borrador sin publicar")
    if (cause == "failed" || failedPlatforms.isNotEmpty()) add("Subida fallida" + failedPlatforms.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ", transform = ::platformName).orEmpty())
    val remaining = missingPlatforms.filterNot { it in failedPlatforms }
    if (remaining.isNotEmpty()) add("Falta publicar · " + remaining.joinToString(transform = ::platformName))
    else if (isEmpty()) add("Cobertura incompleta")
}.joinToString(" · ")
