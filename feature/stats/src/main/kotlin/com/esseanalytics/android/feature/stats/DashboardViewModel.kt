package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.WorkflowMode
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DashboardData(
    val items: List<GroupStatsItemDto>,
    val calendar: List<CalendarConfigDto>,
    val latestHistory: UploadHistoryItemDto?,
    // Stats puntuales del último publicado cuando NO aparece en `items` --
    // group-stats solo trae videos ya cross-posteados a las 3 plataformas, y
    // el más reciente puede no estarlo todavía. Sin esto, DashboardScreen
    // mostraba la tarjeta con todo en 0 / "Pendiente de datos" aunque el
    // video sí tuviera métricas reales en al menos una red.
    val fallbackItem: GroupStatsItemDto? = null,
    // Plataforma real de la última subida, solo cuando el modo es AVANZADO --
    // mirror de focusPlatform en DashboardView.tsx (escritorio) y
    // DashboardView.swift (iOS). En modo simple queda null: ahí publicar
    // cross-postea a las 3 juntas, así que sumar sus métricas tiene sentido.
    val focusPlatform: Platform? = null,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val data: DashboardData) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            val result = supervisorScope {
                val stats = async { runCatching { syncRepository.getGroupStats(limit = 5) } }
                val calendar = async { runCatching { syncRepository.getCalendarConfig() } }
                val history = async { runCatching { syncRepository.getHistory(limit = 1, force = true) } }
                val workflowMode = async { runCatching { settingsStore.workflowMode.first() } }
                Triple(stats.await(), calendar.await(), history.await()) to workflowMode.await()
            }
            val (triple, workflowModeResult) = result
            val stats = triple.first
            if (stats.isFailure) {
                _uiState.value = DashboardUiState.Error(
                    stats.exceptionOrNull()?.message ?: "No se pudo cargar el dashboard.",
                )
            } else {
                val items = stats.getOrThrow().items
                val latestHistory = triple.third.getOrDefault(emptyList()).firstOrNull()
                val historyPlatform = latestHistory?.platform?.let { Platform.fromApiValue(it) }
                val alreadyMatched = latestHistory == null || items.any { entry ->
                    entry.fileName == latestHistory.fileName ||
                        (historyPlatform != null && entry.platforms[historyPlatform.apiValue]?.platformId == latestHistory.platformId)
                }
                val fallbackItem = if (!alreadyMatched) {
                    latestHistory?.fileName?.let { fileName ->
                        runCatching { syncRepository.getFileStats(fileName = fileName) }.getOrNull()
                    }
                } else null
                val isSimpleFlow = workflowModeResult.getOrDefault(WorkflowMode.SIMPLE) == WorkflowMode.SIMPLE
                _uiState.value = DashboardUiState.Success(
                    DashboardData(
                        items = items,
                        calendar = triple.second.getOrDefault(emptyList()),
                        latestHistory = latestHistory,
                        fallbackItem = fallbackItem,
                        focusPlatform = if (isSimpleFlow) null else historyPlatform,
                    ),
                )
            }
        }
    }
}
