package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import com.esseanalytics.android.core.network.dto.RemoteLibraryPlatformLinkDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class RemoteVideoEditViewModel @Inject constructor(
    private val remoteLibraryApi: RemoteLibraryApi,
    @PlatformOkHttp private val platformOkHttpClient: OkHttpClient,
) : ViewModel(), VideoDetailEditor {
    private val _video = MutableStateFlow<RemoteLibraryVideoDto?>(null)
    val video: StateFlow<RemoteLibraryVideoDto?> = _video.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setInitial(initial: RemoteLibraryVideoDto) {
        if (_video.value?._id != initial._id) _video.value = initial
    }

    fun existingLink(platform: Platform): String? =
        _video.value?.platformLinks?.firstOrNull { it.platform == platform.apiValue }?.platformUrl

    override fun togglePlatform(platform: Platform) {
        val current = _video.value ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            val (platforms, discarded) = PlatformLinkResolver.nextCycle(
                platform,
                current.platforms,
                current.platformsDiscarded,
            )
            _video.value = current.copy(platforms = platforms, platformsDiscarded = discarded)
            runCatching {
                remoteLibraryApi.updatePlatforms(
                    current._id,
                    UpdateRemoteLibraryPlatformsRequest(platforms, discarded),
                ).video
            }.onSuccess { _video.value = it }
                .onFailure {
                    _video.value = current
                    _errorMessage.value = it.message ?: "No se pudo actualizar."
                }
        }
    }

    override fun saveLink(platform: Platform, rawUrl: String) {
        val current = _video.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()
            val existing = current.platformLinks.firstOrNull { it.platform == platform.apiValue }
            val platformId = if (trimmed.isEmpty()) {
                existing?.platformId.orEmpty()
            } else {
                PlatformLinkResolver.resolvedPlatformId(platform, trimmed, platformOkHttpClient)
            }
            val link = RemoteLibraryPlatformLinkDto(
                platform = platform.apiValue,
                platformId = platformId,
                platformUrl = trimmed.ifEmpty { null },
                publishedAt = existing?.publishedAt
                    ?: Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
            )
            runCatching {
                remoteLibraryApi.updatePlatforms(
                    current._id,
                    UpdateRemoteLibraryPlatformsRequest(
                        current.platforms,
                        current.platformsDiscarded,
                        listOf(link),
                    ),
                ).video
            }.onSuccess { _video.value = it }
                .onFailure { _errorMessage.value = it.message ?: "No se pudo guardar el link." }
            _isSaving.value = false
        }
    }
}
