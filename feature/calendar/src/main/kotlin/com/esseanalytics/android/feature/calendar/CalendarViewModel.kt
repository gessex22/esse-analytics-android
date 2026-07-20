package com.esseanalytics.android.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CalendarSlot(
    val platform: String,
    val lastPublishedTitle: String,
    val lastPublishedDate: String,
    val intervalDays: Int,
    val nextFileName: String?,
    // lastPublishedDate + intervalDays -- mismo cálculo que calcNextDate() en
    // frontend/src/data/mockPublishingData.ts (consumido por
    // PublishingQueue.tsx en desktop). null si todavía no hay una fecha base
    // (plataforma sin nada publicado todavía, la central manda "").
    val nextDate: LocalDate?,
)

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(val slots: List<CalendarSlot>) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

// GET /api/sync/calendar-config alimenta el Calendario real de desktop
// (PublishingQueue.tsx) -- la cadencia por plataforma (último publicado,
// cada cuántos días, qué sigue) vive en la central, no en Room local. Acá se
// consume en modo lectura; drag-drop/edición de fechas queda para Fase 2.
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val syncApi: SyncApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CalendarUiState.Loading
            _uiState.value = try {
                CalendarUiState.Success(syncApi.getCalendarConfig().map { it.toSlot() })
            } catch (e: Exception) {
                // Boundary real: llamada a la central, puede fallar por red,
                // rol sin permiso (varios endpoints de /api/sync/* son
                // todopoderoso-only), o estar caída.
                CalendarUiState.Error(e.message ?: "No se pudo cargar el calendario.")
            }
        }
    }

    // nextVideoId es un ObjectId de Mongo o un título (nunca un id de Room) --
    // la central ya lo resuelve contra FileModel y lo manda listo en
    // nextVideo.title (ver CalendarConfigDto), no hace falta re-resolverlo acá.
    private fun CalendarConfigDto.toSlot(): CalendarSlot = CalendarSlot(
        platform = platform,
        lastPublishedTitle = lastPublishedTitle,
        lastPublishedDate = lastPublishedDate,
        intervalDays = intervalDays,
        nextFileName = nextVideo?.title,
        // lastPublishedDate llega en "yyyy-MM-dd" (ver
        // local-backend/backend: new Date().toISOString().slice(0, 10)) o ""
        // si la plataforma todavía no tiene nada publicado -- ahí no hay
        // fecha base de la que calcular la próxima.
        nextDate = lastPublishedDate.takeIf { it.isNotBlank() }
            ?.runCatching { LocalDate.parse(this) }
            ?.getOrNull()
            ?.plusDays(intervalDays.toLong()),
    )
}
