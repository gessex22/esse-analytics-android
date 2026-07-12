package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformVideoDao {
    @Query("SELECT * FROM platform_videos WHERE linkedFileId = :fileId")
    fun findByLinkedFile(fileId: Long): Flow<List<PlatformVideoEntity>>

    @Query("SELECT * FROM platform_videos WHERE platform = :platform AND platformId = :platformId LIMIT 1")
    suspend fun findByPlatformAndId(platform: String, platformId: String): PlatformVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlatformVideoEntity): Long

    @Update
    suspend fun update(entity: PlatformVideoEntity)
}
