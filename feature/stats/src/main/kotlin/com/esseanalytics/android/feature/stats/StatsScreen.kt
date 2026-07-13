package com.esseanalytics.android.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.GroupStatsSlotDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Mismo dato y misma vista que frontend/src/components/StatsView.tsx: los
// últimos videos ya vinculados en las 3 plataformas, con un donut de SOLO
// views (no likes/comments — responde "qué plataforma tuvo mayor alcance")
// dibujado a mano con Canvas, ver el plan ("Charts: Canvas a mano, un solo
// gráfico no amerita librería"). Mismos colores de marca que el resto de la
// app (designsystem/theme/Color.kt).
private val PLATFORM_ORDER = Platform.publishable

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> Color.Gray
}

private fun platformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Los últimos videos publicados en las 3 redes, comparados lado a lado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
            }
        }

        when (val current = state) {
            is StatsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is StatsUiState.Error -> PlaceholderScreen(
                title = "No se pudo cargar",
                note = current.message,
                icon = Icons.Outlined.ErrorOutline,
                iconTint = MaterialTheme.colorScheme.error,
            )

            is StatsUiState.Success -> if (current.items.isEmpty()) {
                PlaceholderScreen(
                    title = "Todavía no hay videos matcheados en las 3 redes",
                    note = "Completá los links en Ajustes → Sincronización → \"Emparejar entre plataformas\".",
                    icon = Icons.Outlined.QueryStats,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.items, key = { it.fileId }) { item -> GroupStatsCard(item) }
                }
            }
        }
    }
}

@Composable
private fun GroupStatsCard(item: GroupStatsItemDto) {
    val totalViews = PLATFORM_ORDER.sumOf { item.platforms[it.apiValue]?.views ?: 0 }
    val totalLikes = PLATFORM_ORDER.sumOf { item.platforms[it.apiValue]?.likes ?: 0 }
    val totalComments = PLATFORM_ORDER.sumOf { item.platforms[it.apiValue]?.comments ?: 0 }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        formatDate(item.fecha_creacion),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatBadge(Icons.Outlined.Visibility, formatNum(totalViews))
                        StatBadge(Icons.Outlined.Favorite, formatNum(totalLikes))
                        StatBadge(Icons.Outlined.ChatBubble, formatNum(totalComments))
                    }
                }
                ViewsDonut(platforms = item.platforms, modifier = Modifier.padding(start = 8.dp))
            }

            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PLATFORM_ORDER.forEach { platform ->
                    val slot = item.platforms[platform.apiValue] ?: return@forEach
                    PlatformStatsRow(platform, slot)
                }
            }
        }
    }
}

@Composable
private fun StatBadge(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PlatformStatsRow(platform: Platform, slot: GroupStatsSlotDto) {
    val uriHandler = LocalUriHandler.current
    val color = platformColor(platform)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(platformShortLabel(platform), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatBadge(Icons.Outlined.Visibility, formatNum(slot.views))
            StatBadge(Icons.Outlined.Favorite, formatNum(slot.likes))
            StatBadge(Icons.Outlined.ChatBubble, formatNum(slot.comments))
        }
        if (slot.platformUrl.isNotBlank()) {
            Text(
                "Ver",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { uriHandler.openUri(slot.platformUrl) },
            )
        }
    }
}

// Donut de SOLO views -- responde "qué plataforma tuvo mayor alcance", igual
// que ViewsDonut en frontend/src/components/StatsView.tsx: anillo con
// separador entre segmentos, centro con la plataforma líder + su %.
@Composable
private fun ViewsDonut(platforms: Map<String, GroupStatsSlotDto>, modifier: Modifier = Modifier) {
    val values = PLATFORM_ORDER.map { it to (platforms[it.apiValue]?.views ?: 0) }
    val total = values.sumOf { it.second }
    val trackColor = MaterialTheme.colorScheme.outline

    if (total == 0) {
        Box(
            modifier = modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = "Todavía sin vistas",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        return
    }

    val leader = values.maxByOrNull { it.second }!!
    val leaderPct = ((leader.second.toFloat() / total) * 100).roundToInt()
    val leaderColor = platformColor(leader.first)

    Box(modifier = modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val gapDegrees = 6f

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )

            var startAngle = -90f
            values.filter { it.second > 0 }.forEach { (platform, v) ->
                val sweep = (v.toFloat() / total) * 360f
                val drawnSweep = (sweep - gapDegrees).coerceAtLeast(1f)
                drawArc(
                    color = platformColor(platform),
                    startAngle = startAngle,
                    sweepAngle = drawnSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                platformShortLabel(leader.first),
                style = MaterialTheme.typography.labelSmall,
                color = leaderColor,
                fontWeight = FontWeight.Bold,
            )
            Text("$leaderPct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatNum(n: Int): String = when {
    n >= 1_000_000 -> "${(n / 1_000_000.0).let { "%.1f".format(it) }.removeSuffix(".0")}M"
    n >= 1_000 -> "${(n / 1_000.0).let { "%.1f".format(it) }.removeSuffix(".0")}K"
    else -> n.toString()
}

private fun formatDate(iso: String): String = runCatching {
    val instant = Instant.parse(iso)
    DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault(iso)
