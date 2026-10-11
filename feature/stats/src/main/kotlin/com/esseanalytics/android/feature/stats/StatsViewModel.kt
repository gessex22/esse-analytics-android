package com.esseanalytics.android.feature.stats

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import retrofit2.Retrofit
import javax.inject.Inject

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): isRefreshing/
// refreshError dentro de Success -- mismo criterio que DashboardUiState.
sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(
        val items: List<GroupStatsItemDto>,
        val loadedFilter: StatsFilter,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : StatsUiState
    data class Error(val message: String) : StatsUiState
}

enum class StatsFilter(val apiValue: String?) {
    COMPARED(null), YOUTUBE("youtube"), INSTAGRAM("instagram"), TIKTOK("tiktok");

    val label: String get() = when (this) {
        COMPARED -> "Comparadas"
        YOUTUBE -> "YouTube"
        INSTAGRAM -> "Instagram"
        TIKTOK -> "TikTok"
    }

    // Comparadas trae los 3 links en el mismo item (null = sumar las 3 en el
    // gráfico/totales). En cualquier otro filtro el item ya viene con SOLO
    // esa plataforma poblada, así que el gráfico/totales tienen que acotarse
    // a ella -- si no, las otras 2 quedan como una línea plana en 0.
    val platform: Platform? get() = when (this) {
        COMPARED -> null
        YOUTUBE -> Platform.YOUTUBE
        INSTAGRAM -> Platform.INSTAGRAM
        TIKTOK -> Platform.TIKTOK
    }
}

// GET /api/sync/group-stats(limit=10) -- mismo dato y misma vista que
// frontend/src/components/StatsView.tsx: los últimos videos ya vinculados en
// las 3 plataformas, comparados lado a lado. El matching es siempre por
// archivo, no depende de workflow_mode (simple/avanzado ven lo mismo acá).
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore,
    private val refreshTracker: RefreshActivityTracker,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    private val _filter = MutableStateFlow(StatsFilter.COMPARED)
    val filter: StateFlow<StatsFilter> = _filter.asStateFlow()
    private var requestGeneration = 0L
    private var requestJob: Job? = null
    private data class FilterSnapshot(val items: List<GroupStatsItemDto>, val loadedAt: Long)
    // Cuatro filtros en memoria de esta vista; las actualizaciones manuales
    // siempre consultan la central. La sesión y el tiempo delimitan la reutilización.
    private val filterSnapshots = mutableMapOf<StatsFilter, FilterSnapshot>()
    private var cacheSession = tokenStore.token

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
        loadFilter(force = true)
    }

    private fun loadFilter(force: Boolean) {
        val requestedFilter = _filter.value
        val generation = ++requestGeneration
        requestJob?.cancel()
        requestJob = null
        val requestedSession = tokenStore.token
        if (cacheSession != requestedSession) {
            filterSnapshots.clear()
            cacheSession = requestedSession
            _uiState.value = StatsUiState.Loading
        }
        val cached = filterSnapshots[requestedFilter]
        if (!force && cached != null && SystemClock.elapsedRealtime() - cached.loadedAt < 30_000L) {
            _uiState.value = StatsUiState.Success(cached.items, loadedFilter = requestedFilter)
            refreshTracker.setRefreshing("stats", false)
            return
        }
        val current = (_uiState.value as? StatsUiState.Success)?.takeIf { it.loadedFilter == requestedFilter }
        val previous = current ?: cached?.let { StatsUiState.Success(it.items, loadedFilter = requestedFilter) }
        _uiState.value = previous?.copy(isRefreshing = true, refreshError = null) ?: StatsUiState.Loading
        requestJob = viewModelScope.launch {
            // Señal para AppTopBar -- ver RefreshActivityTracker.
            refreshTracker.setRefreshing("stats", true)
            try {
                val result = syncRepository.getGroupStats(limit = 10, platform = requestedFilter.apiValue, force = true)
                if (generation == requestGeneration) {
                    if (tokenStore.token == requestedSession) {
                        filterSnapshots[requestedFilter] = FilterSnapshot(result.items, SystemClock.elapsedRealtime())
                        _uiState.value = StatsUiState.Success(result.items, loadedFilter = requestedFilter)
                    } else {
                        filterSnapshots.clear()
                        _uiState.value = StatsUiState.Error("La sesión cambió. Vuelve a actualizar los datos.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Boundary real: llamada a la central, red/rol/caída. Con
                // datos previos: banner no bloqueante, no se pierde lo último
                // bueno. Sin datos previos: Error, pantalla completa.
                if (generation == requestGeneration) {
                    val message = e.message ?: "No se pudieron cargar las estadísticas."
                    _uiState.value = if (tokenStore.token == requestedSession) {
                        previous?.copy(isRefreshing = false, refreshError = message) ?: StatsUiState.Error(message)
                    } else {
                        filterSnapshots.clear()
                        StatsUiState.Error("La sesión cambió. Vuelve a actualizar los datos.")
                    }
                }
            } finally {
                if (generation == requestGeneration) refreshTracker.setRefreshing("stats", false)
            }
        }
    }

    fun setFilter(filter: StatsFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        loadFilter(force = false)
    }
}
