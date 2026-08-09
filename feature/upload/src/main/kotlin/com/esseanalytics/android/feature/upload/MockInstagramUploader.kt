package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.network.api.PlatformAuthApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Ver MockYoutubeUploader.kt -- mismo criterio, con el crosspost a Facebook
// también simulado (nunca llama a graph.facebook.com).
@Singleton
class MockInstagramUploader @Inject constructor(
    private val platformAuthApi: PlatformAuthApi,
) : PlatformUploader {

    var mode: MockUploadMode = MockUploadMode.SUCCESS

    override suspend fun upload(file: File, metadata: UploadMetadata, onProgress: (Float) -> Unit): UploadResult {
        return try {
            platformAuthApi.instagramToken()
            MockUploadSupport.simulateProgress(mode, onProgress)
            val platformId = MockUploadSupport.platformId(MockPlatform.INSTAGRAM)
            val crossPost = if (metadata.crossPostFacebook) {
                val fbId = "lab_fb_${java.util.UUID.randomUUID().toString().take(12).lowercase()}"
                FacebookCrossPostResult.Published(
                    videoId = fbId,
                    url = "https://laboratorio.esse-analytics.local/mock/facebook/$fbId",
                )
            } else null
            UploadResult.Success(
                platformId = platformId,
                platformUrl = MockUploadSupport.platformUrl(MockPlatform.INSTAGRAM, platformId),
                facebookCrossPost = crossPost,
            )
        } catch (e: MockInterrupted) {
            UploadResult.Failure(e.message ?: "Interrumpido", retryable = true)
        } catch (e: MockTokenExpired) {
            UploadResult.Failure(e.message ?: "Token vencido", retryable = false)
        } catch (e: MockRecoverableFailure) {
            UploadResult.Failure(e.message ?: "Error recuperable", retryable = true)
        } catch (e: Exception) {
            UploadResult.Failure(e.message ?: "Conectá tu cuenta de Instagram primero.", retryable = false)
        }
    }
}
