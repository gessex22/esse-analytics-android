package com.esseanalytics.android.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.ConfirmLinkRequest
import com.esseanalytics.android.core.network.dto.CrossMatchCandidateDto
import com.esseanalytics.android.core.network.dto.CrossMatchResolvedSlotDto
import com.esseanalytics.android.core.network.dto.PlatformRecentItemDto
import com.esseanalytics.android.core.network.dto.ResolveCrossMatchSlotRequest
import com.esseanalytics.android.core.network.dto.SyncReviewItemDto
import com.esseanalytics.android.core.network.dto.SyncStatsDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CrossPlatform(val apiValue: String) { YOUTUBE("youtube"), INSTAGRAM("instagram"), TIKTOK("tiktok") }

sealed interface CrossMatchUiState {
    data object Loading : CrossMatchUiState
    data class Success(
        val candidates: List<CrossMatchCandidateDto>,
        val page: Int,
        val totalPages: Int,
        val loadingMore: Boolean = false,
    ) : CrossMatchUiState
    data class Error(val message: String) : CrossMatchUiState
}

data class SlotPickerState(
    val fileId: String,
    val platform: CrossPlatform,
    val items: List<PlatformRecentItemDto> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val resolvingId: String? = null,
)

sealed interface ReviewUiState {
    data object Loading : ReviewUiState
    data class Success(val items: List<SyncReviewItemDto>, val page: Int, val totalPages: Int) : ReviewUiState
    data class Error(val message: String) : ReviewUiState
}

// Puerto de frontend/src/components/SyncPanel.tsx -- dos flujos independientes
// en la misma pantalla (ver el plan, "Sync completo"):
// 1) Emparejar entre plataformas: arranca de archivos locales con las 3
//    badges ya marcadas, completa solo los links que faltan.
// 2) Vincular con archivo local: match automático de YouTube por
//    duración/fecha contra la biblioteca, con cola de revisión manual.
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncApi: SyncApi,
) : ViewModel() {

    private val _crossMatchState = MutableStateFlow<CrossMatchUiState>(CrossMatchUiState.Loading)
    val crossMatchState: StateFlow<CrossMatchUiState> = _crossMatchState.asStateFlow()

    private val _slotPicker = MutableStateFlow<SlotPickerState?>(null)
    val slotPicker: StateFlow<SlotPickerState?> = _slotPicker.asStateFlow()

    private val _stats = MutableStateFlow<SyncStatsDto?>(null)
    val stats: StateFlow<SyncStatsDto?> = _stats.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _reviewState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val reviewState: StateFlow<ReviewUiState> = _reviewState.asStateFlow()

    private val _busyReviewId = MutableStateFlow<String?>(null)
    val busyReviewId: StateFlow<String?> = _busyReviewId.asStateFlow()

    init {
        loadCrossMatchCandidates()
        loadStats()
        loadReview(1)
    }

    // ---- Emparejar entre plataformas ----

    fun loadCrossMatchCandidates() {
        viewModelScope.launch {
            _crossMatchState.value = CrossMatchUiState.Loading
            _crossMatchState.value = try {
                val res = syncApi.getCrossMatchCandidates(page = 1, limit = 20)
                CrossMatchUiState.Success(res.items, res.page, res.totalPages)
            } catch (e: Exception) {
                CrossMatchUiState.Error(e.message ?: "No se pudieron cargar los candidatos.")
            }
        }
    }

    fun loadMoreCrossMatchCandidates() {
        val current = _crossMatchState.value
        if (current !is CrossMatchUiState.Success || current.page >= current.totalPages) return
        viewModelScope.launch {
            _crossMatchState.value = current.copy(loadingMore = true)
            _crossMatchState.value = try {
                val res = syncApi.getCrossMatchCandidates(page = current.page + 1, limit = 20)
                CrossMatchUiState.Success(current.candidates + res.items, res.page, res.totalPages)
            } catch (e: Exception) {
                current.copy(loadingMore = false)
            }
        }
    }

    fun openSlotPicker(fileId: String, platform: CrossPlatform) {
        _slotPicker.value = SlotPickerState(fileId, platform)
        loadSlotPage(null)
    }

    fun closeSlotPicker() {
        _slotPicker.value = null
    }

    fun loadMoreSlots() {
        val cursor = _slotPicker.value?.cursor ?: return
        loadSlotPage(cursor)
    }

    private fun loadSlotPage(cursor: String?) {
        val target = _slotPicker.value ?: return
        viewModelScope.launch {
            _slotPicker.value = target.copy(loading = cursor == null, loadingMore = cursor != null)
            try {
                val page = syncApi.getPlatformRecent(target.platform.apiValue, limit = 10, cursor = cursor)
                val existing = _slotPicker.value ?: return@launch
                _slotPicker.value = existing.copy(
                    items = if (cursor == null) page.items else existing.items + page.items,
                    cursor = page.nextCursor,
                    loading = false,
                    loadingMore = false,
                )
            } catch (e: Exception) {
                _slotPicker.value = _slotPicker.value?.copy(loading = false, loadingMore = false)
            }
        }
    }

    fun resolveSlot(item: PlatformRecentItemDto) {
        val slot = _slotPicker.value ?: return
        viewModelScope.launch {
            _slotPicker.value = slot.copy(resolvingId = item.platformId)
            try {
                syncApi.resolveCrossMatchSlot(
                    ResolveCrossMatchSlotRequest(
                        fileId = slot.fileId,
                        platform = slot.platform.apiValue,
                        platformId = item.platformId,
                        title = item.title,
                        thumbnail = item.thumbnail,
                        publishedAt = item.publishedAt,
                        platformUrl = item.platformUrl,
                    ),
                )
                applyResolvedSlot(slot.fileId, slot.platform, item)
                _slotPicker.value = null
            } catch (e: Exception) {
                _slotPicker.value = _slotPicker.value?.copy(resolvingId = null)
            }
        }
    }

    private fun applyResolvedSlot(fileId: String, platform: CrossPlatform, item: PlatformRecentItemDto) {
        val current = _crossMatchState.value
        if (current !is CrossMatchUiState.Success) return
        val resolvedSlot = CrossMatchResolvedSlotDto(
            platformId = item.platformId,
            platformUrl = item.platformUrl ?: "",
            title = item.title,
            thumbnail = item.thumbnail,
        )
        _crossMatchState.value = current.copy(
            candidates = current.candidates.map { candidate ->
                if (candidate.fileId != fileId) {
                    candidate
                } else {
                    candidate.copy(
                        resolved = when (platform) {
                            CrossPlatform.YOUTUBE -> candidate.resolved.copy(youtube = resolvedSlot)
                            CrossPlatform.INSTAGRAM -> candidate.resolved.copy(instagram = resolvedSlot)
                            CrossPlatform.TIKTOK -> candidate.resolved.copy(tiktok = resolvedSlot)
                        },
                    )
                }
            },
        )
    }

    // ---- Vincular con archivo local ----

    private fun loadStats() {
        viewModelScope.launch {
            runCatching { syncApi.getSyncStats() }.onSuccess { _stats.value = it }
        }
    }

    fun loadReview(page: Int) {
        viewModelScope.launch {
            _reviewState.value = ReviewUiState.Loading
            _reviewState.value = try {
                val res = syncApi.getReview(page)
                ReviewUiState.Success(res.items, res.page, res.totalPages)
            } catch (e: Exception) {
                ReviewUiState.Error(e.message ?: "No se pudo cargar la revisión.")
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _syncing.value = true
            runCatching { syncApi.triggerYoutubeSync() }
            loadStats()
            _syncing.value = false
        }
    }

    fun confirmLink(reviewItemId: String, fileId: String) {
        viewModelScope.launch {
            _busyReviewId.value = reviewItemId
            runCatching { syncApi.confirmLink(reviewItemId, ConfirmLinkRequest(fileId)) }
                .onSuccess {
                    removeReviewItem(reviewItemId)
                    _stats.value = _stats.value?.let { it.copy(linked = it.linked + 1, revisar = it.revisar - 1) }
                }
            _busyReviewId.value = null
        }
    }

    fun markOrphan(reviewItemId: String) {
        viewModelScope.launch {
            _busyReviewId.value = reviewItemId
            runCatching { syncApi.markOrphan(reviewItemId) }
                .onSuccess {
                    removeReviewItem(reviewItemId)
                    _stats.value = _stats.value?.let { it.copy(sinMatch = it.sinMatch + 1, revisar = it.revisar - 1) }
                }
            _busyReviewId.value = null
        }
    }

    private fun removeReviewItem(id: String) {
        val current = _reviewState.value
        if (current is ReviewUiState.Success) {
            _reviewState.value = current.copy(items = current.items.filter { it._id != id })
        }
    }
}
