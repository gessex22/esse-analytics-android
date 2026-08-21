package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.LocalBackendApiFactory
import com.esseanalytics.android.core.network.dto.LocalPcPlatformLinksDto
import com.esseanalytics.android.core.network.dto.LocalPcSetPlatformLinkRequest
import com.esseanalytics.android.core.network.dto.LocalPcUpdatePlatformsRequest
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanVideoDetailViewModel @Inject constructor(
    private val localBackendApiFactory: LocalBackendApiFactory,
) : ViewModel(), VideoDetailEditor {
    private var currentItem: LibraryListItem.LanVideo? = null
    private val _video = MutableStateFlow<LocalPcVideoDto?>(null)
    val video: StateFlow<LocalPcVideoDto?> = _video.asStateFlow()
    private val _links = MutableStateFlow(LocalPcPlatformLinksDto())
    val links: StateFlow<LocalPcPlatformLinksDto> = _links.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setInitial(item: LibraryListItem.LanVideo) {
        if (currentItem?.video?._id == item.video._id && currentItem?.baseUrl == item.baseUrl) return
        currentItem = item
        _video.value = item.video
        _links.value = LocalPcPlatformLinksDto()
        viewModelScope.launch { reloadLinks() }
    }

    fun existingLink(platform: Platform): String? = _links.value.urlFor(platform.apiValue)

    override fun togglePlatform(platform: Platform) {
        val item = currentItem ?: return
        val current = _video.value ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            val (platforms, discarded) = PlatformLinkResolver.nextCycle(
                platform,
                current.platforms,
                current.platforms_discarded,
            )
            _video.value = current.copy(platforms = platforms, platforms_discarded = discarded)
            val result = runCatching {
                localBackendApiFactory.create(item.baseUrl).updatePlatforms(
                    current._id,
                    LocalPcUpdatePlatformsRequest(platforms, discarded),
                )
            }
            result.onSuccess { response ->
                if (!response.isSuccessful) {
                    _video.value = current
                    _errorMessage.value = "No se pudo actualizar (${response.code()})."
                }
            }.onFailure {
                _video.value = current
                _errorMessage.value = it.message ?: "No se pudo actualizar."
            }
        }
    }

    override fun saveLink(platform: Platform, rawUrl: String) {
        val item = currentItem ?: return
        val current = _video.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()
            runCatching {
                val api = localBackendApiFactory.create(item.baseUrl)
                val response = api.setPlatformLink(
                    current._id,
                    platform.apiValue,
                    LocalPcSetPlatformLinkRequest(trimmed.ifEmpty { null }),
                )
                if (!response.isSuccessful) error("Error ${response.code()}")
                _links.value = api.getPlatformLinks(current._id)
            }.onFailure {
                _errorMessage.value = it.message ?: "No se pudo guardar el link."
            }
            _isSaving.value = false
        }
    }

    private suspend fun reloadLinks() {
        val item = currentItem ?: return
        runCatching {
            localBackendApiFactory.create(item.baseUrl).getPlatformLinks(item.video._id)
        }.onSuccess { _links.value = it }
            .onFailure { _errorMessage.value = it.message ?: "No se pudieron cargar los links." }
    }
}
