package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esseanalytics.android.core.database.entity.PendingCatalogRegistrationEntity

@Dao
interface PendingCatalogRegistrationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(row: PendingCatalogRegistrationEntity)

    @Query("SELECT * FROM pending_catalog_registrations WHERE userKey = :userKey AND serverUrl = :serverUrl AND (state = 'pending' OR (state = 'completed' AND thumbnailPath IS NOT NULL AND thumbnailUploaded = 0)) AND nextAttemptAtEpochMs <= :now ORDER BY id")
    suspend fun pending(userKey: String, serverUrl: String, now: Long): List<PendingCatalogRegistrationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_catalog_registrations WHERE userKey = :userKey AND serverUrl = :serverUrl AND clientFileId = :clientFileId)")
    suspend fun exists(userKey: String, serverUrl: String, clientFileId: String): Boolean

    @Query("UPDATE pending_catalog_registrations SET state = 'completed', contentId = :contentId, lastError = NULL WHERE id = :id")
    suspend fun markCompleted(id: Long, contentId: String)

    @Query("UPDATE pending_catalog_registrations SET thumbnailUploaded = 1, lastError = NULL WHERE id = :id")
    suspend fun markThumbnailUploaded(id: Long)

    @Query("UPDATE pending_catalog_registrations SET attempts = attempts + 1, nextAttemptAtEpochMs = :nextAt, lastError = :error WHERE id = :id")
    suspend fun recordAttempt(id: Long, nextAt: Long, error: String?)

    @Query("UPDATE pending_catalog_registrations SET state = 'blocked', lastError = :error WHERE id = :id")
    suspend fun block(id: Long, error: String)
}
