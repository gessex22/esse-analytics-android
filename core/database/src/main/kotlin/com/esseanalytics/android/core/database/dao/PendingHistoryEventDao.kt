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

    @Insert
    suspend fun insert(entity: PendingHistoryEventEntity): Long

    @Update
    suspend fun update(entity: PendingHistoryEventEntity)

    @Delete
    suspend fun delete(entity: PendingHistoryEventEntity)
}
