package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter(val apiValue: String?, val label: String) {
    ALL(null, "Todos"),
    YOUTUBE("youtube", "YouTube"),
    INSTAGRAM("instagram", "Instagram"),
    TIKTOK("tiktok", "TikTok"),
    FACEBOOK("facebook", "Facebook"),
}

data class HistoryUiState(
    val items: List<UploadHistoryItemDto> = emptyList(),
    val total: Int = 0,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
) {
    val hasMore: Boolean get() = items.size < total
}

// Historial de subidas -- paridad con HistoryView.tsx (desktop, Fase 3 del
// plan de estabilidad/UX): mismo endpoint (GET /api/sync/history), mismos
// filtros por plataforma. Carga paginada estilo scroll infinito (mismo
// criterio que los paginadores ya existentes en Biblioteca remota), no
// page-by-page como el desktop -- más natural en mobile.
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val syncApi: SyncApi,
) : ViewModel() {
    private val pageSize = 30

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load(HistoryFilter.ALL)
    }

    fun setFilter(filter: HistoryFilter) {
        if (filter == _uiState.value.filter) return
        load(filter)
    }

    fun retry() {
        load(_uiState.value.filter)
    }

    // Llamalo tras una publicación confirmada (mismo criterio que
    // dashboardRefreshTrigger en iOS/AppTabRouter) para que el historial
    // muestre lo recién publicado sin depender de que el usuario haga
    // pull-to-refresh a mano.
    fun refresh() {
        load(_uiState.value.filter)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.isLoading) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            runCatching { syncApi.getHistory(limit = pageSize, offset = state.items.size, platform = state.filter.apiValue) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items + response.items,
                        total = response.total,
                        isLoadingMore = false,
                    )
                }
                .onFailure {
                    // Best-effort -- un error de "cargar más" no pisa lo ya
                    // cargado, el usuario puede reintentar bajando de nuevo.
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    private fun load(filter: HistoryFilter) {
        viewModelScope.launch {
            _uiState.value = HistoryUiState(filter = filter, isLoading = true)
            runCatching { syncApi.getHistory(limit = pageSize, offset = 0, platform = filter.apiValue) }
                .onSuccess { response ->
                    _uiState.value = HistoryUiState(
                        items = response.items,
                        total = response.total,
                        filter = filter,
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = HistoryUiState(
                        filter = filter,
                        isLoading = false,
                        error = error.message ?: "No se pudo cargar el historial.",
                    )
                }
        }
    }
}
