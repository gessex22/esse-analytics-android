package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.RecordPublishRequest

/** Shared caller contract for local and remote-library publication workers. */
object PublicationWorkContracts {
    fun historyRequest(
        platform: Platform,
        platformId: String,
        platformUrl: String,
        fileName: String,
        title: String,
        remoteLibraryVideoId: String?,
        clientFileId: String?,
        operationId: String?,
        publishedAt: String,
        deviceId: String?,
        deviceName: String?,
    ) = RecordPublishRequest(
        clientFileId = clientFileId,
        platform = platform.apiValue,
        platformId = platformId,
        platformUrl = platformUrl.ifBlank { null },
        fileName = fileName,
        remoteLibraryVideoId = remoteLibraryVideoId,
        title = title,
        publishedAt = publishedAt,
        operationId = operationId,
        deviceId = deviceId,
        deviceName = deviceName,
    )

}
