package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Retrofit
import javax.inject.Inject

data class VideoStatsDetailState(val item: GroupStatsItemDto? = null, val loading: Boolean = true, val error: String? = null)
@HiltViewModel
class VideoStatsDetailViewModel @Inject constructor(private val api: SyncApi, private val tokens: TokenStore, @CentralRetrofit private val retrofit: Retrofit) : ViewModel() {
    private val _state = MutableStateFlow(VideoStatsDetailState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var generation = 0L
    fun thumbnail(item: GroupStatsItemDto): String? = item.remoteLibraryVideoId?.let { remoteLibraryThumbnailUrl(retrofit.baseUrl(), it, item.thumbnailStoredFileName, tokens.token) } ?: item.thumbnail
    private var lastSelection: VideoStatsSelection? = null
    private var lastSession: String? = null
    fun load(selection: VideoStatsSelection) {
        val id = ++generation; request?.cancel()
        val token = tokens.token
        val previous = if (lastSelection == selection && lastSession == token) _state.value.item else selection.seed
        lastSelection = selection; lastSession = token
        _state.value = VideoStatsDetailState(previous)
        request = viewModelScope.launch {
            try {
                val exact = !selection.platform.isNullOrBlank() && !selection.platformId.isNullOrBlank()
                val item = api.getVideoDetail(
                    platform = selection.platform.takeIf { exact }, platformId = selection.platformId.takeIf { exact },
                    fileId = selection.fileId.takeUnless { exact }, fileName = selection.fileName.takeUnless { exact || !selection.fileId.isNullOrBlank() },
                )
                ensureActive()
                if (id == generation) _state.value = if (tokens.token == token) VideoStatsDetailState(item, false) else VideoStatsDetailState(loading = false, error = "La sesión cambió. Actualiza los datos.")
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if (id == generation) _state.value = VideoStatsDetailState(previous.takeIf { tokens.token == token }, false, statsError(e, "el detalle de este video")) }
        }
    }
}
