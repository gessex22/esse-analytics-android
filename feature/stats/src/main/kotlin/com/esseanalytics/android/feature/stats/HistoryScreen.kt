package com.esseanalytics.android.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Historial de subidas -- paridad con HistoryView.tsx (desktop, Fase 3 del
// plan de estabilidad/UX). Accesible desde "Más" (ver EsseAnalyticsNavHost) --
// Dashboard sigue mostrando solo el último evento, esto es la vista completa.
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        FilterChipsRow(selected = uiState.filter, onSelect = viewModel::setFilter)
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null -> HistoryErrorState(message = uiState.error!!, onRetry = viewModel::retry)
            uiState.items.isEmpty() -> HistoryEmptyState()
            else -> HistoryList(
                items = uiState.items,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = viewModel::loadMore,
            )
        }
    }
}

@Composable
private fun FilterChipsRow(selected: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HistoryFilter.entries) { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
        )
        Text(
            "No se pudo cargar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Reintentar") }
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Text(
            "Sin subidas todavía",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Cada video que publiques desde acá va a quedar registrado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HistoryList(items: List<UploadHistoryItemDto>, isLoadingMore: Boolean, onLoadMore: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            HistoryRow(item)
            // Prefetch a 5 filas del final -- mismo umbral que los
            // paginadores de Biblioteca remota. LaunchedEffect(item.id), no
            // una llamada directa en el cuerpo del composable -- dispara UNA
            // vez cuando esta fila entra en composición (mismo efecto que
            // .onAppear en iOS), no en cada recomposición.
            if (index >= items.size - 5) {
                LaunchedEffect(item.id) { onLoadMore() }
            }
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: UploadHistoryItemDto) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(platformColor(item.platform).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = platformIcon(item.platform)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = platformColor(item.platform), modifier = Modifier.size(16.dp))
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(
                item.fileName ?: item.title ?: "Sin título",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${platformFullLabel(item.platform)} · ${formatHistoryDate(item.publishedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                sourceLabel(item.source, item.deviceId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // item.platformUrl es un val de un DTO declarado en otro módulo
        // (core:network) -- Kotlin no puede smart-cast String? -> String
        // ahí (solo funciona con propiedades del mismo módulo), rompía
        // compileDebugKotlin. Capturar en un val local sí smart-castea.
        val platformUrl = item.platformUrl
        if (platformUrl != null) {
            IconButton(onClick = { uriHandler.openUri(platformUrl) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Ver", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun platformColor(platform: String): Color = when (platform) {
    "youtube" -> YoutubeRed
    "instagram" -> InstagramPurple
    "tiktok" -> TiktokPink
    else -> Color.Gray
}

private fun platformIcon(platform: String): ImageVector? = when (platform) {
    "youtube" -> PlatformIcons.YoutubeLogo
    "instagram" -> PlatformIcons.InstagramLogo
    "tiktok" -> PlatformIcons.TiktokLogo
    else -> null
}

private fun platformFullLabel(platform: String): String = when (platform) {
    "youtube" -> "YouTube"
    "instagram" -> "Instagram"
    "tiktok" -> "TikTok"
    "facebook" -> "Facebook"
    else -> platform.replaceFirstChar { it.uppercase() }
}

private fun sourceLabel(source: String?, deviceId: String?): String {
    val labels = mapOf("pc" to "PC", "android" to "Android", "ios" to "iPhone/iPad", "web" to "Web")
    val label = labels[source] ?: source ?: "dispositivo desconocido"
    return if (deviceId != null) "$label · ${deviceId.take(8)}" else label
}

private fun formatHistoryDate(iso: String): String = runCatching {
    val instant = Instant.parse(iso)
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault("—")
