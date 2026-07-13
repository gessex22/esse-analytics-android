package com.esseanalytics.android.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 1 (básico): cadencia de publicación por plataforma -- último
// publicado, cada cuántos días toca, y qué archivo local sigue en la cola.
// Mismo dato que ya expone la central (GET /api/sync/calendar-config), que
// alimenta el Calendario real de desktop (PublishingQueue.tsx) -- acá se
// muestra en modo lectura, sin drag-drop ni edición todavía (Fase 2).
@Composable
fun CalendarScreen(modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    when (val current = state) {
        is CalendarUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        is CalendarUiState.Error -> PlaceholderScreen(
            title = "No se pudo cargar",
            note = current.message,
            icon = Icons.Filled.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )

        is CalendarUiState.Success -> if (current.slots.isEmpty()) {
            PlaceholderScreen(
                title = "Todavía no hay nada programado",
                note = "Publicá al menos un video en cada plataforma para que arranque la cadencia.",
                icon = Icons.Filled.CalendarMonth,
                modifier = modifier,
            )
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current.slots, key = { it.platform }) { slot -> CalendarSlotCard(slot) }
            }
        }
    }
}

@Composable
private fun CalendarSlotCard(slot: CalendarSlot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(slot.platform, style = MaterialTheme.typography.titleMedium)
            Text(
                "Último: ${slot.lastPublishedTitle} (${slot.lastPublishedDate})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Cada ${slot.intervalDays} días",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Sigue: ${slot.nextFileName ?: "sin definir"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
