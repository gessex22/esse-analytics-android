package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Uploader mock -- conforma el mismo PlatformUploader que YoutubeUploader.kt,
// así UploadWorker elige entre uno u otro sin cambiar nada más (ver
// LabModeStatus). Nunca llama a googleapis.com. Pide el token real primero
// (mismo endpoint que el uploader real, GET /api/youtube/token) para heredar
// gratis el escenario "conexión vencida" -- si el lab-backend ya marcó esa
// conexión como expired/disconnected, esto falla ahí, antes de simular nada.
@Singleton
class MockYoutubeUploader @Inject constructor(
    private val platformAuthApi: PlatformAuthApi,
) : PlatformUploader {

    var mode: MockUploadMode = MockUploadMode.SUCCESS

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        return try {
            platformAuthApi.youtubeToken()
            MockUploadSupport.simulateProgress(mode, onProgress)
            val platformId = MockUploadSupport.platformId(MockPlatform.YOUTUBE)
            UploadResult.Success(
                platformId = platformId,
                platformUrl = MockUploadSupport.platformUrl(MockPlatform.YOUTUBE, platformId),
            )
        } catch (e: MockInterrupted) {
            UploadResult.Failure(e.message ?: "Interrumpido", retryable = true)
        } catch (e: MockTokenExpired) {
            UploadResult.Failure(e.message ?: "Token vencido", retryable = false)
        } catch (e: MockRecoverableFailure) {
            UploadResult.Failure(e.message ?: "Error recuperable", retryable = true)
        } catch (e: Exception) {
            // Token real vencido/desconectado (401 de PlatformAuthApi.youtubeToken) --
            // mismo criterio que el uploader real: no retryable sin reconectar.
            UploadResult.Failure(e.message ?: "Conectá tu cuenta de YouTube primero.", retryable = false)
        }
    }
}
