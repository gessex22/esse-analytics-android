package com.esseanalytics.android.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 2: GET /api/sync/group-stats (core:network SyncApi, ya armado) + donut
// de Canvas a mano — mismos colores de marca que designsystem/theme/Color.kt
// (YouTube #ef4444 / Instagram #a855f7 / TikTok #ec4899), igual que el donut
// que ya existe en frontend/src/components/StatsView.tsx.
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Estadísticas",
        note = "Vistas por plataforma — Fase 2",
        modifier = modifier,
    )
}
