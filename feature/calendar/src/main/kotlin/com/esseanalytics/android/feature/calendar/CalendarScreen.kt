package com.esseanalytics.android.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 1 (básico): fechas programadas + GET/PATCH /api/sync/calendar-config
// (core:network SyncApi, ya armado).
@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Calendario",
        note = "Próximas publicaciones — Fase 1",
        modifier = modifier,
    )
}
