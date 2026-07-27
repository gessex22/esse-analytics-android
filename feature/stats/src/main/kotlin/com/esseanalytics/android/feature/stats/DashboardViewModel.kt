package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

data class DashboardData(
    val items: List<GroupStatsItemDto>,
    val calendar: List<CalendarConfigDto>,
    val latestHistory: UploadHistoryItemDto?,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val data: DashboardData) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
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
                Triple(stats.await(), calendar.await(), history.await())
            }
            val stats = result.first
            if (stats.isFailure) {
                _uiState.value = DashboardUiState.Error(
                    stats.exceptionOrNull()?.message ?: "No se pudo cargar el dashboard.",
                )
            } else {
                _uiState.value = DashboardUiState.Success(
                    DashboardData(
                        items = stats.getOrThrow().items,
                        calendar = result.second.getOrDefault(emptyList()),
                        latestHistory = result.third.getOrDefault(emptyList()).firstOrNull(),
                    ),
                )
            }
        }
    }
}
