package com.esseanalytics.android.feature.stats

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PublishOperationStore
import com.esseanalytics.android.core.network.CatalogRegistrationOutbox
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.*
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import retrofit2.Retrofit
import java.time.ZoneId
import javax.inject.Inject

enum class StatsSegment(val label: String) { COMPARED("Comparadas"), RECENT("Recientes"), PLATFORMS("Plataformas") }
data class StatsUiState(
    val segment: StatsSegment = StatsSegment.COMPARED,
    val items: List<GroupStatsItemDto> = emptyList(),
    val history: List<UploadHistoryItemDto> = emptyList(),
    val weeks: List<WeeklyStatsWeekDto> = emptyList(),
    val loading: Boolean = true, val loadingMore: Boolean = false, val loaded: Boolean = false,
    val error: String? = null, val warning: String? = null,
    val historyTotal: Int = 0,
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
) { val hasMore: Boolean get() = history.size < historyTotal }
internal fun statsError(error: Exception, section: String): String = when {
    error is HttpException && error.code() in listOf(404,405) -> "El servidor necesita actualizarse para mostrar $section."
    error is HttpException && error.code() == 401 -> "Tu sesión venció. Vuelve a iniciar sesión."
    else -> "No se pudo cargar $section. Comprueba la conexión y vuelve a intentarlo."
}

internal data class LocalHistoryIssue(val id: Long, val fileId: Long?, val platform: String, val title: String, val message: String)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val syncRepository: SyncRepository, private val api: SyncApi,
    private val tokenStore: TokenStore, private val refreshTracker: RefreshActivityTracker,
    settings: SettingsStore, operations: PublishOperationStore, files: FileRepository, identity: CausalPlatformDao,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState = _uiState.asStateFlow()
    private var generation = 0L
    private var request: Job? = null
    private var session = tokenStore.token
    private val snapshots = mutableMapOf<StatsSegment, Pair<StatsUiState, Long>>()
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val localIssues = combine(tokenStore.authState, settings.serverUrl) { _, server ->
        tokenStore.currentSession()?.let { it.second to CatalogRegistrationOutbox.scope(it.second, server) }
    }.distinctUntilChanged().flatMapLatest { scope ->
        if (scope == null) flowOf(emptyList()) else combine(operations.observeBlocked(scope.first), files.observeAll()) { rows, local ->
            rows.mapNotNull { row ->
                val file = local.firstOrNull { it.clientFileId == row.sourceId } ?: return@mapNotNull null
                // Legacy journals use user id; show them here only with a proven identity in this server scope.
                if (identity.findContentId(scope.second, CausalKeys.forClientFile(file.clientFileId)) == null) return@mapNotNull null
                LocalHistoryIssue(row.id, file.id, row.platform, file.libraryDisplayName, row.lastError ?: "Revisión de publicación pendiente")
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { refresh() }
    fun setHistoryFilter(filter: HistoryFilter) {
        if (_uiState.value.historyFilter == filter) return
        snapshots.remove(StatsSegment.RECENT)
        _uiState.value = StatsUiState(segment = StatsSegment.RECENT, historyFilter = filter)
        refresh()
    }
    fun thumbnailUrl(item: GroupStatsItemDto): String? = item.remoteLibraryVideoId?.let {
        remoteLibraryThumbnailUrl(retrofit.baseUrl(), it, item.thumbnailStoredFileName, tokenStore.token)
    } ?: item.thumbnail
    fun select(segment: StatsSegment) {
        if (segment == _uiState.value.segment) return
        ++generation; request?.cancel(); refreshTracker.setRefreshing("stats", false)
        if (session != tokenStore.token) { session = tokenStore.token; snapshots.clear() }
        val cached = snapshots[segment]
        _uiState.value = cached?.first ?: StatsUiState(segment)
        if (cached == null || SystemClock.elapsedRealtime() - cached.second >= 30_000) refresh()
    }
    fun refresh() = load(false)
    fun loadMore() { if (_uiState.value.segment == StatsSegment.RECENT && _uiState.value.hasMore && !_uiState.value.loading && !_uiState.value.loadingMore) load(true) }
    private fun load(more: Boolean) {
        val id = ++generation; request?.cancel()
        val token = tokenStore.token
        if (session != token) { session = token; snapshots.clear(); _uiState.value = StatsUiState(_uiState.value.segment) }
        val previous = _uiState.value
        val segment = previous.segment
        _uiState.value = previous.copy(loading = !more, loadingMore = more, error = null, warning = null)
        request = viewModelScope.launch {
            refreshTracker.setRefreshing("stats", true)
            try {
                val result = when(segment) {
                    StatsSegment.COMPARED -> previous.copy(items = syncRepository.getGroupStats(limit = 10, force = true).items)
                    StatsSegment.RECENT -> {
                        val page = api.getHistory(limit = 30, offset = if (more) previous.history.size else 0, platform = previous.historyFilter.apiValue, includeStats = true)
                        previous.copy(history = if (more) previous.history + page.items else page.items, historyTotal = page.total)
                    }
                    StatsSegment.PLATFORMS -> previous.copy(weeks = api.getWeeklyStats(ZoneId.systemDefault().id).weeks)
                }.copy(loading = false, loadingMore = false, loaded = true, error = null, warning = null)
                ensureActive()
                if (id == generation && tokenStore.token == token) {
                    _uiState.value = result; snapshots[segment] = result to SystemClock.elapsedRealtime()
                } else if (id == generation) { snapshots.clear(); _uiState.value = StatsUiState(segment, loading = false, error = "La sesión cambió. Actualiza los datos.") }
            } catch (e: CancellationException) { throw e }
            catch(e: Exception) {
                if (id == generation) {
                    _uiState.value = if (tokenStore.token != token) StatsUiState(segment, loading = false, error = "La sesión cambió. Actualiza los datos.")
                    else previous.copy(loading = false, loadingMore = false, error = statsError(e, segment.label.lowercase()))
                }
            } finally { if (id == generation) refreshTracker.setRefreshing("stats", false) }
        }
    }
}
