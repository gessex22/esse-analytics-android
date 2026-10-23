package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PlatformVideoRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.HistoryOutbox
import com.esseanalytics.android.core.network.dto.RecordPublishRequest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Production side effects shared by the local and remote publication callers. */
@Singleton
class PublicationSuccessEffects @Inject constructor(
    private val fileRepository: FileRepository,
    private val platformVideoRepository: PlatformVideoRepository,
    private val settingsStore: SettingsStore,
    private val historyOutbox: HistoryOutbox,
) {
    suspend fun recordLocalSuccess(
        file: VideoFile,
        platform: Platform,
        result: UploadResult.Success,
        title: String,
        operationId: String?,
    ) {
        platformVideoRepository.upsertPublished(
            platform = platform,
            platformId = result.platformId,
            platformUrl = result.platformUrl.ifBlank { null },
            linkedFileId = file.id,
            title = title,
        )
        fileRepository.onPlatformPublished(file.id, platform, settingsStore.workflowMode.first())
        recordHistory(
            platform = platform,
            platformId = result.platformId,
            platformUrl = result.platformUrl,
            fileName = file.fileName,
            title = title,
            remoteLibraryVideoId = file.remoteLibraryVideoId,
            clientFileId = file.clientFileId,
            operationId = result.durableOperationId ?: operationId,
        )
    }

    suspend fun recordFacebookCrossPost(
        file: VideoFile,
        videoId: String,
        url: String,
        title: String,
        operationId: String?,
    ) {
        platformVideoRepository.upsertPublished(
            platform = Platform.FACEBOOK,
            platformId = videoId,
            platformUrl = url,
            linkedFileId = file.id,
            title = title,
        )
        // Facebook is a crosspost, not one of the independently selected
        // platforms, so it must not resolve pending YouTube/TikTok choices.
        fileRepository.addPlatform(file.id, Platform.FACEBOOK)
        recordHistory(
            platform = Platform.FACEBOOK,
            platformId = videoId,
            platformUrl = url,
            fileName = file.fileName,
            title = title,
            remoteLibraryVideoId = file.remoteLibraryVideoId,
            clientFileId = file.clientFileId,
            operationId = operationId,
        )
    }

    suspend fun recordRemoteSuccess(
        platform: Platform,
        platformId: String,
        platformUrl: String,
        fileName: String,
        title: String,
        remoteLibraryVideoId: String,
    ) {
        recordHistory(
            platform = platform,
            platformId = platformId,
            platformUrl = platformUrl,
            fileName = fileName,
            title = title,
            remoteLibraryVideoId = remoteLibraryVideoId,
            clientFileId = null,
            operationId = null,
        )
    }

    private suspend fun recordHistory(
        platform: Platform,
        platformId: String,
        platformUrl: String,
        fileName: String,
        title: String,
        remoteLibraryVideoId: String?,
        clientFileId: String?,
        operationId: String?,
    ) {
        val request: RecordPublishRequest = PublicationWorkContracts.historyRequest(
            platform = platform,
            platformId = platformId,
            platformUrl = platformUrl,
            fileName = fileName,
            title = title,
            remoteLibraryVideoId = remoteLibraryVideoId,
            clientFileId = clientFileId,
            operationId = operationId,
            publishedAt = Instant.now().toString(),
            deviceId = settingsStore.getOrCreateInstallId(),
            deviceName = settingsStore.getOrCreateDeviceName(),
        )
        historyOutbox.sendOrEnqueue(request)
    }
}
