package com.esseanalytics.android.feature.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.esseanalytics.android.core.designsystem.component.CreatorFloatingHomeActions
import com.esseanalytics.android.core.designsystem.component.CreatorChartBar
import com.esseanalytics.android.core.designsystem.component.CreatorVideoMetricChart
import com.esseanalytics.android.core.designsystem.component.CreatorLoadingBar
import com.esseanalytics.android.core.designsystem.component.CreatorMetric
import com.esseanalytics.android.core.designsystem.component.CreatorMetricSegmentedControl
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.component.CreatorVideoStyle
import com.esseanalytics.android.core.designsystem.component.CreatorVideoSelectionMark
import com.esseanalytics.android.core.designsystem.component.formatCreatorMetric as compact
import com.esseanalytics.android.core.designsystem.component.RefreshErrorBanner
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.GroupStatsSlotDto
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

private val platforms = listOf(Platform.YOUTUBE, Platform.INSTAGRAM, Platform.TIKTOK)
private val platformNames = mapOf(Platform.YOUTUBE to "YouTube", Platform.INSTAGRAM to "Instagram", Platform.TIKTOK to "TikTok")

private fun videoKey(item: GroupStatsItemDto): String = item.fileId.ifBlank { item.fileName }

private fun platformIcon(platform: Platform): ImageVector = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> PlatformIcons.FacebookLogo
}

@Composable
private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> Color(0xFFE65D52)
    Platform.INSTAGRAM -> Color(0xFFB968AA)
    Platform.TIKTOK -> MaterialTheme.colorScheme.onSurface
    Platform.FACEBOOK -> Color(0xFF4267B2)
}

private fun displayTitle(item: GroupStatsItemDto): String =
    item.platforms.values.firstOrNull { it.title.isNotBlank() }?.title?.takeIf { it.isNotBlank() }
        ?: item.fileName.substringBeforeLast('.').ifBlank { "Video sin título" }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
    username: String = "",
    onProfileClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    initialVideoKey: String? = null,
    initialVideoRequest: Long = 0L,
    showFloatingHeader: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(CreatorMetric.VIEWS) }
    var expandedChart by remember { mutableStateOf(false) }
    var expandedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVideo by rememberSaveable(initialVideoKey, initialVideoRequest) { mutableStateOf<String?>(null) }
    var pendingVideoKey by remember { mutableStateOf<String?>(null) }
    var entryResolved by rememberSaveable(initialVideoKey, initialVideoRequest) { mutableStateOf(initialVideoKey.isNullOrBlank()) }
    var entryFound by rememberSaveable(initialVideoKey, initialVideoRequest) { mutableStateOf(false) }
    val success = state as? StatsUiState.Success
    val hasCurrentFilter = success?.loadedFilter == filter
    val items = if (hasCurrentFilter) success?.items.orEmpty() else emptyList()
    val orderedItems = remember(items) { items.sortedByDescending { it.fecha_creacion } }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val loading = state == StatsUiState.Loading || success?.isRefreshing == true
    val selectionOffset = with(LocalDensity.current) { if (showFloatingHeader) -64.dp.roundToPx() else 0 }
    val selectVideo: (String) -> Unit = { key ->
        selectedVideo = key
        expandedVideo = key
        pendingVideoKey = key
    }

    LaunchedEffect(initialVideoKey, initialVideoRequest, filter, success?.loadedFilter, success?.items) {
        // Un enlace desde Inicio entra en Comparadas como antes; cambiar de
        // pestaña sin enlace conserva el filtro y no genera una nueva consulta.
        if (!entryResolved && !initialVideoKey.isNullOrBlank() && filter != StatsFilter.COMPARED) {
            viewModel.setFilter(StatsFilter.COMPARED)
            return@LaunchedEffect
        }
        if (!entryResolved && !initialVideoKey.isNullOrBlank() && success?.loadedFilter == filter) {
            val match = success.items.firstOrNull { videoKey(it) == initialVideoKey || it.fileId == initialVideoKey || it.fileName == initialVideoKey }
            if (match != null) {
                val key = videoKey(match)
                selectVideo(key)
                entryFound = true
            }
            entryResolved = true
        }
    }

    LaunchedEffect(pendingVideoKey, orderedItems, success?.refreshError, entryFound) {
        val key = pendingVideoKey ?: return@LaunchedEffect
        val index = orderedItems.indexOfFirst { videoKey(it) == key }
        if (index >= 0) {
            val noticeCount = if (!initialVideoKey.isNullOrBlank() && entryResolved && !entryFound) 1 else 0
            val errorCount = if (success?.refreshError != null) 1 else 0
            val target = 4 + noticeCount + errorCount + index
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > target }
            listState.animateScrollToItem(target, scrollOffset = selectionOffset)
        }
        pendingVideoKey = null
    }

    CreatorHomeTheme {
        Box(modifier.fillMaxSize()) {
            CreatorStudioBackdrop()
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = viewModel::refresh,
                state = pullState,
                indicator = {},
                modifier = Modifier.fillMaxSize().then(if (showFloatingHeader) Modifier.statusBarsPadding() else Modifier),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = CreatorVideoStyle.screenHorizontalPadding, end = CreatorVideoStyle.screenHorizontalPadding, top = 8.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "title") {
                        Text("Datos", Modifier.padding(horizontal = 8.dp), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFamily = com.esseanalytics.android.core.designsystem.component.CreatorSerif)
                    }
                    item(key = "filters") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatsFilter.entries.forEach { option ->
                                FilterPill(option, selected = filter == option, onClick = { viewModel.setFilter(option) })
                            }
                        }
                    }
                    when {
                        state is StatsUiState.Loading -> statsLoadingContent(filter)
                        state is StatsUiState.Error -> item(key = "error") {
                            StatsError((state as StatsUiState.Error).message, viewModel::refresh)
                        }
                        !hasCurrentFilter -> statsLoadingContent(filter)
                        else -> {
                            if (!initialVideoKey.isNullOrBlank() && entryResolved && !entryFound) {
                                item(key = "initialVideoNotFound") { StatsMessage("Este video no aparece en esta selección.") }
                            }
                            success?.refreshError?.let { error -> item(key = "refreshError") { RefreshErrorBanner(error) } }
                            if (items.isEmpty()) {
                                item(key = "empty") {
                                    StatsMessage(
                                        if (filter == StatsFilter.COMPARED) "Todavía no hay videos comparados" else "Todavía no hay videos en ${filter.label}",
                                        if (filter == StatsFilter.COMPARED) "Vincula publicaciones de las tres plataformas en Sincronización." else "Cuando se sincronicen publicaciones de esta plataforma, aparecerán aquí.",
                                        retry = if (success?.refreshError != null) viewModel::refresh else null,
                                    )
                                }
                            } else {
                                item(key = "chart") {
                                    StatsChartCard(items, filter, metric, selectedVideo, viewModel::thumbnailUrl, selectVideo, { expandedChart = true })
                                }
                                item(key = "summary") { StatsTotals(items, filter, metric, { metric = it }) }
                                items(orderedItems, key = ::videoKey) { item ->
                                    val key = videoKey(item)
                                    StatsVideoRow(
                                        item = item,
                                        selectedMetric = metric,
                                        expanded = expandedVideo == key,
                                        selected = selectedVideo == key,
                                        filter = filter,
                                        thumbnailUrl = viewModel.thumbnailUrl(item),
                                        onClick = {
                                            selectedVideo = key
                                            expandedVideo = if (expandedVideo == key) null else key
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showFloatingHeader) {
                BoxWithConstraints(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp)) {
                    val showUsername = maxWidth >= 360.dp && (LocalDensity.current.fontScale <= 1.2f || maxWidth >= 400.dp)
                    CreatorFloatingHomeActions(
                        username = username,
                        onNotificationsClick = onNotificationClick,
                        onProfileClick = onProfileClick,
                        showUsername = showUsername,
                    )
                }
            }
            CreatorLoadingBar(
                isLoading = loading,
                pullProgress = pullState.distanceFraction,
                modifier = Modifier.align(Alignment.TopCenter)
                    .then(if (showFloatingHeader) Modifier.statusBarsPadding() else Modifier)
                    .fillMaxWidth(),
            )
            if (expandedChart && items.isNotEmpty()) {
                ExpandedStatsChart(items, filter, metric, selectedVideo, viewModel::thumbnailUrl, { metric = it }, {
                    expandedChart = false
                    selectVideo(it)
                }, { expandedChart = false })
            }
        }
    }
}

private fun LazyListScope.statsLoadingContent(filter: StatsFilter) {
    item(key = "chart") {
        val colors = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxWidth().height(280.dp).semantics { contentDescription = "Cargando datos de ${filter.label}" },
            shape = RoundedCornerShape(26.dp), color = colors.surface.copy(alpha = 0.92f),
        ) {
            Column(Modifier.padding(start = CreatorVideoStyle.cardStartPadding, end = CreatorVideoStyle.cardEndPadding, top = 12.dp, bottom = 12.dp)) {
                Text("Cargando datos…", fontSize = 16.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant))
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    repeat(4) { Box(Modifier.size(CreatorVideoStyle.chartThumbnailSize).clip(CreatorVideoStyle.chartThumbnailShape).background(colors.surfaceVariant)) }
                }
            }
        }
    }
    item(key = "summary") {
        Surface(Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
            }
        }
    }
    items(3, key = { "loading-video-$it" }) {
        Surface(Modifier.fillMaxWidth().height(88.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.91f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(CreatorVideoStyle.listThumbnailSize).clip(CreatorVideoStyle.listThumbnailShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                    Box(Modifier.fillMaxWidth(0.6f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
private fun FilterPill(filter: StatsFilter, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val platform = filter.platform
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics {
            this.selected = selected
            role = Role.Tab
            contentDescription = filter.label
        },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primary else colors.surface.copy(alpha = 0.86f),
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(if (platform == null) Icons.Outlined.Layers else platformIcon(platform), null, Modifier.size(20.dp))
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(tween(180), expandFrom = Alignment.Start) + fadeIn(tween(120)),
                exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
            ) {
                Text(filter.label, Modifier.padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun StatsMessage(title: String, note: String? = null, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        retry?.let { TextButton(onClick = it) { Text("Reintentar") } }
    }
}

@Composable
private fun StatsError(message: String, retry: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Text("No se pudieron cargar los datos", fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = retry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun StatsTotals(items: List<GroupStatsItemDto>, filter: StatsFilter, metric: CreatorMetric, onMetric: (CreatorMetric) -> Unit) {
    val totals = remember(items, filter) { metrics(items, filter) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
        Row(Modifier.fillMaxWidth().padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TotalItem(Icons.Outlined.Visibility, "Vistas", totals.views, metric == CreatorMetric.VIEWS, { onMetric(CreatorMetric.VIEWS) }, Modifier.weight(1f))
            TotalItem(Icons.Outlined.FavoriteBorder, "Likes", totals.likes, metric == CreatorMetric.LIKES, { onMetric(CreatorMetric.LIKES) }, Modifier.weight(1f))
            TotalItem(Icons.Outlined.ChatBubbleOutline, "Comentarios", totals.comments, metric == CreatorMetric.COMMENTS, { onMetric(CreatorMetric.COMMENTS) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TotalItem(icon: ImageVector, label: String, value: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: ${NumberFormat.getIntegerInstance(Locale("es")).format(value)}. Mostrar en la gráfica"
            }
            .heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(compact(value), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Icon(icon, null, Modifier.size(16.dp), tint = colors.primary)
        }
        Box(Modifier.padding(top = 4.dp).size(width = 18.dp, height = 2.dp).background(if (selected) colors.primary else Color.Transparent, RoundedCornerShape(1.dp)))
    }
}

private data class Metrics(val views: Long = 0, val likes: Long = 0, val comments: Long = 0)
private fun metrics(items: List<GroupStatsItemDto>, filter: StatsFilter): Metrics {
    val slots = items.flatMap { item ->
        platforms.filter { filter == StatsFilter.COMPARED || it == filter.platform }.mapNotNull { platform -> item.platforms[platform.apiValue] }
    }
    return Metrics(slots.sumOf { it.views.toLong() }, slots.sumOf { it.likes.toLong() }, slots.sumOf { it.comments.toLong() })
}

private fun metricValue(slot: GroupStatsSlotDto?, metric: CreatorMetric): Long = when (metric) {
    CreatorMetric.VIEWS -> slot?.views?.toLong() ?: 0L
    CreatorMetric.LIKES -> slot?.likes?.toLong() ?: 0L
    CreatorMetric.COMMENTS -> slot?.comments?.toLong() ?: 0L
}

private fun videoMetric(item: GroupStatsItemDto, filter: StatsFilter, metric: CreatorMetric): Long =
    platforms.filter { filter == StatsFilter.COMPARED || it == filter.platform }
        .sumOf { metricValue(item.platforms[it.apiValue], metric) }

@Composable
private fun StatsChartCard(
    items: List<GroupStatsItemDto>,
    filter: StatsFilter,
    metric: CreatorMetric,
    focusKey: String?,
    thumbnailUrl: (GroupStatsItemDto) -> String?,
    onVideoSelect: (String) -> Unit,
    onExpand: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(26.dp), color = colors.surface.copy(alpha = 0.92f), border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.17f)), shadowElevation = 2.dp) {
        Column(Modifier.padding(start = CreatorVideoStyle.cardStartPadding, end = CreatorVideoStyle.cardEndPadding, top = CreatorVideoStyle.cardVerticalPadding, bottom = CreatorVideoStyle.cardVerticalPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${metric.label} por video", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onExpand) { Icon(Icons.Outlined.Fullscreen, "Ampliar gráfico") }
            }
            StatsVideoChart(items, filter, metric, focusKey, thumbnailUrl, onVideoSelect, Modifier.fillMaxWidth(), plotHeight = 126.dp)
            Text("Del más antiguo al más reciente", Modifier.padding(top = 4.dp, bottom = 4.dp), fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsVideoChart(
    items: List<GroupStatsItemDto>,
    filter: StatsFilter,
    metric: CreatorMetric,
    focusKey: String?,
    thumbnailUrl: (GroupStatsItemDto) -> String?,
    onVideoSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    plotHeight: androidx.compose.ui.unit.Dp = 126.dp,
) {
    val itemsByKey = remember(items) { items.associateBy(::videoKey) }
    val bars = remember(items, filter, metric) {
        items.sortedBy { it.fecha_creacion }.map { item ->
            val value = videoMetric(item, filter, metric)
            CreatorChartBar(
                value = value, label = displayTitle(item), id = videoKey(item),
                valueLabel = compact(value), thumbnailUrl = thumbnailUrl(item),
                dateLabel = formatChartDate(item.fecha_creacion),
            )
        }
    }
    CreatorVideoMetricChart(
        bars = bars, metric = metric, selectedBarId = focusKey,
        onBarSelected = onVideoSelect,
        thumbnail = { bar, thumbnailModifier ->
            itemsByKey[bar.id]?.let { item ->
                StatsThumbnail(bar.thumbnailUrl, item, thumbnailModifier, CreatorVideoStyle.chartThumbnailShape)
            }
        },
        modifier = modifier, plotHeight = plotHeight, scrollIdentity = filter, showCaption = false,
    )
}

@Composable
private fun StatsVideoRow(item: GroupStatsItemDto, selectedMetric: CreatorMetric, expanded: Boolean, selected: Boolean, filter: StatsFilter, thumbnailUrl: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var linkError by remember(videoKey(item), filter) { mutableStateOf<String?>(null) }
    val total = videoMetric(item, filter, selectedMetric)
    val title = displayTitle(item)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "videoExpand")
    Surface(
        modifier = Modifier.semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.65f) else colors.surface.copy(alpha = 0.91f),
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.animateContentSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactHeader = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 88.dp)
                        .clickable(role = Role.Button, onClickLabel = if (expanded) "Contraer detalle" else "Expandir detalle", onClick = onClick)
                        .semantics { stateDescription = if (expanded) "Detalle expandido" else "Detalle contraído" }
                        .padding(start = 10.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(CreatorVideoStyle.listThumbnailSize)) {
                        StatsThumbnail(thumbnailUrl, item, Modifier.fillMaxSize())
                        if (selected) CreatorVideoSelectionMark(Modifier.align(Alignment.BottomEnd).padding(3.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatDate(item.fecha_creacion), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (compactHeader) Text(
                            "${compact(total)} ${selectedMetric.unit}",
                            Modifier.semantics { contentDescription = "${NumberFormat.getIntegerInstance(Locale("es")).format(total)} ${selectedMetric.label}" },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!compactHeader) Column(horizontalAlignment = Alignment.End) {
                        Text(
                            compact(total),
                            Modifier.semantics { contentDescription = "${NumberFormat.getIntegerInstance(Locale("es")).format(total)} ${selectedMetric.label}" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(selectedMetric.unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp).rotate(rotation))
                    }
                }
            }
            AnimatedVisibility(expanded) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    val fontScale = LocalDensity.current.fontScale
                    val useTable = maxWidth >= 280.dp && fontScale <= 1.2f
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (useTable) Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Spacer(Modifier.width(24.dp))
                            DetailHeading("Vistas", Modifier.weight(1f))
                            DetailHeading("Likes", Modifier.weight(1f))
                            DetailHeading("Comentarios", Modifier.weight(1.6f))
                            Spacer(Modifier.width(40.dp))
                        }
                        platforms.forEach { platform ->
                            if (filter != StatsFilter.COMPARED && platform != filter.platform) return@forEach
                            val slot = item.platforms[platform.apiValue] ?: return@forEach
                            if (useTable) Row(
                                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                    Icon(platformIcon(platform), platformNames.getValue(platform), Modifier.size(18.dp), tint = platformColor(platform))
                                }
                                DetailMetric("Vistas", slot.views.toLong(), false, Modifier.weight(1f))
                                DetailMetric("Likes", slot.likes.toLong(), false, Modifier.weight(1f))
                                DetailMetric("Comentarios", slot.comments.toLong(), false, Modifier.weight(1.6f))
                                PlatformVideoLink(platform, slot.platformUrl) { linkError = it }
                            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(platformIcon(platform), null, Modifier.size(18.dp), tint = platformColor(platform))
                                    Text(platformNames.getValue(platform), Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    PlatformVideoLink(platform, slot.platformUrl) { linkError = it }
                                }
                                if (fontScale > 1.6f) {
                                    DetailMetricLine("Vistas", slot.views.toLong())
                                    DetailMetricLine("Likes", slot.likes.toLong())
                                    DetailMetricLine("Comentarios", slot.comments.toLong())
                                } else Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DetailMetric("Vistas", slot.views.toLong(), true, Modifier.weight(1f))
                                    DetailMetric("Likes", slot.likes.toLong(), true, Modifier.weight(1f))
                                    DetailMetric("Comentarios", slot.comments.toLong(), true, Modifier.weight(1.6f))
                                }
                            }
                        }
                        linkError?.let { Text(it, fontSize = 12.sp, color = colors.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeading(label: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailMetric(label: String, value: Long, showLabel: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "${NumberFormat.getIntegerInstance(Locale("es")).format(value)} $label"
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showLabel) Text(label, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            compact(value),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailMetricLine(label: String, value: Long) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$label: ${NumberFormat.getIntegerInstance(Locale("es")).format(value)}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(compact(value), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun PlatformVideoLink(platform: Platform, url: String, onError: (String?) -> Unit) {
    val uriHandler = LocalUriHandler.current
    if (url.isBlank()) {
        Spacer(Modifier.width(40.dp))
    } else IconButton(modifier = Modifier.size(40.dp), onClick = {
        runCatching { uriHandler.openUri(url) }
            .onSuccess { onError(null) }
            .onFailure { onError("No se pudo abrir el video en ${platformNames[platform]}. Puedes volver a intentarlo.") }
    }) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir este video en ${platformNames[platform]}", Modifier.size(18.dp))
    }
}

@Composable
private fun StatsThumbnail(thumb: String?, item: GroupStatsItemDto, modifier: Modifier = Modifier, shape: Shape = CreatorVideoStyle.listThumbnailShape) {
    val url = thumb ?: platforms.firstNotNullOfOrNull { p -> item.platforms[p.apiValue]?.thumbnail?.takeIf(String::isNotBlank) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ExpandedStatsChart(
    items: List<GroupStatsItemDto>,
    filter: StatsFilter,
    metric: CreatorMetric,
    focusKey: String?,
    thumbnailUrl: (GroupStatsItemDto) -> String?,
    onMetric: (CreatorMetric) -> Unit,
    onVideoSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(start = CreatorVideoStyle.cardStartPadding, end = CreatorVideoStyle.cardEndPadding, top = CreatorVideoStyle.cardVerticalPadding, bottom = CreatorVideoStyle.cardVerticalPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${metric.label} por video", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.ExpandLess, "Cerrar gráfico") }
                }
                StatsVideoChart(items, filter, metric, focusKey, thumbnailUrl, onVideoSelect, Modifier.fillMaxWidth(), plotHeight = 244.dp)
                Text("Del video más antiguo al más reciente", Modifier.align(Alignment.CenterHorizontally), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CreatorMetricSegmentedControl(metric, onMetric, Modifier.align(Alignment.End).padding(top = 8.dp))
            }
        }
    }
}

private fun formatDate(iso: String): String = runCatching {
    val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es")))
}.getOrElse { iso.take(10) }

private fun formatChartDate(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d/M", Locale("es")))
}.getOrElse { iso.take(5) }

private val previewItem = GroupStatsItemDto(
    fileId = "preview-1",
    fileName = "receta-de-temporada.mp4",
    fecha_creacion = "2026-09-30T12:00:00Z",
    platforms = mapOf(
        "youtube" to GroupStatsSlotDto("yt", "https://youtube.com/", "Una receta de temporada", "", 18300, 940, 38),
        "instagram" to GroupStatsSlotDto("ig", "https://instagram.com/", "Una receta de temporada", "", 12600, 1100, 24),
        "tiktok" to GroupStatsSlotDto("tt", "https://tiktok.com/", "Una receta de temporada", "", 28100, 2300, 91),
    ),
)

private val previewItems = (1..8).map { index ->
    previewItem.copy(
        fileId = "preview-$index",
        fileName = "receta-de-temporada-$index.mp4",
        fecha_creacion = "2026-09-${index.toString().padStart(2, '0')}T12:00:00Z",
        platforms = previewItem.platforms.mapValues { (platform, slot) ->
            slot.copy(
                views = when (platform) {
                    "youtube" -> index * 4_300
                    "instagram" -> if (index == 2) 0 else index * 2_700
                    else -> index * 7_100
                },
                likes = index * 150,
                comments = if (index == 3 && platform == "youtube") 0 else index * 17,
            )
        }.filterKeys { !(index % 3 == 0 && it == "tiktok") },
    )
}

@Composable
private fun StatsPreviewContent(expanded: Boolean = false) {
    CreatorHomeTheme {
        Box(Modifier.fillMaxSize()) {
            CreatorStudioBackdrop()
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datos", Modifier.padding(horizontal = 8.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = com.esseanalytics.android.core.designsystem.component.CreatorSerif)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatsFilter.entries.forEach { option -> FilterPill(option, option == StatsFilter.COMPARED, {}) }
                }
                StatsChartCard(previewItems, StatsFilter.COMPARED, CreatorMetric.VIEWS, "preview-8", { null }, {}, {})
                StatsTotals(previewItems, StatsFilter.COMPARED, CreatorMetric.VIEWS, {})
                StatsVideoRow(previewItems.last(), CreatorMetric.VIEWS, expanded, true, StatsFilter.COMPARED, null, {})
            }
        }
    }
}

@Preview(name = "Datos Studio claro", showBackground = true)
@Composable
private fun StatsStudioLightPreview() = StatsPreviewContent()

@Preview(name = "Datos Studio oscuro", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsStudioDarkPreview() = StatsPreviewContent()

@Preview(name = "Datos 320dp texto grande", showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun StatsStudioCompactPreview() = StatsPreviewContent()

@Preview(name = "Datos fila desplegada", showBackground = true)
@Composable
private fun StatsStudioExpandedPreview() = StatsPreviewContent(expanded = true)

@Preview(name = "Datos detalle 320dp texto grande", showBackground = true, widthDp = 320, fontScale = 1.3f, heightDp = 1100)
@Composable
private fun StatsStudioCompactExpandedPreview() = StatsPreviewContent(expanded = true)
