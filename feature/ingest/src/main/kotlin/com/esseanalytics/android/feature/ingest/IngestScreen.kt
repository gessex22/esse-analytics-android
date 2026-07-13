package com.esseanalytics.android.feature.ingest

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// Dos caminos de entrada, mismo pipeline (ImportUseCase): Share Sheet desde
// otra app (pendingUris, ver MainActivity/EsseAnalyticsNavHost) y el selector
// SAF de acá abajo. Ver el plan, sección "Ingesta de videos".
@Composable
fun IngestScreen(
    pendingUris: List<Uri> = emptyList(),
    onPendingUrisConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: IngestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(pendingUris) {
        if (pendingUris.isNotEmpty()) {
            viewModel.importUris(pendingUris)
            onPendingUrisConsumed()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importUris(uris) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val current = state) {
            is IngestUiState.Idle -> {
                Text("Importar video", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Compartilo desde Galería con \"Compartir → EsseAnalytics\", o elegilo acá.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
                Button(
                    onClick = { pickerLauncher.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Elegir video") }
            }

            is IngestUiState.Importing -> CircularProgressIndicator()

            is IngestUiState.Success -> {
                Text(
                    "Importado: ${current.file.fileName}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = viewModel::resetState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Importar otro") }
            }

            is IngestUiState.Duplicate -> {
                Text(
                    "Ya tenías \"${current.existing.fileName}\" importado — parece un duplicado.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = viewModel::resetState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Entendido") }
            }

            is IngestUiState.Error -> {
                Text(
                    current.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = viewModel::resetState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Reintentar") }
            }
        }
    }
}
