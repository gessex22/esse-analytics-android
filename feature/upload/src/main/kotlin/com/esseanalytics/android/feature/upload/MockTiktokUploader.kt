package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Ver MockYoutubeUploader.kt -- mismo criterio.
@Singleton
class MockTiktokUploader @Inject constructor(
    private val platformAuthApi: PlatformAuthApi,
) : PlatformUploader {

    var mode: MockUploadMode = MockUploadMode.SUCCESS

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        return try {
            platformAuthApi.tiktokToken()
            MockUploadSupport.simulateProgress(mode, onProgress)
            val platformId = MockUploadSupport.platformId(MockPlatform.TIKTOK)
            UploadResult.Success(
                platformId = platformId,
                platformUrl = MockUploadSupport.platformUrl(MockPlatform.TIKTOK, platformId),
            )
        } catch (e: MockInterrupted) {
            UploadResult.Failure(e.message ?: "Interrumpido", retryable = true)
        } catch (e: MockTokenExpired) {
            UploadResult.Failure(e.message ?: "Token vencido", retryable = false)
        } catch (e: MockRecoverableFailure) {
            UploadResult.Failure(e.message ?: "Error recuperable", retryable = true)
        } catch (e: Exception) {
            UploadResult.Failure(e.message ?: "Conectá tu cuenta de TikTok primero.", retryable = false)
        }
    }
}
