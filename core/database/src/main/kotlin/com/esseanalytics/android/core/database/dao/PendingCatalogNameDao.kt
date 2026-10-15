package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.esseanalytics.android.core.database.entity.PendingCatalogNameEntity

sealed interface CatalogNameAckResult {
    data class Complete(val revision: Int) : CatalogNameAckResult
    data class SendDesired(val revision: Int) : CatalogNameAckResult
    data class Conflict(val currentName: String, val revision: Int) : CatalogNameAckResult
    data object Missing : CatalogNameAckResult
}

fun decideCatalogNameAck(row: PendingCatalogNameEntity, serverName: String, revision: Int): CatalogNameAckResult =
    if (revision != row.baseRevision + 1) CatalogNameAckResult.Conflict(serverName, revision)
    else if (row.desiredName != row.displayName) CatalogNameAckResult.SendDesired(revision)
    else CatalogNameAckResult.Complete(revision)

@Dao
interface PendingCatalogNameDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: PendingCatalogNameEntity)

    @Query("SELECT * FROM pending_catalog_names WHERE userKey = :userKey AND serverUrl = :serverUrl AND clientFileId = :clientFileId LIMIT 1")
    suspend fun current(userKey: String, serverUrl: String, clientFileId: String): PendingCatalogNameEntity?

    @Query("UPDATE pending_catalog_names SET desiredName = :name WHERE operationId = :operationId")
    suspend fun updateDesiredName(operationId: String, name: String)

    @Query("DELETE FROM pending_catalog_names WHERE operationId = :operationId")
    suspend fun delete(operationId: String)

    @Transaction
    suspend fun enqueue(row: PendingCatalogNameEntity) {
        val old = current(row.userKey, row.serverUrl, row.clientFileId)
        if (old != null && (old.state == "sending" || old.state == "retry" || old.state == "conflict" || old.attempts > 0)) {
            updateDesiredName(old.operationId, row.desiredName)
        } else {
            old?.let { delete(it.operationId) }
            insert(row)
        }
    }

    @Transaction
    suspend fun replaceConflict(row: PendingCatalogNameEntity) {
        current(row.userKey, row.serverUrl, row.clientFileId)?.let { delete(it.operationId) }
        insert(row)
    }

    @Query("SELECT * FROM pending_catalog_names WHERE userKey = :userKey AND serverUrl = :serverUrl AND state IN ('pending','retry','sending') AND nextAttemptAtEpochMs <= :now ORDER BY operationId")
    suspend fun pending(userKey: String, serverUrl: String, now: Long): List<PendingCatalogNameEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_catalog_names WHERE operationId = :operationId)")
    suspend fun isCurrent(operationId: String): Boolean

    @Query("SELECT * FROM pending_catalog_names WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): PendingCatalogNameEntity?

    @Query("UPDATE pending_catalog_names SET state = 'sending' WHERE operationId = :operationId AND state IN ('pending','retry','sending')")
    suspend fun markSending(operationId: String): Int

    @Query("SELECT * FROM pending_catalog_names WHERE userKey = :userKey AND serverUrl = :serverUrl AND clientFileId = :clientFileId AND state = 'conflict' LIMIT 1")
    suspend fun conflictFor(userKey: String, serverUrl: String, clientFileId: String): PendingCatalogNameEntity?

    @Query("DELETE FROM pending_catalog_names WHERE userKey = :userKey AND serverUrl = :serverUrl AND clientFileId = :clientFileId")
    suspend fun clear(userKey: String, serverUrl: String, clientFileId: String)

    @Query("UPDATE pending_catalog_names SET contentId = :contentId WHERE operationId = :operationId")
    suspend fun attachContentId(operationId: String, contentId: String)

    @Query("DELETE FROM pending_catalog_names WHERE operationId = :operationId")
    suspend fun acknowledge(operationId: String)

    @Query("UPDATE pending_catalog_names SET state = 'conflict', currentName = :name, currentRevision = :revision, lastError = 'revision_conflict' WHERE operationId = :operationId")
    suspend fun conflict(operationId: String, name: String, revision: Int)

    @Query("UPDATE pending_catalog_names SET state = 'retry', attempts = attempts + 1, nextAttemptAtEpochMs = :nextAt, lastError = :error WHERE operationId = :operationId")
    suspend fun retry(operationId: String, nextAt: Long, error: String)

    @Transaction
    suspend fun applyAcknowledgement(operationId: String, serverName: String, revision: Int, clientFileId: String, nextOperationId: String): CatalogNameAckResult {
        val row = findByOperationId(operationId) ?: return CatalogNameAckResult.Missing
        val decision = decideCatalogNameAck(row, serverName, revision)
        when (decision) {
            is CatalogNameAckResult.Conflict -> {
                conflict(operationId, decision.currentName, decision.revision)
                updateFileNameStatus(clientFileId, decision.revision, "conflict", row.userKey.substringAfterLast('|'), row.serverUrl)
            }
            is CatalogNameAckResult.SendDesired -> {
                queueDesired(operationId, nextOperationId, decision.revision)
                updateFileNameStatus(clientFileId, decision.revision, "pending", row.userKey.substringAfterLast('|'), row.serverUrl)
            }
            is CatalogNameAckResult.Complete -> {
                acknowledge(operationId)
                updateFileNameStatus(clientFileId, decision.revision, "synced", row.userKey.substringAfterLast('|'), row.serverUrl)
            }
            CatalogNameAckResult.Missing -> Unit
        }
        return decision
    }

    @Transaction
    suspend fun applyConflict(operationId: String, serverName: String, revision: Int, clientFileId: String) {
        val row = findByOperationId(operationId) ?: return
        conflict(operationId, serverName, revision)
        updateFileNameStatus(clientFileId, revision, "conflict", row.userKey.substringAfterLast('|'), row.serverUrl)
    }

    @Query("UPDATE pending_catalog_names SET operationId = :nextOperationId, displayName = desiredName, baseRevision = :revision, attempts = 0, nextAttemptAtEpochMs = 0, state = 'pending', currentName = NULL, currentRevision = NULL, lastError = NULL WHERE operationId = :operationId")
    suspend fun queueDesired(operationId: String, nextOperationId: String, revision: Int)

    @Query("UPDATE files SET displayNameRevision = :revision, displayNameState = :state WHERE clientFileId = :clientFileId AND displayNameOwnerId = :ownerId AND displayNameServerUrl = :serverUrl")
    suspend fun updateFileNameStatus(clientFileId: String, revision: Int, state: String, ownerId: String, serverUrl: String)

    @Transaction
    suspend fun hydrateIfNoPending(userKey: String, serverUrl: String, clientFileId: String, name: String, revision: Int, ownerId: String): Boolean {
        if (current(userKey, serverUrl, clientFileId) != null) return false
        updateHydratedFileName(clientFileId, name, revision, ownerId, serverUrl)
        return true
    }

    @Query("UPDATE files SET displayName = :name, displayNameRevision = :revision, displayNameState = 'synced', displayNameOwnerId = :ownerId, displayNameServerUrl = :serverUrl WHERE clientFileId = :clientFileId AND (displayNameOwnerId IS NULL OR displayNameOwnerId != :ownerId OR displayNameServerUrl IS NULL OR displayNameServerUrl != :serverUrl OR displayNameRevision <= :revision)")
    suspend fun updateHydratedFileName(clientFileId: String, name: String, revision: Int, ownerId: String, serverUrl: String)
}
