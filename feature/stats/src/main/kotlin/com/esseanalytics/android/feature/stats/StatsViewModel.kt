package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(val items: List<GroupStatsItemDto>) : StatsUiState
    data class Error(val message: String) : StatsUiState
}

// GET /api/sync/group-stats(limit=5) -- mismo dato y misma vista que
// frontend/src/components/StatsView.tsx: los últimos videos ya vinculados en
// las 3 plataformas, comparados lado a lado. El matching es siempre por
// archivo, no depende de workflow_mode (simple/avanzado ven lo mismo acá).
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val syncApi: SyncApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = StatsUiState.Loading
            _uiState.value = try {
                StatsUiState.Success(syncApi.getGroupStats(limit = 5).items)
            } catch (e: Exception) {
                // Boundary real: llamada a la central, red/rol/caída.
                StatsUiState.Error(e.message ?: "No se pudieron cargar las estadísticas.")
            }
        }
    }
}
