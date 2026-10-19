package com.esseanalytics.android.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.component.RefreshErrorBanner
import com.esseanalytics.android.core.designsystem.component.formatCreatorMetric
import com.esseanalytics.android.core.designsystem.theme.StudioTheme
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.network.dto.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    onVideoClick: (VideoStatsSelection) -> Unit = {},
    onPendingAction: (DashboardPendingActionDto) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hasResumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasResumed) viewModel.refresh() else hasResumed = true
    }
    StudioTheme {
        DashboardContent(state, viewModel::refresh, onVideo = { onVideoClick(it.detailSelection()) }, onPendingAction = onPendingAction, modifier = modifier)
    }
}

/** Studio summary: no charts, no duplicated statistics list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onVideo: (DashboardVideoDto) -> Unit = {},
    modifier: Modifier = Modifier,
    onPendingAction: (DashboardPendingActionDto) -> Unit = {},
) {
    val success = state as? DashboardUiState.Success
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showPending by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.background)) {
        CreatorStudioBackdrop()
        PullToRefreshBox(isRefreshing = state == DashboardUiState.Loading || success?.isRefreshing == true, onRefresh = onRefresh) {
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("title") { Text("Dashboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, fontSize = 34.sp) }
                when (state) {
                    DashboardUiState.Loading -> item("loading") { Text("Actualizando tu resumen…", color = colors.onSurfaceVariant) }
                    is DashboardUiState.Error -> item("error") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.message)
                            TextButton(onClick = onRefresh) { Text("Reintentar") }
                        }
                    }
                    is DashboardUiState.Success -> {
                        state.refreshError?.let { item("refreshError") { RefreshErrorBanner(it) } }
                        if (state.compatibilityMode) item("compatibility") {
                            Text("El servidor necesita actualizarse para mostrar el resumen completo. Tus publicaciones disponibles se muestran abajo.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        item("subscribers") { SubscribersTile(state.data.followers, expanded, { expanded = !expanded }) }
                        item("week") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionTitle("Esta semana")
                                DashboardCard {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
                                        WeekCell("Publicaciones", state.data.week.publications?.let { formatCreatorMetric(it.toLong()) } ?: "—", when (state.data.week.publications) { null -> "No disponible"; 0 -> "Sin videos"; else -> "Esta semana" }, Modifier.weight(1f))
                                        WeekCell("Vistas", state.data.week.views?.let(::formatCreatorMetric) ?: "Pendiente",
                                            state.data.week.viewsDeltaPercent?.let { "${if (it >= 0) "+" else ""}${NumberFormat.getNumberInstance(Locale("es")).format(it)}% vs anterior" }
                                                ?: if (state.data.week.views == null) viewsUnavailableDetail(state.data.week.viewsStatus) else "Sin comparativa", Modifier.weight(1f))
                                        val pending = state.data.pending
                                        val calendarCount = pending?.items?.size
                                        WeekCell("Calendario · hoy", calendarCount?.let { if (it == 0) "Al día" else formatCreatorMetric(it.toLong()) } ?: "—",
                                            "", Modifier.weight(1f).clickable(enabled = pending != null, role = Role.Button) { showPending = true }, (calendarCount ?: 0) > 0,
                                            success = calendarCount == 0)
                                    }
                                    if (state.data.week.views != null && state.data.week.viewsEstimated) Text("Vistas estimadas · observación diaria", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
                                    else if (state.data.week.viewsStatus == "collecting_history") Text("Estamos guardando el historial para calcular las vistas recibidas desde el inicio de la semana.", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
                                }
                            }
                        }
                        item("latest") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionTitle("Lo último de cada plataforma")
                                DashboardCard {
                                    listOf("youtube", "instagram", "tiktok").forEachIndexed { index, platform ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(start = 90.dp), color = colors.outlineVariant)
                                        LatestRow(platform, state.data.latest.firstOrNull { it.platform == platform }?.video, onVideo)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val pendingDetail = success?.data?.pending
        if (showPending && pendingDetail != null) {
            ModalBottomSheet(onDismissRequest = { showPending = false }, containerColor = colors.background) {
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("Publicaciones de hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (pendingDetail.items.isEmpty()) item { Text("No hay publicaciones pendientes para hoy.") }
                    pendingDetail.items.forEach { calendarItem -> item("${calendarItem.platform}:${calendarItem.fileId}") {
                        StatsCard {
                            Text(calendarItem.title, fontWeight = FontWeight.SemiBold)
                            Text("${platformName(calendarItem.platform)} · ${if (calendarItem.overdue) "Atrasada desde" else "Programada para"} ${calendarItem.dueDate}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun SubscribersTile(data: DashboardFollowersDto, expanded: Boolean, onToggle: () -> Unit) {
    val accessible = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    val expandedForLayout = expanded
    val fraction = if (expandedForLayout || accessible) 1f else 1f / 3f
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(fraction).semantics { stateDescription = if (expandedForLayout) "Expandido. Toca para contraer" else "Contraído. Toca para ver plataformas" },
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (accessible) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                SubscriberSummary(data, Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                if (expandedForLayout) {
                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        data.platforms.forEach { item -> SubscriberPlatform(item) }
                    }
                }
            }
        } else if (expandedForLayout) Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            SubscriberSummary(data, Modifier.weight(1f).padding(horizontal = 10.dp))
            Column(Modifier.weight(2f).padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                data.platforms.forEach { item -> SubscriberPlatform(item) }
            }
        } else {
            SubscriberSummary(data, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp))
        }
    }
}

@Composable
private fun SubscriberSummary(data: DashboardFollowersDto, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Suscriptores", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(data.total?.let { if (it >= 1_000_000) formatCreatorMetric(it) else NumberFormat.getIntegerInstance(Locale("es")).format(it) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, softWrap = false,
            modifier = Modifier.semantics { contentDescription = data.total?.toString() ?: "Sin datos" })
        if (data.partial) Text(if (data.total == null) "Sin datos" else "Total parcial", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubscriberPlatform(item: DashboardFollowerDto) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(platformName(item.platform), style = MaterialTheme.typography.labelMedium)
            CountUp(item.count)
        }
        Text(item.weeklyGain?.let { "${if (it >= 0) "+" else ""}${formatCreatorMetric(it)} esta semana" } ?: when (item.status) {
            "reconnect" -> if (item.platform == "tiktok") "Autoriza estadísticas en Ajustes" else "Reconectar en Ajustes"
            "disconnected" -> "Sin conectar"
            "syncing" -> "Sincronizando…"
            "unavailable" -> "No disponible"
            else -> "Sin historial semanal"
        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CountUp(target: Long?) {
    val count = remember { Animatable(0f) }
    LaunchedEffect(target) { count.snapTo(0f); count.animateTo(1f, tween(650)) }
    Text(target?.let { NumberFormat.getIntegerInstance(Locale("es")).format((it.toDouble() * count.value).roundToLong()) } ?: "—", fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { contentDescription = target?.toString() ?: "Sin datos" })
}

@Composable
private fun DashboardCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(content = content) }
}

@Composable
private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

@Composable
private fun WeekCell(label: String, value: String, detail: String, modifier: Modifier, pending: Boolean = false, success: Boolean = false) {
    val orange = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFFFF9F0A) else androidx.compose.ui.graphics.Color(0xFFAD4E00)
    Column(modifier.heightIn(min = 92.dp).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (success) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = if (pending) orange else MaterialTheme.colorScheme.onSurface)
        }
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = if (pending) orange else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LatestRow(platform: String, video: DashboardVideoDto?, onVideo: (DashboardVideoDto) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = video != null, role = Role.Button) { video?.let(onVideo) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (!video?.thumbnail.isNullOrBlank()) AsyncImage(video?.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(video?.title?.ifBlank { video.fileName ?: "Video sin título" } ?: "Sin videos recientes", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (video != null) Text("${if (video.metricsAvailable == false) "Vistas pendientes" else "${formatCreatorMetric(video.views)} vistas"} · ${relativeTime(video.publishedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(when (platform) { "youtube" -> PlatformIcons.YoutubeLogo; "instagram" -> PlatformIcons.InstagramLogo; else -> PlatformIcons.TiktokLogo },
            contentDescription = platformName(platform), modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (video != null) Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun viewsUnavailableDetail(status: String?) = when (status) {
    "collecting_history" -> "Creando historial"
    "reconnect" -> "Revisa las conexiones"
    "disconnected" -> "Conecta tus cuentas"
    "unavailable" -> "Datos no disponibles"
    else -> "Sin historial semanal"
}
