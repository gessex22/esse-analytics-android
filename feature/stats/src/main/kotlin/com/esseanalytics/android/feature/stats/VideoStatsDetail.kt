package com.esseanalytics.android.feature.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.theme.StudioTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStatsDetailSheet(selection: VideoStatsSelection, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        VideoStatsDetailScreen(selection, onDismiss, Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * .88f).dp), inSheet = true)
    }
}

/** One body for Dashboard, Calendar and both Datos entry points. */
@Composable
fun VideoStatsDetailScreen(selection: VideoStatsSelection, onBack: () -> Unit, modifier: Modifier = Modifier, inSheet: Boolean = false, viewModel: VideoStatsDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(selection) { viewModel.load(selection) }
    StudioTheme {
        Box(modifier.fillMaxSize()) {
            CreatorStudioBackdrop()
            Column(Modifier.fillMaxSize().then(if (inSheet) Modifier else Modifier.statusBarsPadding())) {
                TextButton(onClick = onBack) { Text("‹ ${selection.origin}") }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.load(selection) }) { Text("Reintentar") } }
                    state.item?.let { item ->
                        StatsCard {
                            VideoThumbnail(item, viewModel.thumbnail(item), Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * .26f).coerceIn(88f,220f).dp))
                            Text(item.displayTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(dateLabel(item.fecha_creacion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MetricTotals(item, delta = true)
                        }
                        StatsCard { Text("Plataformas", style = MaterialTheme.typography.titleMedium); PlatformMetricTable(item) }
                    }
                    if (!state.loading && state.item == null) Text("Estadísticas no disponibles para este video.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
