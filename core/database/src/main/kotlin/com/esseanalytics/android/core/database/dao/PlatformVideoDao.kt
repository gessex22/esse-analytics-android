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

    // El índice único (platform, platformId) NO evita que dos filas de la
    // MISMA plataforma apunten al MISMO linkedFileId (ej: re-publicar el
    // mismo video a la misma red genera un platformId nuevo) -- mismo bug
    // que se encontró y arregló del lado de desktop (resolveCrossMatchSlot,
    // backend/src/controllers/sync.controller.ts). Se llama ANTES del upsert
    // en PlatformVideoRepository.upsertPublished para no repetirlo acá.
    @Query(
        "UPDATE platform_videos SET linkedFileId = NULL, matchStatus = 'sin_match' " +
            "WHERE platform = :platform AND linkedFileId = :fileId",
    )
    suspend fun unlinkOthersForFile(platform: String, fileId: Long)
}
