package com.esseanalytics.android.feature.library

import com.esseanalytics.android.feature.ingest.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class VideoSaveTarget { APP, GALLERY }

internal sealed interface VideoSaveStatus {
    data object Idle : VideoSaveStatus
    data object Running : VideoSaveStatus
    data object Saved : VideoSaveStatus
    data class Failed(val message: String) : VideoSaveStatus
}

internal sealed interface GallerySaveResult {
    data object Saved : GallerySaveResult
    data class Failed(val message: String) : GallerySaveResult
}

internal fun ImportResult.toVideoSaveStatus(): VideoSaveStatus = when (this) {
    is ImportResult.Success, is ImportResult.Duplicate -> VideoSaveStatus.Saved
    is ImportResult.Error -> VideoSaveStatus.Failed(message)
}

internal fun GallerySaveResult.toVideoSaveStatus(): VideoSaveStatus = when (this) {
    GallerySaveResult.Saved -> VideoSaveStatus.Saved
    is GallerySaveResult.Failed -> VideoSaveStatus.Failed(message)
}

/**
 * Estado puro y testeable de los dos destinos de guardado. Solo una operación
 * puede estar activa; un fallo se puede reintentar y un destino ya guardado no
 * se ejecuta otra vez para evitar copias duplicadas.
 */
internal class VideoSaveCoordinator {
    private val _appStatus = MutableStateFlow<VideoSaveStatus>(VideoSaveStatus.Idle)
    val appStatus: StateFlow<VideoSaveStatus> = _appStatus.asStateFlow()

    private val _galleryStatus = MutableStateFlow<VideoSaveStatus>(VideoSaveStatus.Idle)
    val galleryStatus: StateFlow<VideoSaveStatus> = _galleryStatus.asStateFlow()

    @Synchronized
    fun begin(target: VideoSaveTarget): Boolean {
        if (isRunning()) return false
        val current = status(target)
        if (current is VideoSaveStatus.Saved) return false
        set(target, VideoSaveStatus.Running)
        return true
    }

    @Synchronized
    fun complete(target: VideoSaveTarget, result: VideoSaveStatus) {
        if (status(target) is VideoSaveStatus.Running) set(target, result)
    }

    @Synchronized
    fun markSaved(target: VideoSaveTarget) {
        if (status(target) !is VideoSaveStatus.Running) set(target, VideoSaveStatus.Saved)
    }

    @Synchronized
    fun reset() {
        _appStatus.value = VideoSaveStatus.Idle
        _galleryStatus.value = VideoSaveStatus.Idle
    }

    @Synchronized
    fun isRunning(): Boolean =
        _appStatus.value is VideoSaveStatus.Running ||
            _galleryStatus.value is VideoSaveStatus.Running

    private fun status(target: VideoSaveTarget): VideoSaveStatus = when (target) {
        VideoSaveTarget.APP -> _appStatus.value
        VideoSaveTarget.GALLERY -> _galleryStatus.value
    }

    private fun set(target: VideoSaveTarget, status: VideoSaveStatus) {
        when (target) {
            VideoSaveTarget.APP -> _appStatus.value = status
            VideoSaveTarget.GALLERY -> _galleryStatus.value = status
        }
    }
}
