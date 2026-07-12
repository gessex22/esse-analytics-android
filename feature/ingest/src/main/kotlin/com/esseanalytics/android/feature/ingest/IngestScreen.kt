package com.esseanalytics.android.feature.ingest

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 1: Activity con intent-filter ACTION_SEND/ACTION_SEND_MULTIPLE +
// video/*, selector SAF (OpenMultipleDocuments), ImportUseCase compartido
// entre ambos. Ver el plan, sección "Ingesta de videos".
@Composable
fun IngestScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Importar video",
        note = "Compartir desde Galería / selector de archivos — Fase 1",
        modifier = modifier,
    )
}
