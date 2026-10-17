package com.esseanalytics.android.feature.stats

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.formatCreatorMetric
import com.esseanalytics.android.core.network.dto.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun platformName(p: String) = when(p) { "youtube" -> "YouTube"; "instagram" -> "Instagram"; "tiktok" -> "TikTok"; "facebook" -> "Facebook"; else -> p }
@Composable internal fun platformColor(p: String) = com.esseanalytics.android.core.designsystem.theme.studioPlatformColor(p)
internal fun number(value: Long?): String = value?.let(::formatCreatorMetric) ?: "—"
internal fun signed(value: Long?): String = value?.let { (if(it >= 0) "+" else "") + number(it) } ?: "—"
internal fun dateLabel(value: String?): String = runCatching {
    val date = Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es")))
}.getOrElse { value?.take(10) ?: "Fecha no disponible" }
internal fun relativeTime(value: String?): String = runCatching {
    val seconds = Duration.between(Instant.parse(value), Instant.now()).seconds.coerceAtLeast(0)
    when { seconds < 60 -> "ahora"; seconds < 3600 -> "hace ${seconds / 60} min"; seconds < 86400 -> "hace ${seconds / 3600} h"; else -> "hace ${seconds / 86400} d" }
}.getOrDefault("Fecha no disponible")

@Composable
internal fun StatsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
/** Content participates in height measurement during both opening and closing. */
@Composable
internal fun StudioAccordion(expanded: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(expanded, enter = expandVertically(tween(280), expandFrom = Alignment.Top) + fadeIn(), exit = shrinkVertically(tween(280), shrinkTowards = Alignment.Top) + fadeOut()) { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp))) { content() } }
}
@Composable
internal fun VideoThumbnail(item: GroupStatsItemDto?, url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("Video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetricTotals(item: GroupStatsItemDto?, active: StatsMetric? = null, delta: Boolean = false) {
    val accessible = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = if (accessible) maxWidth else (maxWidth - 16.dp) / 3
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsMetric.entries.forEach { metric ->
                Column(Modifier.width(cellWidth).background(if (active == metric) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(12.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(number(item?.total(metric)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (active == metric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (delta) Text(item?.last24Hours?.value(metric)?.let { "${signed(it)} · ≈24 h" } ?: "24 h sin datos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (item?.metricsPartial == true) Text("Totales parciales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable
internal fun PlatformMetricTable(item: GroupStatsItemDto, only: StatsMetric? = null) {
    if (only != null) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            statsPlatforms.forEach { p ->
                val winner = p in item.winners(only)
                Column(Modifier.width(100.dp).background(if (winner) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(8.dp).semantics { if (winner) stateDescription = "Ganadora" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(platformName(p), style = MaterialTheme.typography.labelSmall)
                    Text(number(item.platforms[p]?.value(only).takeUnless { item.metricsAvailable == false }), fontWeight = if(winner) FontWeight.Bold else FontWeight.Normal, color = if(winner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    val metrics = only?.let { listOf(it) } ?: StatsMetric.entries
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    Column(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = (300 * fontScale.coerceAtLeast(1f)).dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (only == null) Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.2f)); metrics.forEach { Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 2) }
        }
        statsPlatforms.forEach { p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(platformName(p), Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                metrics.forEach { m ->
                    val winner = p in item.winners(m)
                    Text(number(item.platforms[p]?.value(m).takeUnless { item.metricsAvailable == false }),
                        Modifier.weight(1f).background(if (winner) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(6.dp)).padding(4.dp)
                            .semantics { if (winner) stateDescription = "Mayor ${m.label}" },
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                        color = if (winner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable
internal fun VideoStatsRow(item: GroupStatsItemDto, thumbnail: String?, active: StatsMetric? = null, delta: Boolean = false, onClick: () -> Unit) {
    StatsCard(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VideoThumbnail(item, thumbnail, Modifier.size(62.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(dateLabel(item.fecha_creacion), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
        }
        MetricTotals(item, active, delta)
        active?.let { PlatformMetricTable(item, it) }
    }
}
