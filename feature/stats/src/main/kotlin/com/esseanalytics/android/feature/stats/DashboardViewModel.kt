package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.WorkflowMode
import com.esseanalytics.android.core.network.HistoryOutbox
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
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
import retrofit2.Retrofit

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
    val individualItems: List<GroupStatsItemDto> = emptyList(),
    val podiumMode: DashboardPodiumMode = DashboardPodiumMode.COMBINED,
)

enum class DashboardPodiumMode { COMBINED, INDIVIDUAL }

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): isRefreshing/
// refreshError viven DENTRO de Success, no como un 4to caso del sealed
// interface -- así el compilador obliga a que solo puedan existir cuando ya
// hay `data` para mostrar. Antes cada refresh() pisaba todo con `Loading`
// incondicionalmente, vaciando la pantalla en cada pull-to-refresh o tras
// publicar, no solo en la carga inicial.
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val data: DashboardData,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val refreshTracker: RefreshActivityTracker,
    private val historyOutbox: HistoryOutbox,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun thumbnailUrl(item: GroupStatsItemDto): String? {
        val videoId = item.remoteLibraryVideoId ?: return null
        return remoteLibraryThumbnailUrl(retrofit.baseUrl(), videoId, item.thumbnailStoredFileName, tokenStore.token)
    }

    init {
        // force=false: la primera carga (o un ViewModel recreado mientras la
        // caché sigue tibia por otra pantalla) puede aprovechar
        // SyncRepository -- el bypass real de caché es responsabilidad
        // exclusiva del pull-to-refresh explícito (ver refresh()).
        load(force = false)
    }

    fun refresh() {
        load(force = true)
    }

    private fun load(force: Boolean) {
        // Fire-and-forget: vacía el outbox de publicaciones que no llegaron a
        // la central (SYNC-02#4, ver HistoryOutbox) en cada apertura de
        // Dashboard/pull-to-refresh, sin bloquear ni retrasar el resto de
        // esta carga -- si falla, no afecta el resto de load() ni su estado.
        viewModelScope.launch { historyOutbox.flush() }
        viewModelScope.launch {
            // `previous` capturado UNA vez acá -- es la señal de "ya hay
            // datos en pantalla" para todo este intento, sin importar cómo
            // termine. No puede desincronizarse porque es un val local, no
            // se relee mid-flight.
            val previous = _uiState.value as? DashboardUiState.Success
            val podiumMode = previous?.data?.podiumMode ?: DashboardPodiumMode.COMBINED
            _uiState.value = if (previous != null) {
                previous.copy(isRefreshing = true, refreshError = null)
            } else {
                DashboardUiState.Loading
            }
            // Señal para AppTopBar (pedido del usuario: spinner en la barra
            // compartida, no en el contenido) -- ver RefreshActivityTracker.
            refreshTracker.setRefreshing("dashboard", true)
            val result = supervisorScope {
                val stats = async { runCatching { syncRepository.getGroupStats(limit = 5, force = force) } }
                val individualStats = Platform.publishable.map { platform ->
                    async { runCatching { syncRepository.getGroupStats(limit = 5, platform = platform.apiValue, force = force) } }
                }
                val calendar = async { runCatching { syncRepository.getCalendarConfig(force = force) } }
                val history = async { runCatching { syncRepository.getHistory(limit = 1, force = true) } }
                val workflowMode = async { runCatching { settingsStore.workflowMode.first() } }
                Triple(stats.await(), calendar.await(), history.await()) to Pair(workflowMode.await(), individualStats.map { it.await() })
            }
            val (triple, supplementary) = result
            val (workflowModeResult, individualStats) = supplementary
            val stats = triple.first
            if (stats.isFailure) {
                val message = stats.exceptionOrNull()?.message ?: "No se pudo cargar el dashboard."
                // Con datos previos: banner no bloqueante, se mantiene lo
                // último bueno en pantalla. Sin datos previos: Error, que sí
                // gatea la pantalla completa (ver DashboardScreen.kt).
                _uiState.value = previous?.copy(isRefreshing = false, refreshError = message)
                    ?: DashboardUiState.Error(message)
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
                        individualItems = individualStats.flatMap { it.getOrNull()?.items.orEmpty() },
                        podiumMode = podiumMode,
                    ),
                )
            }
            refreshTracker.setRefreshing("dashboard", false)
        }
    }

    fun setPodiumMode(mode: DashboardPodiumMode) {
        val current = _uiState.value as? DashboardUiState.Success ?: return
        if (current.data.podiumMode == mode) return
        _uiState.value = DashboardUiState.Success(current.data.copy(podiumMode = mode))
    }
}
