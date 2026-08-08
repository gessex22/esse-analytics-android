package com.esseanalytics.android.feature.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
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
import kotlin.math.pow

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

private fun platformIcon(platform: Platform): ImageVector? = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> null
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var showExpandedChart by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsFilter.entries) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Text(
                statsDescription(filter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(
                onClick = viewModel::refresh,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Actualizar", modifier = Modifier.padding(start = 4.dp))
            }
        }

        // Crossfade en vez de un when() directo -- sin esto, cambiar de pestaña
        // cortaba en seco de un estado al otro (vacío -> spinner -> contenido),
        // se sentía como una recarga brusca de toda la pantalla.
        Crossfade(targetState = state, label = "statsContent") { targetState ->
            when (targetState) {
                is StatsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is StatsUiState.Error -> PlaceholderScreen(
                    title = "No se pudo cargar",
                    note = targetState.message,
                    icon = Icons.Outlined.ErrorOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                )

                is StatsUiState.Success -> if (targetState.items.isEmpty()) {
                    PlaceholderScreen(
                        title = statsEmptyTitle(filter),
                        note = statsEmptyNote(filter),
                        icon = Icons.Outlined.QueryStats,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Mismo orden que StatsView.swift (iOS): gráfico primero,
                        // totales debajo -- pedido explícito del usuario (antes en
                        // iOS los totales estaban arriba del gráfico).
                        item(key = "chart") {
                            StatsChartCard(targetState.items, onExpand = { showExpandedChart = true })
                        }
                        item(key = "totals") { StatsTotalsCard(targetState.items) }
                        items(targetState.items, key = { it.fileId }) { item ->
                            GroupStatsCard(item, thumbnailUrl = viewModel.thumbnailUrl(item))
                        }
                    }

                    if (showExpandedChart) {
                        ExpandedStatsChartDialog(items = targetState.items, onDismiss = { showExpandedChart = false })
                    }
                }
            }
        }
    }
}

private fun statsDescription(filter: StatsFilter): String = when (filter) {
    StatsFilter.COMPARED -> "Los últimos videos publicados en las 3 redes, comparados lado a lado."
    else -> "Los últimos videos publicados en ${filter.label}."
}

private fun statsEmptyTitle(filter: StatsFilter): String = when (filter) {
    StatsFilter.COMPARED -> "Todavía no hay videos matcheados en las 3 redes"
    else -> "Todavía no hay videos publicados en ${filter.label}"
}

private fun statsEmptyNote(filter: StatsFilter): String = when (filter) {
    StatsFilter.COMPARED -> "Completá los links en Ajustes → Sincronización → \"Emparejar entre plataformas\"."
    else -> "Cuando se sincronicen publicaciones de ${filter.label}, aparecerán acá."
}

@Composable
private fun GroupStatsCard(item: GroupStatsItemDto, thumbnailUrl: String?) {
    val totalViews = PLATFORM_ORDER.sumOf { item.platforms[it.apiValue]?.views ?: 0 }
    val totalLikes = PLATFORM_ORDER.sumOf { item.platforms[it.apiValue]?.likes ?: 0 }
    val totalComments = PLATFORM_ORDER.sumOf { item.platforms[it.apiValue]?.comments ?: 0 }

    // Colapsado por defecto -- mismo criterio que GroupStatsCard en iOS: el
    // desglose por plataforma (views/likes/comments individuales + link) solo
    // se muestra si el usuario lo pide, la fila de arriba ya resume lo
    // esencial (miniatura + totales + dona).
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "statsCardChevron")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatsThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    platformThumbnailUrl = anyPlatformThumbnailUrl(item),
                    modifier = Modifier.padding(end = 12.dp),
                )
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
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Contraer" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .rotate(chevronRotation),
                )
            }

            AnimatedVisibility(visible = isExpanded) {
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
}

// El thumbnail REAL de alguna plataforma (YouTube/IG/TikTok, ya viaja en la
// respuesta de group-stats) como última red de contención antes del
// placeholder genérico -- no depende de tener match en Biblioteca remota.
private fun anyPlatformThumbnailUrl(item: GroupStatsItemDto): String? =
    PLATFORM_ORDER.firstNotNullOfOrNull { item.platforms[it.apiValue]?.thumbnail?.takeIf { url -> url.isNotBlank() } }

// Mirror de GroupStatsCard.thumbnail en iOS: miniatura puntual de Biblioteca
// remota (thumbnailUrl, resuelta server-side vía remoteLibraryVideoId -- ver
// StatsViewModel.thumbnailUrl) primero, sin depender de traer un batch
// aparte de la cola remota; si el item no tiene match ahí, cae al thumbnail
// real de alguna plataforma; si tampoco hay, ícono genérico.
@Composable
private fun StatsThumbnail(thumbnailUrl: String?, platformThumbnailUrl: String?, modifier: Modifier = Modifier) {
    val resolvedUrl = thumbnailUrl ?: platformThumbnailUrl
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedUrl != null) {
            AsyncImage(
                model = resolvedUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
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
            val icon = platformIcon(platform)
            if (icon != null) {
                Icon(icon, contentDescription = platformShortLabel(platform), tint = color, modifier = Modifier.size(13.dp))
            } else {
                Text(platformShortLabel(platform), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
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
            val icon = platformIcon(leader.first)
            if (icon != null) {
                Icon(icon, contentDescription = platformShortLabel(leader.first), tint = leaderColor, modifier = Modifier.size(14.dp))
            } else {
                Text(
                    platformShortLabel(leader.first),
                    style = MaterialTheme.typography.labelSmall,
                    color = leaderColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("$leaderPct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun chartAxisMax(rawMax: Int): Int {
    if (rawMax <= 0) return 1
    val padded = rawMax * 1.15
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(padded))).toInt().coerceAtLeast(1)
    val normalized = padded / magnitude
    val step = when {
        normalized <= 1.0 -> 0.2
        normalized <= 2.0 -> 0.5
        normalized <= 5.0 -> 1.0
        else -> 2.0
    } * magnitude
    return (kotlin.math.ceil(padded / step) * step).roundToInt().coerceAtLeast(1)
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

private fun parseDateOrEpoch(iso: String): Instant = runCatching { Instant.parse(iso) }.getOrDefault(Instant.EPOCH)

// Punto de datos para el gráfico de vistas acumuladas -- mirror de
// AccumulatedViewsPoint en iOS (StatsView.swift): una serie por plataforma,
// sumando views video a video en orden cronológico. El eje X es por video
// (V1..Vn), no por fecha -- con solo 5 videos, una fecha real deja huecos o
// aprieta los puntos según cuán separados estén en el tiempo.
private data class ViewsPoint(val platform: Platform, val videoLabel: String, val views: Int)

private fun viewsData(items: List<GroupStatsItemDto>): List<ViewsPoint> {
    val sorted = items.sortedBy { parseDateOrEpoch(it.fecha_creacion) }
    val points = mutableListOf<ViewsPoint>()
    for (platform in PLATFORM_ORDER) {
        sorted.forEachIndexed { index, item ->
            points += ViewsPoint(platform, "V${index + 1}", item.platforms[platform.apiValue]?.views ?: 0)
        }
    }
    return points
}

private fun totalMetric(items: List<GroupStatsItemDto>, selector: (GroupStatsSlotDto) -> Int): Int =
    items.sumOf { item -> PLATFORM_ORDER.sumOf { platform -> item.platforms[platform.apiValue]?.let(selector) ?: 0 } }

// Card propia con los totales -- mismo lenguaje visual que GroupStatsCard,
// mirror de StatsTotalsCard en iOS. Va DEBAJO del gráfico (StatsChartCard),
// no arriba -- orden pedido explícitamente, distinto del primer diseño.
@Composable
private fun StatsTotalsCard(items: List<GroupStatsItemDto>) {
    val totalViews = totalMetric(items) { it.views }
    val totalLikes = totalMetric(items) { it.likes }
    val totalComments = totalMetric(items) { it.comments }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TotalStat(Icons.Outlined.Visibility, formatNum(totalViews), "Vistas")
            TotalStat(Icons.Outlined.Favorite, formatNum(totalLikes), "Likes")
            TotalStat(Icons.Outlined.ChatBubble, formatNum(totalComments), "Comentarios")
        }
    }
}

@Composable
private fun TotalStat(icon: ImageVector, value: String, label: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Card propia con el gráfico -- mirror de StatsChartCard en iOS. Se mueve con
// el resto del scroll, no queda fijo arriba.
@Composable
private fun StatsChartCard(items: List<GroupStatsItemDto>, onExpand: () -> Unit) {
    val chartData = remember(items) { viewsData(items) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Vistas por video y plataforma",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onExpand) {
                    Icon(Icons.Outlined.Fullscreen, contentDescription = "Expandir estadísticas")
                }
            }
            ViewsChart(chartData, modifier = Modifier.fillMaxWidth().height(180.dp))
        }
    }
}

// Dibujado a mano con Canvas -- mismo criterio que ViewsDonut arriba, un
// gráfico de líneas simple no amerita sumar una librería de charts entera.
// Ejes: Y en notación compacta (1K/2K/1M) a la izquierda con grid horizontal,
// X implícito (un punto por video, sin labels -- con 5 videos el eje X real
// no aporta nada que el orden ya no diga). Leyenda de plataformas debajo.
@Composable
private fun ViewsChart(data: List<ViewsPoint>, modifier: Modifier = Modifier, yAxisTickCount: Int = 4) {
    if (data.isEmpty()) return

    val grouped = remember(data) { data.groupBy { it.platform } }
    val videoCount = remember(data) { data.map { it.videoLabel }.distinct().size }
    val maxViews = remember(data) { chartAxisMax(data.maxOfOrNull { it.views } ?: 0) }
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val platformColors = PLATFORM_ORDER.associateWith { platformColor(it) }

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val yAxisWidth = 34.dp.toPx()
            val chartLeft = yAxisWidth
            val chartRight = size.width
            val chartTop = 6.dp.toPx()
            val chartBottom = size.height - 6.dp.toPx()
            val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
            val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

            for (i in 0..yAxisTickCount) {
                val fraction = i.toFloat() / yAxisTickCount
                val y = chartBottom - fraction * chartHeight
                drawLine(gridColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1.dp.toPx())
                val label = formatNum((maxViews * fraction).roundToInt())
                val measured = textMeasurer.measure(AnnotatedString(label), style = labelStyle)
                drawText(measured, topLeft = Offset(0f, (y - measured.size.height / 2).coerceAtLeast(0f)))
            }

            if (videoCount > 0) {
                fun xFor(index: Int) = if (videoCount == 1) chartLeft + chartWidth / 2f else chartLeft + (index.toFloat() / (videoCount - 1)) * chartWidth
                fun yFor(views: Int) = chartBottom - (views.toFloat() / maxViews) * chartHeight

                grouped.forEach { (platform, points) ->
                    val sortedPoints = points.sortedBy { it.videoLabel }
                    val color = platformColors.getValue(platform)
                    if (sortedPoints.size > 1) {
                        val path = Path()
                        sortedPoints.forEachIndexed { index, point ->
                            val x = xFor(index)
                            val y = yFor(point.views)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    sortedPoints.forEachIndexed { index, point ->
                        drawCircle(color, radius = 3.dp.toPx(), center = Offset(xFor(index), yFor(point.views)))
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PLATFORM_ORDER.forEach { platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(platformColor(platform)),
                    )
                    Text(
                        platformShortLabel(platform),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

// Mismo gráfico a pantalla completa -- mirror de ExpandedStatsChartView en
// iOS ("Expandir" al estilo YT Studio). Dialog fullscreen en vez de una ruta
// de navegación nueva: es un detalle transitorio del mismo dato que ya está
// en pantalla, no una sección propia de la app.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedStatsChartDialog(items: List<GroupStatsItemDto>, onDismiss: () -> Unit) {
    val chartData = remember(items) { viewsData(items) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TopAppBar(
                title = { Text("Estadísticas") },
                actions = { TextButton(onClick = onDismiss) { Text("Listo") } },
            )
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    "Vistas por video y plataforma",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ViewsChart(
                    chartData,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    yAxisTickCount = 6,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PLATFORM_ORDER.forEach { platform ->
                        val views = items.sumOf { it.platforms[platform.apiValue]?.views ?: 0 }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(platformColor(platform).copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = platformIcon(platform)
                            if (icon != null) {
                                Icon(icon, contentDescription = platformShortLabel(platform), tint = platformColor(platform), modifier = Modifier.size(18.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(platformColor(platform).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        platformShortLabel(platform).take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = platformColor(platform),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                platformShortLabel(platform),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            )
                            Text(
                                formatNum(views),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
