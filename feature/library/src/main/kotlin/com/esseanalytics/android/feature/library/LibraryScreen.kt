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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.model.VideoFile
import java.io.File

// Fase 1: lista de VideoFile (core:database) ya ingestados, con miniatura
// real (AndroidFrameThumbnailGenerator, ver core:media -- un frame
// recortado al centro, sin el blur de fondo estilo YT Studio que sí tiene
// desktop y necesita ffmpeg de verdad). Filtros por estado y tap-through a
// detalle quedan pendientes.
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit = {},
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
                items(files, key = { it.id }) { file -> VideoFileCard(file) }
            }
        }
    }
}

@Composable
private fun VideoFileCard(file: VideoFile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumbnail(file.thumbnailPath)

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(file.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val durationLabel = file.duracionSegundos?.let { "${it}s" } ?: "—"
                Text(
                    "$durationLabel · ${file.resolucion ?: "—"} · ${file.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
