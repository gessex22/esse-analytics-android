package com.esseanalytics.android.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.GroupStatsSlotDto
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import com.esseanalytics.android.core.model.Platform

private val dashboardPlatforms = Platform.publishable

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    when (val current = state) {
        DashboardUiState.Loading -> Box(modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is DashboardUiState.Error -> DashboardMessage("No se pudo cargar", current.message)
        is DashboardUiState.Success -> LazyColumn(
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                val history = current.data.latestHistory
                val statsItem = history?.fileName?.let { name -> current.data.items.firstOrNull { it.fileName == name } }
                    ?: if (history == null) current.data.items.firstOrNull() else null
                LatestVideoCard(history, statsItem)
            }
            item { PlatformPodium(current.data.items) }
            item { UpcomingCard(current.data.calendar) }
        }
    }
}

@Composable
private fun LatestVideoCard(history: UploadHistoryItemDto?, item: GroupStatsItemDto?) {
    DashboardCard {
        DashboardHeader("Último video publicado", "Rendimiento comparado entre plataformas", Icons.Outlined.QueryStats)
        if (history == null && item == null) {
            DashboardMessage("Todavía no hay videos publicados", "Cuando se sincronicen tus publicaciones aparecerán aquí.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thumbnail = remember(item) { item?.let { dashboardPlatforms.firstNotNullOfOrNull { platform -> it.platforms[platform.apiValue]?.thumbnail } } }
                if (thumbnail.isNullOrBlank()) {
                    Box(Modifier.size(86.dp, 116.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    AsyncImage(model = thumbnail, contentDescription = null, modifier = Modifier.size(86.dp, 116.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(history?.fileName ?: history?.title ?: item?.fileName.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3)
                    Text(history?.publishedAt ?: item?.fecha_creacion.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 5.dp)) {
                        Metric(Icons.Outlined.Visibility, item?.total { it.views } ?: 0, emphasized = true)
                        Metric(Icons.Outlined.Favorite, item?.total { it.likes } ?: 0)
                        Metric(Icons.Outlined.ChatBubble, item?.total { it.comments } ?: 0)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { dashboardPlatforms.forEach { platform -> PlatformMetricRow(platform, item?.platforms?.get(platform.apiValue)) } }
        }
    }
}

@Composable
private fun PlatformPodium(items: List<GroupStatsItemDto>) {
    val ranking = remember(items) { dashboardPlatforms.map { platform -> platform to items.sumOf { it.platforms[platform.apiValue]?.views ?: 0 } }.sortedByDescending { it.second } }
    DashboardCard {
        DashboardHeader("Plataforma líder", "Podio de vistas recientes", Icons.Outlined.QueryStats)
        if (ranking.none { it.second > 0 }) {
            Text("Pendiente de datos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                PodiumPlace(ranking.getOrNull(1), 2, 42.dp)
                PodiumPlace(ranking.getOrNull(0), 1, 58.dp)
                PodiumPlace(ranking.getOrNull(2), 3, 42.dp)
            }
        }
    }
}

@Composable
private fun UpcomingCard(configs: List<CalendarConfigDto>) {
    DashboardCard {
        DashboardHeader("Próximas publicaciones", "Tu agenda más cercana", Icons.Outlined.CalendarMonth)
        val upcoming = configs.filter { it.nextVideo != null }.take(3)
        if (upcoming.isEmpty()) Text("No hay publicaciones próximas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { upcoming.forEach { config ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp)) {
                PlatformMetricLogo(Platform.fromApiValue(config.platform))
                Text(config.nextVideo?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(config.platform.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
    }
}

@Composable
private fun DashboardCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }

@Composable
private fun DashboardHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) } }

@Composable
private fun DashboardMessage(title: String, message: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun PlatformMetricRow(platform: Platform, slot: GroupStatsSlotDto?) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(platformColor(platform).copy(alpha = .10f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) { PlatformMetricLogo(platform); Text(platform.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); if (slot == null) Text("Pendiente de datos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else { Metric(Icons.Outlined.Visibility, slot.views); Metric(Icons.Outlined.Favorite, slot.likes); Metric(Icons.Outlined.ChatBubble, slot.comments) } } }

@Composable
private fun PlatformMetricLogo(platform: Platform?) { if (platform == null) Spacer(Modifier.size(18.dp)) else when (platform) { Platform.YOUTUBE -> Icon(PlatformIcons.YoutubeLogo, null, tint = YoutubeRed, modifier = Modifier.size(18.dp)); Platform.INSTAGRAM -> Icon(PlatformIcons.InstagramLogo, null, tint = InstagramPurple, modifier = Modifier.size(18.dp)); Platform.TIKTOK -> Icon(PlatformIcons.TiktokLogo, null, tint = TiktokPink, modifier = Modifier.size(18.dp)); else -> Spacer(Modifier.size(18.dp)) } }

@Composable
private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, emphasized: Boolean = false) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(icon, null, modifier = Modifier.size(13.dp), tint = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant); Text(formatDashboardCount(value), style = MaterialTheme.typography.labelSmall, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal) } }

@Composable
private fun RowScope.PodiumPlace(entry: Pair<Platform, Int>?, place: Int, size: androidx.compose.ui.unit.Dp) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) { if (entry != null) { Box(Modifier.size(size).background(platformColor(entry.first).copy(alpha = .14f), RoundedCornerShape(if (place == 1) 16.dp else 12.dp)), contentAlignment = Alignment.Center) { PlatformMetricLogo(entry.first) }; Text("${place}º ${entry.first.displayName}", style = MaterialTheme.typography.labelSmall, fontWeight = if (place == 1) FontWeight.SemiBold else FontWeight.Medium); Text(formatDashboardCount(entry.second), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) } } }

private fun platformColor(platform: Platform): Color = when (platform) { Platform.YOUTUBE -> YoutubeRed; Platform.INSTAGRAM -> InstagramPurple; Platform.TIKTOK -> TiktokPink; else -> Color.Gray }
private fun GroupStatsItemDto.total(selector: (GroupStatsSlotDto) -> Int): Int = dashboardPlatforms.sumOf { platforms[it.apiValue]?.let(selector) ?: 0 }
private fun formatDashboardCount(value: Int): String = when { value >= 1_000_000 -> "${value / 1_000_000}.${(value / 100_000) % 10}M"; value >= 1_000 -> "${value / 1_000}.${(value / 100) % 10}K"; else -> value.toString() }
