package com.esseanalytics.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo

// Datos exclusivamente de Preview; el Dashboard usa los datos del ViewModel.
private val previewBars = listOf(
    CreatorChartBar(1200, "Un día creando sin prisa", "day", "1,2k"),
    CreatorChartBar(2100, "Mi setup para editar", "setup", "2,1k"),
    CreatorChartBar(1100, "Ideas para esta semana", "ideas", "1,1k"),
    CreatorChartBar(2400, "El proceso detrás del video", "process", "2,4k"),
    CreatorChartBar(1600, "Así detecta Google los sismos", "quake", "1,6k"),
    CreatorChartBar(398, "Los nombres de los agentes de IA", "agents", "398"),
)

@Composable
private fun PreviewThumbnail(bar: CreatorChartBar, modifier: Modifier) {
    val swatches = listOf(Color(0xFF5B927E), Color(0xFF897E67), Color(0xFF667F8A))
    val color = swatches[previewBars.indexOfFirst { it.id == bar.id }.coerceAtLeast(0) % swatches.size]
    Box(modifier.background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.PlayArrow, null, tint = Color.White)
    }
}

@Composable
private fun StudioPreview(dark: Boolean, scrolled: Boolean = false, refreshing: Boolean = false) {
    var selected by remember { mutableStateOf(previewBars.last().id) }
    var metric by remember { mutableStateOf(CreatorMetric.VIEWS) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (scrolled) 1 else 0, initialFirstVisibleItemScrollOffset = if (scrolled) 160 else 0)
    CreatorHomeTheme(darkTheme = dark) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showUsername = maxWidth >= 360.dp && (LocalDensity.current.fontScale <= 1.2f || maxWidth >= 400.dp)
            CreatorStudioBackdrop()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { CreatorHomeBrand() }
                item {
                    CreatorMetricCard(metric, { metric = it }, "8,8k", "Últimos 6 videos", previewBars,
                        selectedBarId = selected, onBarSelected = { selected = it }, thumbnail = { bar, modifier -> PreviewThumbnail(bar, modifier) })
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Videos recientes", fontFamily = CreatorSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        Text("Ver todos ↗", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }
                previewBars.asReversed().forEach { bar ->
                    item(key = bar.id) {
                        CreatorVideoListItem(bar.label, bar.valueLabel, "vistas", {}, {},
                            thumbnail = { PreviewThumbnail(bar, it) }, metadata = {}, menu = {}, highlighted = selected == bar.id)
                    }
                }
                item {
                    CreatorCommentsSummary("42", "En los 6 videos de esta lista", listOf(
                        CreatorCommentPlatform("YouTube", "24", PlatformIcons.YoutubeLogo, Color(0xFFE53935)),
                        CreatorCommentPlatform("Instagram", "12", PlatformIcons.InstagramLogo, Color(0xFF9455CF)),
                        CreatorCommentPlatform("TikTok", "6", PlatformIcons.TiktokLogo, MaterialTheme.colorScheme.onSurface),
                    ), Modifier.padding(top = 14.dp))
                }
            }
            CreatorFloatingHomeActions(
                "Esse", {}, {}, showUsername = showUsername,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 20.dp),
            )
            CreatorLoadingBar(refreshing, Modifier.align(Alignment.TopCenter).fillMaxWidth())
            CreatorBottomNav(
                items = listOf(
                    CreatorNavItem("home", "Inicio", Icons.Outlined.Dashboard),
                    CreatorNavItem("calendar", "Agenda", Icons.Outlined.CalendarMonth),
                    CreatorNavItem("create", "Subir", Icons.Outlined.CloudUpload),
                    CreatorNavItem("videos", "Videos", Icons.Outlined.VideoLibrary),
                    CreatorNavItem("stats", "Datos", Icons.Outlined.QueryStats),
                ),
                selectedRoute = "home", createRoute = "create", moreRoute = "more", onNavigate = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
@Preview(name = "Studio · claro", widthDp = 393, heightDp = 852)
@Composable
private fun LightStudioPreview() = StudioPreview(false)

@Preview(name = "Studio · oscuro", widthDp = 393, heightDp = 852)
@Composable
private fun DarkStudioPreview() = StudioPreview(true)

@Preview(name = "Studio · texto grande", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LargeTextStudioPreview() = StudioPreview(false)

@Preview(name = "Studio · compacto", widthDp = 320, heightDp = 800)
@Composable
private fun CompactStudioPreview() = StudioPreview(false)

@Preview(name = "Studio · actualización con barra fina", widthDp = 393, heightDp = 852)
@Composable
private fun RefreshingStudioPreview() = StudioPreview(false, refreshing = true)

@Preview(name = "Studio · acciones flotantes al desplazar", widthDp = 393, heightDp = 852)
@Composable
private fun ScrolledStudioPreview() = StudioPreview(false, scrolled = true)
