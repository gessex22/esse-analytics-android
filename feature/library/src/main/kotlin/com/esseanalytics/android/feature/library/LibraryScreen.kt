package com.esseanalytics.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 1: lista/grid de VideoFile (core:database), filtros por estado,
// tap-through a detalle. Ver el plan, sección "Datos locales (Room)".
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Videos",
        note = "Biblioteca local — Fase 1",
        modifier = modifier,
    )
}
