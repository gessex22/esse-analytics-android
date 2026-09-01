package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.esseanalytics.android.core.database.entity.PendingPlatformUpdateEntity

@Dao
interface PendingPlatformUpdateDao {
    @Query("SELECT * FROM pending_platform_updates ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<PendingPlatformUpdateEntity>

    // REPLACE por fileName (primary key): un archivo tocado de nuevo
    // mientras el intento anterior sigue pendiente pisa el estado viejo en
    // vez de apilar -- ver comentario completo en PendingPlatformUpdateEntity.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingPlatformUpdateEntity)

    @Update
    suspend fun update(entity: PendingPlatformUpdateEntity)

    @Delete
    suspend fun delete(entity: PendingPlatformUpdateEntity)

    @Query("DELETE FROM pending_platform_updates WHERE fileName = :fileName")
    suspend fun deleteByFileName(fileName: String)
}
