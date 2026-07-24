package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
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
    private val tokenStore: TokenStore,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    // Mirror de RemoteLibraryAPI.thumbnailURL(id:thumbnailStoredFileName:) en
    // iOS -- pide la miniatura de ESE video puntual (group-stats ya manda el
    // id resuelto server-side), no depende de traer un batch de la cola
    // remota. Null si el item no tiene match en Biblioteca remota o si no
    // tiene sesión.
    fun thumbnailUrl(item: GroupStatsItemDto): String? {
        val videoId = item.remoteLibraryVideoId ?: return null
        return remoteLibraryThumbnailUrl(retrofit.baseUrl(), videoId, item.thumbnailStoredFileName, tokenStore.token)
    }

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
