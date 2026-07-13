package com.esseanalytics.android.feature.sync

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.network.dto.CrossMatchCandidateDto
import com.esseanalytics.android.core.network.dto.CrossMatchResolvedSlotDto
import com.esseanalytics.android.core.network.dto.PlatformRecentItemDto
import com.esseanalytics.android.core.network.dto.SyncReviewItemDto
import com.esseanalytics.android.core.network.dto.SyncStatsDto

// Puerto de frontend/src/components/SyncPanel.tsx -- ver SyncViewModel para
// el detalle de los 2 flujos. Sin miniaturas (no hay ThumbnailGenerator real
// todavía, ver core:media) -- filas de texto en su lugar.
@Composable
fun SyncScreen(modifier: Modifier = Modifier, viewModel: SyncViewModel = hiltViewModel()) {
    val crossMatchState by viewModel.crossMatchState.collectAsState()
    val slotPicker by viewModel.slotPicker.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val reviewState by viewModel.reviewState.collectAsState()
    val busyReviewId by viewModel.busyReviewId.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                "Emparejar entre plataformas",
                "Archivos ya publicados en las 3 redes — completá el link de las que falten.",
                onRefresh = viewModel::loadCrossMatchCandidates,
            )
        }

        when (val current = crossMatchState) {
            is CrossMatchUiState.Loading -> item { LoadingRow() }
            is CrossMatchUiState.Error -> item { ErrorRow(current.message) }
            is CrossMatchUiState.Success -> {
                if (current.candidates.isEmpty()) {
                    item {
                        EmptyRow(
                            "Sin candidatos todavía",
                            "No hay archivos locales con las 3 badges de plataforma marcadas.",
                        )
                    }
                } else {
                    items(current.candidates, key = { "cm_${it.fileId}" }) { candidate ->
                        CandidateCard(
                            candidate = candidate,
                            openSlot = slotPicker?.takeIf { it.fileId == candidate.fileId },
                            onToggleSlot = { platform ->
                                if (slotPicker?.fileId == candidate.fileId && slotPicker?.platform == platform) {
                                    viewModel.closeSlotPicker()
                                } else {
                                    viewModel.openSlotPicker(candidate.fileId, platform)
                                }
                            },
                            onCloseSlot = viewModel::closeSlotPicker,
                            onUseSlot = viewModel::resolveSlot,
                            onLoadMoreSlots = viewModel::loadMoreSlots,
                        )
                    }
                    if (current.page < current.totalPages) {
                        item {
                            LoadMoreButton(loading = current.loadingMore, onClick = viewModel::loadMoreCrossMatchCandidates)
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item {
            SectionHeader(
                "Vincular con archivo local",
                "Match automático de YouTube contra tu biblioteca local (por duración/fecha).",
                onRefresh = null,
            )
        }

        stats?.let { s -> item { StatsBar(s, syncing, onSync = viewModel::triggerSync) } }

        item {
            Text(
                "Revisión manual" + (stats?.let { " · ${it.revisar} pendientes" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        when (val current = reviewState) {
            is ReviewUiState.Loading -> item { LoadingRow() }
            is ReviewUiState.Error -> item { ErrorRow(current.message) }
            is ReviewUiState.Success -> {
                if (current.items.isEmpty()) {
                    item { EmptyRow("Todo revisado", "No hay videos pendientes de revisión.") }
                } else {
                    items(current.items, key = { "rv_${it._id}" }) { reviewItem ->
                        ReviewCard(
                            item = reviewItem,
                            busy = busyReviewId,
                            onLink = { fileId -> viewModel.confirmLink(reviewItem._id, fileId) },
                            onOrphan = { viewModel.markOrphan(reviewItem._id) },
                        )
                    }
                    if (current.totalPages > 1) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { viewModel.loadReview(current.page - 1) },
                                    enabled = current.page > 1,
                                ) { Text("‹ Anterior") }
                                Text(
                                    "${current.page} / ${current.totalPages}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                TextButton(
                                    onClick = { viewModel.loadReview(current.page + 1) },
                                    enabled = current.page < current.totalPages,
                                ) { Text("Siguiente ›") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, onRefresh: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorRow(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun EmptyRow(title: String, note: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadMoreButton(loading: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text(if (loading) "Cargando…" else "Cargar más")
    }
}

// ---- Emparejar entre plataformas ----

@Composable
private fun CandidateCard(
    candidate: CrossMatchCandidateDto,
    openSlot: SlotPickerState?,
    onToggleSlot: (CrossPlatform) -> Unit,
    onCloseSlot: () -> Unit,
    onUseSlot: (PlatformRecentItemDto) -> Unit,
    onLoadMoreSlots: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(candidate.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)

            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlatformChip("YT", candidate.resolved.youtube, openSlot?.platform == CrossPlatform.YOUTUBE) {
                    onToggleSlot(CrossPlatform.YOUTUBE)
                }
                PlatformChip("IG", candidate.resolved.instagram, openSlot?.platform == CrossPlatform.INSTAGRAM) {
                    onToggleSlot(CrossPlatform.INSTAGRAM)
                }
                PlatformChip("TT", candidate.resolved.tiktok, openSlot?.platform == CrossPlatform.TIKTOK) {
                    onToggleSlot(CrossPlatform.TIKTOK)
                }
            }

            if (candidate.resolved.youtube != null && candidate.resolved.instagram != null && candidate.resolved.tiktok != null) {
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Las 3 plataformas vinculadas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (openSlot != null) {
                SlotPickerPanel(openSlot, onClose = onCloseSlot, onUse = onUseSlot, onLoadMore = onLoadMoreSlots)
            }
        }
    }
}

@Composable
private fun PlatformChip(label: String, resolved: CrossMatchResolvedSlotDto?, open: Boolean, onClick: () -> Unit) {
    val active = resolved != null || open
    val containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.Bold)
        Icon(
            if (resolved != null) Icons.Filled.Check else Icons.Filled.Search,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(12.dp)
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun SlotPickerPanel(
    state: SlotPickerState,
    onClose: () -> Unit,
    onUse: (PlatformRecentItemDto) -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Buscando en ${state.platform.apiValue}…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClose) { Text("Cerrar") }
        }

        if (state.loading) {
            LoadingRow()
        } else if (state.items.isEmpty()) {
            EmptyRow("Sin resultados", "No se encontraron videos recientes en esta plataforma.")
        } else {
            state.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                    OutlinedButton(onClick = { onUse(item) }, enabled = state.resolvingId == null) {
                        Text(if (state.resolvingId == item.platformId) "…" else "Usar")
                    }
                }
            }
            if (state.cursor != null) {
                LoadMoreButton(loading = state.loadingMore, onClick = onLoadMore)
            }
        }
    }
}

// ---- Vincular con archivo local ----

@Composable
private fun StatsBar(stats: SyncStatsDto, syncing: Boolean, onSync: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("YouTube", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${stats.linked} de ${stats.youtube} vinculados",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onSync, enabled = !syncing) {
                    Text(if (syncing) "Sincronizando…" else "Re-sincronizar")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip("Vinculados", stats.linked, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatChip("Revisar", stats.revisar, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                StatChip("Huérfanos", stats.sinMatch, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewCard(
    item: SyncReviewItemDto,
    busy: String?,
    onLink: (String) -> Unit,
    onOrphan: () -> Unit,
) {
    val busyThisItem = busy == item._id
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Text(
                formatDuration(item.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.candidates.isEmpty()) {
                Text(
                    "Sin candidatos por duración",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    item.candidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.file_name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text(
                                    formatDuration(candidate.duracion_segundos),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { onLink(candidate._id) }, enabled = busy == null) {
                                Text(if (busyThisItem) "…" else "Vincular")
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onOrphan,
                enabled = busy == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(if (busyThisItem) "…" else "Marcar como huérfano")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
