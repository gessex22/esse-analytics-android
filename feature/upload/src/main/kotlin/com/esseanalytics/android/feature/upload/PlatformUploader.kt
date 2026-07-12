package com.esseanalytics.android.feature.upload

import java.io.File

data class UploadMetadata(
    val title: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val privacyStatus: String = "public",
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
