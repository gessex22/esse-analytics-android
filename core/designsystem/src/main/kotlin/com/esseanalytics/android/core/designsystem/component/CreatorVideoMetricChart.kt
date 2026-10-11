package com.esseanalytics.android.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Un mismo eje, alineación y selección por video para Inicio y Datos. */
@Composable
fun CreatorVideoMetricChart(
    bars: List<CreatorChartBar>,
    metric: CreatorMetric,
    selectedBarId: String?,
    onBarSelected: (String) -> Unit,
    thumbnail: @Composable (CreatorChartBar, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 126.dp,
    scrollIdentity: Any? = null,
    showCaption: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val maximum = bars.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val density = LocalDensity.current
    val groupWidth = 58.dp * (density.fontScale / 1.3f).coerceAtLeast(1f)
    val actualPlotHeight = maxOf(plotHeight, with(density) { 48.sp.toDp() })
    val groupWidthPx = with(density) { groupWidth.roundToPx() }
    var scrollInitialized by rememberSaveable { mutableStateOf(false) }
    var previousSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var previousIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    val identity = scrollIdentity?.toString()
    LaunchedEffect(identity, selectedBarId, groupWidthPx, bars.isNotEmpty()) {
        // Volver a una pestaña restaura su posición; no repite un desplazamiento
        // automático que compita con el scroll del usuario.
        if (bars.isEmpty() || (scrollInitialized && previousSelection == selectedBarId && previousIdentity == identity)) return@LaunchedEffect
        withFrameNanos { }
        val focused = bars.indexOfFirst { it.id == selectedBarId }
        val target = if (focused >= 0) focused else bars.lastIndex
        val animate = scrollInitialized
        scrollInitialized = true
        previousSelection = selectedBarId
        previousIdentity = identity
        if (target >= 0) {
            val offset = (target * groupWidthPx).coerceAtMost(scrollState.maxValue)
            if (animate) scrollState.animateScrollTo(offset) else scrollState.scrollTo(offset)
        }
    }
    Column(modifier) {
        if (bars.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                Text("Tus métricas aparecerán aquí", color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.width(IntrinsicSize.Max).height(actualPlotHeight).padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    listOf(maximum, maximum / 2, 0L).forEach { value ->
                        Text(formatCreatorMetric(value), fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(scrollState).selectableGroup()) {
                    bars.forEach { bar ->
                        key(bar.id) {
                            val selected = bar.id == selectedBarId
                            val fraction by animateFloatAsState(
                                (bar.value.toDouble() / maximum).toFloat().coerceIn(0f, 1f),
                                animationSpec = tween(200), label = "videoBarHeight",
                            )
                            val grid = colors.outlineVariant.copy(alpha = 0.65f)
                            val gradient = Brush.verticalGradient(
                                if (selected) listOf(colors.primary.copy(alpha = 0.8f), colors.primary)
                                else listOf(colors.primary.copy(alpha = 0.48f), colors.primary.copy(alpha = 0.55f)),
                            )
                            Column(
                                Modifier.width(groupWidth)
                                    .selectable(selected, role = Role.RadioButton, onClick = { onBarSelected(bar.id) })
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${bar.label}: ${bar.value} ${metric.unit}. Seleccionar video"
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Canvas(Modifier.fillMaxWidth().height(actualPlotHeight)) {
                                    val labelInset = 8.sp.toPx()
                                    val bottom = size.height - labelInset
                                    listOf(labelInset, size.height / 2f, bottom).forEach { y ->
                                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                    }
                                    val height = (bottom - labelInset) * fraction
                                    if (height > 0f) {
                                        val width = size.width * 0.48f
                                        drawRoundRect(
                                            gradient, Offset((size.width - width) / 2f, bottom - height),
                                            Size(width, height), CornerRadius(7.dp.toPx()),
                                        )
                                    }
                                }
                                Surface(
                                    Modifier.padding(top = 7.dp).size(CreatorVideoStyle.chartThumbnailSize),
                                    shape = CreatorVideoStyle.chartThumbnailShape,
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
                                ) { thumbnail(bar, Modifier.fillMaxSize()) }
                                Text(bar.valueLabel, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = colors.onSurface)
                                bar.dateLabel?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, color = colors.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            if (showCaption) Text("Por video · del más antiguo al más reciente", Modifier.padding(top = 4.dp), fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
        }
    }
}
