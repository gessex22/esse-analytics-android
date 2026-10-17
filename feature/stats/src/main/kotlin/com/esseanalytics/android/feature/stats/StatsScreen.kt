package com.esseanalytics.android.feature.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import com.esseanalytics.android.core.designsystem.component.*
import com.esseanalytics.android.core.designsystem.theme.StudioTheme
import com.esseanalytics.android.core.network.dto.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel(),
    username: String = "", onProfileClick: () -> Unit = {}, onNotificationClick: () -> Unit = {},
    onReviewLocal: (Long) -> Unit = {},
    initialVideoKey: String? = null, initialVideoRequest: Long = 0, showFloatingHeader: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val localIssues by viewModel.localIssues.collectAsStateWithLifecycle()
    var active by rememberSaveable { mutableStateOf(StatsMetric.VIEWS) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var selectedBar by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<VideoStatsSelection?>(null) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (resumed) viewModel.refresh() else resumed = true }
    LaunchedEffect(initialVideoKey, initialVideoRequest, state.items) {
        if (!initialVideoKey.isNullOrBlank() && initialVideoRequest != 0L) state.items.firstOrNull { it.fileId == initialVideoKey }?.let { selection = it.selection() }
    }
    StudioTheme {
        Box(modifier.fillMaxSize()) {
            CreatorStudioBackdrop()
            PullToRefreshBox(state.loading, viewModel::refresh, Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().then(if (showFloatingHeader) Modifier.statusBarsPadding() else Modifier),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item("title") { Text("Datos", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, fontSize = 34.sp) }
                    item("segments") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(4.dp)) {
                            StatsSegment.entries.forEach { segment ->
                                val selected = state.segment == segment
                                Surface(shape = RoundedCornerShape(9.dp), color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent) {
                                    Text(segment.label, Modifier.selectable(selected, role = Role.Tab) { viewModel.select(segment) }.padding(horizontal = 15.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                    state.error?.let { item("error") { StatsCard { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::refresh) { Text("Reintentar") } } } }
                    if (state.loading && !state.loaded) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Cargando datos…", style = MaterialTheme.typography.bodySmall) }
                    when (state.segment) {
                        StatsSegment.COMPARED -> if (state.loaded) {
                            item("compared") {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    MetricCards(state.items, active) { active = it }
                                    StatsCard {
                                        Text("${active.label} por video", style = MaterialTheme.typography.titleMedium)
                                        val chronological = state.items.sortedBy { it.fecha_creacion.orEmpty() }
                                        val bars = chronological.filter { it.total(active) != null }.map { item -> CreatorChartBar(item.total(active)!!, item.displayTitle, item.identity, number(item.total(active)), viewModel.thumbnailUrl(item), dateLabel(item.fecha_creacion)) }
                                        CreatorVideoMetricChart(bars, when(active) { StatsMetric.VIEWS -> CreatorMetric.VIEWS; StatsMetric.LIKES -> CreatorMetric.LIKES; StatsMetric.COMMENTS -> CreatorMetric.COMMENTS }, selectedBar, { selectedBar = it; showList = true }, thumbnail = { bar, mod -> VideoThumbnail(null, bar.thumbnailUrl, mod) }, scrollIdentity = active)
                                    }
                                    PlatformDonut(state.items, active)
                                    TextButton(onClick = { showList = !showList }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if(showList) "Expandido" else "Contraído" }) {
                                        Text(if(showList) "Ocultar lista" else "Mostrar lista", color = Color(0xFF007AFF))
                                    }
                                    StudioAccordion(showList) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("Videos uno a uno", style = MaterialTheme.typography.titleMedium)
                                            if (state.items.isEmpty()) Text("Todavía no hay videos comparables.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            sortedVideos(state.items, active).forEach { item -> key(item.identity) { VideoStatsRow(item, viewModel.thumbnailUrl(item), active) { selection = item.selection() } } }
                                        }
                                    }
                                }
                            }
                        }
                        StatsSegment.RECENT -> {
                            item("historyFilter") {
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { menu = true }) { Text("${state.historyFilter.label} ▾") }
                                    DropdownMenu(menu, { menu = false }) { HistoryFilter.entries.forEach { filter -> DropdownMenuItem(text = { Text(filter.label) }, onClick = { menu = false; viewModel.setHistoryFilter(filter) }) } }
                                }
                            }
                            localIssues.filter { state.historyFilter.apiValue == null || it.platform == state.historyFilter.apiValue }.forEach { issue -> item("local:${issue.id}") {
                                StatsCard {
                                    Text(issue.title, fontWeight = FontWeight.SemiBold)
                                    Text("${platformName(issue.platform)} · Revisión pendiente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    Text(issue.message, style = MaterialTheme.typography.bodySmall)
                                    issue.fileId?.let { id -> TextButton(onClick = { onReviewLocal(id) }) { Text("Revisar") } }
                                }
                            } }
                            if (state.loaded) {
                            val recent = groupRecentHistory(state.history)
                            if (recent.isEmpty()) item("emptyRecent") { StatsCard { Text("Todavía no hay publicaciones.") } }
                            items(recent, key = { it.key }) { row ->
                                val item = row.stats
                                if (item != null) VideoStatsRow(item.copy(fecha_creacion = row.event.publishedAt), viewModel.thumbnailUrl(item), delta = true) { selection = row.event.detailSelection("Datos").copy(seed = item) }
                                else StatsCard(Modifier.clickable(role = Role.Button) { selection = row.event.detailSelection("Datos") }) {
                                    Text(row.event.title?.takeIf(String::isNotBlank) ?: row.event.fileName ?: "Publicación", fontWeight = FontWeight.SemiBold)
                                    Text(dateLabel(row.event.publishedAt), style = MaterialTheme.typography.labelSmall)
                                    MetricTotals(null, delta = true)
                                    Text("Estadísticas pendientes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (state.hasMore) item("more") { TextButton(onClick = viewModel::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if(state.loadingMore) "Cargando…" else "Mostrar más") } }
                        }
                        }
                        StatsSegment.PLATFORMS -> if (state.loaded) {
                            item("weeklyTitle") { Text("Historial semanal", style = MaterialTheme.typography.titleMedium) }
                            if (state.weeks.isEmpty()) item("emptyWeeks") { StatsCard { Text("Sin cierres semanales disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            items(state.weeks, key = { it.id }) { WeeklyStatsCard(it) }
                        }
                    }
                }
            }
            selection?.let { VideoStatsDetailSheet(it) { selection = null } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricCards(items: List<GroupStatsItemDto>, active: StatsMetric, onSelect: (StatsMetric) -> Unit) {
    val accessible = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cellWidth = if (accessible) maxWidth else (maxWidth - 16.dp) / 3
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsMetric.entries.forEach { metric ->
            val known = items.mapNotNull { it.total(metric) }
            Surface(Modifier.width(cellWidth).selectable(active == metric, role = Role.RadioButton) { onSelect(metric) }, shape = RoundedCornerShape(14.dp), color = if(active == metric) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if(active == metric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(metric.label, style = MaterialTheme.typography.labelSmall)
                    Text(number(known.takeIf { it.isNotEmpty() }?.sum()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    if (known.size < items.size) Text("Parcial", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
}
@Composable
private fun PlatformDonut(items: List<GroupStatsItemDto>, metric: StatsMetric) {
    val sums = statsPlatforms.associateWith { p -> items.filter { it.metricsAvailable != false }.mapNotNull { it.platforms[p] }.takeIf { it.isNotEmpty() }?.sumOf { it.value(metric) } }
    val total = sums.values.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    val colors = statsPlatforms.map { platformColor(it) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    StatsCard {
        Text("${metric.label} por plataforma", style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(148.dp).semantics { contentDescription = "${metric.label}: ${number(total)}" }) {
                val width = 18.dp.toPx(); val inset = width / 2
                drawArc(track, -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset,inset), size = Size(size.width-width, size.height-width), style = Stroke(width))
                var angle = -90f
                if (total != null && total > 0) statsPlatforms.forEachIndexed { i, p ->
                    val sweep = (sums[p] ?: 0).toFloat() / total.toFloat() * 360f
                    if(sweep > 0) drawArc(colors[i], angle, sweep, false, topLeft = androidx.compose.ui.geometry.Offset(inset,inset), size = Size(size.width-width,size.height-width), style = Stroke(width))
                    angle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(number(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(metric.label, style = MaterialTheme.typography.labelSmall) }
        }
        statsPlatforms.forEachIndexed { i, p -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(platformName(p), color = colors[i]); Text(number(sums[p])) } }
    }
}
@Composable
private fun WeeklyStatsCard(week: WeeklyStatsWeekDto) {
    var expanded by rememberSaveable(week.id) { mutableStateOf(false) }
    StatsCard {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.semantics { stateDescription = if(expanded) "Expandido" else "Contraído" }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${dateLabel(week.start)} – ${dateLabel(runCatching { java.time.Instant.parse(week.end).minusSeconds(1).toString() }.getOrNull())}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(if(expanded) "⌃" else "⌄") }
            WeeklyValues(week.totals)
            if (week.status != "complete" || week.estimated) Text(when { week.status == "unavailable" -> "Semana sin datos"; week.status == "partial" -> "Cierre parcial · estimado"; else -> "Cierre estimado" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StudioAccordion(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (week.platforms.isEmpty()) Text("Sin datos por plataforma.", style = MaterialTheme.typography.bodySmall)
                week.platforms.forEach { p -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { HorizontalDivider(); Text(platformName(p.platform), fontWeight = FontWeight.SemiBold); WeeklyValues(WeeklyStatsValuesDto(p.views,p.likes,p.comments,p.subscribersGained)) } }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklyValues(values: WeeklyStatsValuesDto) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Vistas" to number(values.views), "Me gusta" to number(values.likes), "Comentarios" to number(values.comments), "Suscriptores" to signed(values.subscribersGained)).forEach { (label,value) -> Column(Modifier.widthIn(min = 90.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) } }
    }
}
