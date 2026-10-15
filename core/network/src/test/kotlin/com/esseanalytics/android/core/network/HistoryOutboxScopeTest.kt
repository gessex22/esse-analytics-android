package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryOutboxScopeTest {
    private val scoped = PendingHistoryEventEntity(
        platform = "youtube", contentId = null, clientFileId = null, platformId = "published-1",
        platformUrl = null, fileName = null, remoteLibraryVideoId = null, title = null,
        publishedAt = null, operationId = null, deviceId = null, deviceName = null, userKey = "user-a",
        serverUrl = "https://central/", state = "pending",
    )

    @Test fun doesNotSendAcrossAccountServerOrAmbiguousLegacyRows() {
        assertTrue(HistoryOutboxScope.canSend(scoped, "user-a", "https://central/"))
        assertFalse(HistoryOutboxScope.canSend(scoped, "user-b", "https://central/"))
        assertFalse(HistoryOutboxScope.canSend(scoped, "user-a", "https://lab/"))
        assertFalse(HistoryOutboxScope.canSend(scoped.copy(userKey = null, serverUrl = null), "user-a", "https://central/"))
        assertFalse(HistoryOutboxScope.canSend(scoped.copy(state = "blocked"), "user-a", "https://central/"))
    }

    @Test fun staleSessionCannotAcknowledgeAnInflightEvent() {
        assertTrue(HistoryOutboxScope.canAcknowledge(scoped, "token-a", "user-a", "https://central/", "token-a", "user-a", "https://central/"))
        assertFalse(HistoryOutboxScope.canAcknowledge(scoped, "token-a", "user-a", "https://central/", "token-b", "user-b", "https://central/"))
        assertFalse(HistoryOutboxScope.canAcknowledge(scoped, "token-a", "user-a", "https://central/", "token-a", "user-a", "https://lab/"))
    }
}
