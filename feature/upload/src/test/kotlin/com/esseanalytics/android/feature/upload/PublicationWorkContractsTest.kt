package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicationWorkContractsTest {
    @Test
    fun `history keeps file name separate from edited title and preserves both identities`() {
        val request = PublicationWorkContracts.historyRequest(
            platform = Platform.INSTAGRAM,
            platformId = "ig-post-7",
            platformUrl = "",
            fileName = "reel-original.mp4",
            title = "Caption edited before publish",
            remoteLibraryVideoId = "remote-video-12",
            clientFileId = "client-file-9",
            operationId = "batch-3",
            publishedAt = "2026-10-22T12:00:00Z",
            deviceId = "device-1",
            deviceName = "Phone",
        )

        assertEquals("instagram", request.platform)
        assertEquals("ig-post-7", request.platformId)
        assertEquals("reel-original.mp4", request.fileName)
        assertEquals("Caption edited before publish", request.title)
        assertEquals("remote-video-12", request.remoteLibraryVideoId)
        assertEquals("client-file-9", request.clientFileId)
        assertEquals("batch-3", request.operationId)
        assertNull(request.platformUrl)
    }

}
