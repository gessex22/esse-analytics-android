package com.esseanalytics.android.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// MVP parcial: estado de conexión de las 3 plataformas + trigger de sync.
// Review/cross-match completo (paridad con SyncPanel.tsx del frontend web) es
// Fase 2 — ver el plan.
@Composable
fun SyncScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Sincronización",
        note = "Estado de conexión de plataformas — Fase 1, review completo en Fase 2",
        modifier = modifier,
    )
}
