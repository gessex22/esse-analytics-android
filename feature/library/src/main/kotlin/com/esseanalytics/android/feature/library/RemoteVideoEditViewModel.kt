package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.PlatformTransitionRepository
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.ACTION_DISCARD
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.ACTION_MARK_PUBLISHED
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.ACTION_UNLINK
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.CausalPlatformOutbox
import com.esseanalytics.android.core.network.di.PlatformOkHttp
import com.esseanalytics.android.core.network.dto.RemoteLibraryPlatformLinkDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// Editor de un video de la COLA REMOTA (RemoteLibraryVideoDto). Ya no PATCHea
// Biblioteca remota directo: los badges/clear viajan como transición causal y
// el link como manual-platform-link, contra la identidad que trae el propio DTO
// (contentId + platformRev, hidratados al listar Nube). Un DTO legado sin esos
// campos NO se puede editar por el camino causal -- falla explícito y revierte
// la UI (no manda una transición sin identidad/revisión, nunca cae al fileName).
@HiltViewModel
class RemoteVideoEditViewModel @Inject constructor(
    private val platformTransitionRepository: PlatformTransitionRepository,
    private val causalPlatformOutbox: CausalPlatformOutbox,
    private val tokenStore: TokenStore,
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
        val session = requireCausalIdentity(current) ?: return
        viewModelScope.launch {
            val action = when {
                platform.apiValue in current.platforms -> ACTION_DISCARD
                platform.apiValue in current.platformsDiscarded -> ACTION_UNLINK
                else -> ACTION_MARK_PUBLISHED
            }
            val (platforms, discarded) = PlatformLinkResolver.nextCycle(
                platform, current.platforms, current.platformsDiscarded,
            )
            _video.value = current.copy(platforms = platforms, platformsDiscarded = discarded)
            val enqueued = runCatching {
                platformTransitionRepository.enqueueKnownTransition(
                    userKey = session.userKey,
                    contentId = session.contentId,
                    remoteLibraryVideoId = current._id,
                    fileName = current.fileName,
                    platform = platform,
                    action = action,
                    baseVersion = current.platformRev?.get(platform.apiValue),
                )
            }
            enqueued.onFailure {
                _video.value = current
                _errorMessage.value = it.message ?: "No se pudo actualizar."
            }.onSuccess { triggerFlush() }
        }
    }

    override fun saveLink(platform: Platform, rawUrl: String) {
        val current = _video.value ?: return
        val session = requireCausalIdentity(current) ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            val trimmed = rawUrl.trim()

            val enqueued = if (trimmed.isEmpty()) {
                // Clear -> transición unlink; el badge vuelve a pendiente.
                _video.value = current.copy(
                    platforms = current.platforms.filter { it != platform.apiValue },
                    platformLinks = current.platformLinks.filter { it.platform != platform.apiValue },
                )
                runCatching {
                    platformTransitionRepository.enqueueKnownTransition(
                        userKey = session.userKey,
                        contentId = session.contentId,
                        remoteLibraryVideoId = current._id,
                        fileName = current.fileName,
                        platform = platform,
                        action = ACTION_UNLINK,
                        baseVersion = current.platformRev?.get(platform.apiValue),
                    )
                }
            } else {
                val platformId = PlatformLinkResolver.resolvedPlatformId(platform, trimmed, platformOkHttpClient)
                val existing = current.platformLinks.firstOrNull { it.platform == platform.apiValue }
                val publishedAt = existing?.publishedAt ?: Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
                val link = RemoteLibraryPlatformLinkDto(
                    platform = platform.apiValue,
                    platformId = platformId,
                    platformUrl = trimmed,
                    publishedAt = publishedAt,
                )
                _video.value = current.copy(
                    platforms = (current.platforms + platform.apiValue).distinct(),
                    platformLinks = current.platformLinks.filter { it.platform != platform.apiValue } + link,
                )
                runCatching {
                    platformTransitionRepository.enqueueManualLink(
                        userKey = session.userKey,
                        fileName = current.fileName,
                        remoteLibraryVideoId = current._id,
                        platform = platform,
                        platformId = platformId,
                        platformUrl = trimmed,
                        title = null,
                        publishedAt = publishedAt,
                    )
                }
            }

            enqueued.onFailure {
                _video.value = current
                _errorMessage.value = it.message ?: "No se pudo guardar el link."
            }.onSuccess { triggerFlush() }
            _isSaving.value = false
        }
    }

    // Guard de identidad causal: exige sesión + contentId + platformRev en el
    // DTO. Si falta algo (DTO legado, sin login), pone el error y NO aplica
    // ningún cambio optimista -- la UI queda como estaba (revert). Devuelve la
    // sesión lista para usar, o null si no se puede editar por el camino causal.
    private fun requireCausalIdentity(video: RemoteLibraryVideoDto): CausalSession? {
        val userKey = tokenStore.currentUser?.id
        val contentId = video.contentId
        if (userKey == null || contentId == null || video.platformRev == null) {
            _errorMessage.value =
                "Este video no tiene identidad causal para editar. Volvé a sincronizar la Nube."
            return null
        }
        return CausalSession(userKey, contentId)
    }

    private fun triggerFlush() {
        viewModelScope.launch { runCatching { causalPlatformOutbox.flush() } }
    }

    private data class CausalSession(val userKey: String, val contentId: String)
}
