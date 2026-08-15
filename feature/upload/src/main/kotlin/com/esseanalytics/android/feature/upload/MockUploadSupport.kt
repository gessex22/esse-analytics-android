package com.esseanalytics.android.feature.upload

import kotlinx.coroutines.delay
import java.util.UUID

// Lógica compartida por los 3 uploaders mock (Mock{Youtube,Instagram,Tiktok}
// Uploader.kt) -- mirror de MockUploadSupport.swift (iOS) y
// lab-backend/src/controllers/publish-jobs.service.ts. Nunca llama a
// googleapis.com/graph.facebook.com/open.tiktokapis.com.
enum class MockUploadMode { SUCCESS, FAIL, TOKEN_EXPIRED, INTERRUPTED }

enum class MockPlatform(val prefix: String, val urlSegment: String) {
    YOUTUBE("lab_yt", "youtube"),
    INSTAGRAM("lab_ig", "instagram"),
    TIKTOK("lab_tt", "tiktok"),
}

object MockUploadSupport {
    fun platformId(platform: MockPlatform): String =
        "${platform.prefix}_${UUID.randomUUID().toString().take(12).lowercase()}"

    fun platformUrl(platform: MockPlatform, platformId: String): String =
        "https://laboratorio.esse-analytics.local/mock/${platform.urlSegment}/$platformId"

    // Lanza [MockInterrupted]/[MockTokenExpired] en los modos que no terminan
    // en éxito -- el caller decide cómo mapear eso a UploadResult.Failure
    // (retryable true/false según corresponda, ver Mock*Uploader.kt).
    suspend fun simulateProgress(mode: MockUploadMode, onProgress: (Float) -> Unit) {
        onProgress(0.02f)
        var percent = 10
        while (percent <= 90) {
            delay(250)
            if (mode == MockUploadMode.INTERRUPTED && percent >= 30) throw MockInterrupted()
            if (mode == MockUploadMode.TOKEN_EXPIRED && percent >= 20) throw MockTokenExpired()
            if (mode == MockUploadMode.FAIL && percent >= 40) throw MockRecoverableFailure()
            onProgress(percent / 100f)
            percent += 10
        }
        delay(250)
    }
}

class MockInterrupted : Exception("La subida se interrumpió (simulado: conexión perdida). Reintentá.")
class MockTokenExpired : Exception("El token de la plataforma venció durante la subida (simulado). Reconectá la cuenta.")
class MockRecoverableFailure : Exception("Error recuperable simulado: la plataforma devolvió un 500. Reintentá.")
