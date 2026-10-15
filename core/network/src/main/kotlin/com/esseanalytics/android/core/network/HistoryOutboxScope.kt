package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity

/** Pure scope guard shared by delivery and ACK decisions. */
internal object HistoryOutboxScope {
    fun canSend(event: PendingHistoryEventEntity, userId: String, server: String): Boolean =
        event.state == "pending" && event.userKey == userId && event.serverUrl == server

    fun canAcknowledge(
        event: PendingHistoryEventEntity,
        capturedToken: String,
        capturedUserId: String,
        capturedServer: String,
        currentToken: String?,
        currentUserId: String?,
        currentServer: String,
    ): Boolean = canSend(event, capturedUserId, capturedServer) &&
        currentToken == capturedToken && currentUserId == capturedUserId && currentServer == capturedServer
}
