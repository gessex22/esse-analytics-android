package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity

@Dao
interface PendingHistoryEventDao {
    @Query("SELECT * FROM pending_history_events ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<PendingHistoryEventEntity>

    @Query("SELECT * FROM pending_history_events WHERE userKey = :userKey AND serverUrl = :serverUrl AND state = 'pending' AND nextAttemptAtEpochMs <= :now ORDER BY createdAtEpochMs ASC")
    suspend fun pending(userKey: String, serverUrl: String, now: Long): List<PendingHistoryEventEntity>

    @Insert
    suspend fun insert(entity: PendingHistoryEventEntity): Long

    @Update
    suspend fun update(entity: PendingHistoryEventEntity)

    @Query("UPDATE pending_history_events SET attempts = :attempts, nextAttemptAtEpochMs = :nextAttemptAtEpochMs, lastError = :lastError WHERE id = :id AND userKey = :userKey AND serverUrl = :serverUrl AND state = 'pending'")
    suspend fun recordAttempt(id: Long, userKey: String, serverUrl: String, attempts: Int, nextAttemptAtEpochMs: Long, lastError: String)

    @Query("UPDATE pending_history_events SET state = 'blocked', lastError = :reason WHERE id = :id AND userKey = :userKey AND serverUrl = :serverUrl AND state = 'pending'")
    suspend fun block(id: Long, userKey: String, serverUrl: String, reason: String)

    @Delete
    suspend fun delete(entity: PendingHistoryEventEntity)
}
