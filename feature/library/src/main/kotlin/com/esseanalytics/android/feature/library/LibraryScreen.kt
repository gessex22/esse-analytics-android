package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import java.io.File

// Historial de todo lo importado -- no solo una lista, cada tarjeta muestra
// el estado real por plataforma (publicado/descartado/pendiente, ver
// VideoFile.platforms/platformsDiscarded) y tocarla lleva directo a Subir
// con ese archivo ya elegido (ver EsseAnalyticsNavHost, ruta upload con
// fileId). Miniatura real (AndroidFrameThumbnailGenerator, ver core:media).
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit = {},
    onVideoClick: (VideoFile) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val files by viewModel.files.collectAsState()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onImportClick) {
                Icon(Icons.Filled.Add, contentDescription = "Importar video")
            }
        },
    ) { padding ->
        if (files.isEmpty()) {
            PlaceholderScreen(
                title = "Todavía no hay videos",
                note = "Tocá + para importar uno, o compartilo desde Galería con \"Compartir → EsseAnalytics\".",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.id }) { file -> VideoFileCard(file, onClick = { onVideoClick(file) }) }
            }
        }
    }
}

@Composable
private fun VideoFileCard(file: VideoFile, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumbnail(file.thumbnailPath)

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(file.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val durationLabel = file.duracionSegundos?.let { "${it}s" } ?: "—"
                Text(
                    "$durationLabel · ${file.resolucion ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlatformBadgeRow(file)
            }
        }
    }
}

// Verde/color de marca = publicado, tachado y apagado = descartado (modo
// Simple auto-descarta las otras al publicar una, ver FileRepository),
// contorno vacío = todavía pendiente.
@Composable
private fun PlatformBadgeRow(file: VideoFile) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Platform.publishable.forEach { platform ->
            val state = when {
                platform in file.platforms -> PlatformBadgeState.PUBLISHED
                platform in file.platformsDiscarded -> PlatformBadgeState.DISCARDED
                else -> PlatformBadgeState.PENDING
            }
            PlatformBadge(platform, state)
        }
    }
}

private enum class PlatformBadgeState { PUBLISHED, DISCARDED, PENDING }

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
private fun PlatformBadge(platform: Platform, state: PlatformBadgeState) {
    val color = platformColor(platform)
    val (background, content) = when (state) {
        PlatformBadgeState.PUBLISHED -> color.copy(alpha = 0.15f) to color
        PlatformBadgeState.DISCARDED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        PlatformBadgeState.PENDING -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            platformShortLabel(platform).take(1),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VideoThumbnail(thumbnailPath: String?) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailPath != null) {
            AsyncImage(
                model = File(thumbnailPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
