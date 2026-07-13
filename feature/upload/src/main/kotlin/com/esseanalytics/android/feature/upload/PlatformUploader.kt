package com.esseanalytics.android.feature.upload

import java.io.File

data class UploadMetadata(
    val title: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val privacyStatus: String = "public",
    // Portada elegida a mano por el usuario (offset dentro del video, en ms) --
    // mismo criterio que desktop (YoutubeUploadView.tsx): YouTube recibe un
    // frame capturado como imagen aparte (thumbnails.set), Instagram/TikTok
    // solo el timestamp -- cada uploader lo usa distinto, ver el suyo.
    val thumbnailOffsetMs: Long? = null,
)

sealed interface UploadResult {
    data class Success(val platformId: String, val platformUrl: String) : UploadResult
    data class Failure(val message: String, val retryable: Boolean) : UploadResult
}

// Implementado por YoutubeUploader / InstagramUploader / TiktokUploader en
// Fase 1 — ver el plan para el detalle exacto de cada flujo (chunked real
// para YouTube, un solo POST para Instagram, chunked FILE_UPLOAD para TikTok).
// UploadWorker (WorkManager) llama a esto sin necesitar saber la plataforma.
interface PlatformUploader {
    suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult
}
