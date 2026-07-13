package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.model.VideoFile

// Fase 1: lista de VideoFile (core:database) ya ingestados. Filtros por
// estado, tap-through a detalle y grid con miniaturas quedan para cuando
// exista un ThumbnailGenerator real (ver core:media) — por ahora, lista.
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
        Column(Modifier.padding(16.dp)) {
            Text(file.fileName, style = MaterialTheme.typography.titleMedium)
            val durationLabel = file.duracionSegundos?.let { "${it}s" } ?: "—"
            Text(
                "$durationLabel · ${file.resolucion ?: "—"} · ${file.status.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
