package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryPlatformLinkDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// Editor manual de "publicado + link real" para un video que todavía vive
// SOLO en Nube (Biblioteca remota) -- no hay Room de por medio, Android no
// tiene forma de "bajar" un video de la cola a un registro local (a
// diferencia de iOS, ver RemoteUploadWorker: descarga a un temporal solo
// para subirlo a la plataforma, nunca crea un VideoFile). Mirror de
// RemoteVideoDetailView.saveLink/togglePlatform en iOS -- ahí SÍ pega
// directo contra RemoteLibraryVideoModel, mismo enfoque acá.
@HiltViewModel
class RemoteVideoEditViewModel @Inject constructor(
    private val remoteLibraryApi: RemoteLibraryApi,
) : ViewModel() {

    private val _video = MutableStateFlow<RemoteLibraryVideoDto?>(null)
    val video: StateFlow<RemoteLibraryVideoDto?> = _video.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Se llama desde el sheet con el video que ya tiene la fila (evita un
    // GET extra) -- solo pisa el estado si es un video distinto, así no se
    // resetea la vista optimista si el composable recompone.
    fun setInitial(initial: RemoteLibraryVideoDto) {
        if (_video.value?._id != initial._id) _video.value = initial
    }

    fun existingLink(platform: Platform): String? =
        _video.value?.platformLinks?.firstOrNull { it.platform == platform.apiValue }?.platformUrl

    // Ciclo pendiente -> publicado -> descartado -> pendiente. Optimista --
    // si el PATCH falla, se revierte al estado anterior (mismo criterio que
    // RemoteVideoDetailView.togglePlatform en iOS).
    fun togglePlatform(platform: Platform) {
        val current = _video.value ?: return
        viewModelScope.launch {
            val apiValue = platform.apiValue
            val platforms = current.platforms.toMutableList()
            val discarded = current.platformsDiscarded.toMutableList()
            when {
                apiValue in platforms -> {
                    platforms.remove(apiValue)
                    discarded.add(apiValue)
                }
                apiValue in discarded -> discarded.remove(apiValue)
                else -> platforms.add(apiValue)
            }
            _video.value = current.copy(platforms = platforms, platformsDiscarded = discarded)

            runCatching {
                remoteLibraryApi.updatePlatforms(
                    id = current._id,
                    body = UpdateRemoteLibraryPlatformsRequest(platforms = platforms, platformsDiscarded = discarded),
                ).video
            }.onSuccess { _video.value = it }
                .onFailure {
                    _video.value = current
                    _errorMessage.value = it.message ?: "No se pudo actualizar."
                }
        }
    }

    fun saveLink(platform: Platform, rawUrl: String) {
        val current = _video.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()
            val existing = current.platformLinks.firstOrNull { it.platform == platform.apiValue }
            val link = RemoteLibraryPlatformLinkDto(
                platform = platform.apiValue,
                platformId = existing?.platformId ?: trimmed,
                platformUrl = trimmed.ifEmpty { null },
                // truncatedTo(MILLIS) -- ver el mismo comentario en
                // VideoDetailViewModel, el parseo de Date en Node no es
                // confiable con más de 3 dígitos decimales.
                publishedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
            )
            runCatching {
                remoteLibraryApi.updatePlatforms(
                    id = current._id,
                    body = UpdateRemoteLibraryPlatformsRequest(
                        platforms = current.platforms,
                        platformsDiscarded = current.platformsDiscarded,
                        platformLinks = listOf(link),
                    ),
                ).video
            }.onSuccess { _video.value = it }
                .onFailure { _errorMessage.value = it.message ?: "No se pudo guardar el link." }
            _isSaving.value = false
        }
    }
}
