package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.esseanalytics.android.core.database.entity.FileEntity
import kotlinx.coroutines.flow.Flow

// Superficie portada de local-backend/src/db/file.repo.ts — nombres alineados
// a propósito para que sea fácil comparar contra el original al portar lógica.
@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE filePath = :path LIMIT 1")
    suspend fun findByPath(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE fileName = :name LIMIT 1")
    suspend fun findByName(name: String): FileEntity?

    // Link explícito primero (ver ImportUseCase.importFromRemoteLibrary) --
    // más confiable que fileName, que se puede repetir o venir con doble
    // extensión.
    @Query("SELECT * FROM files WHERE remoteLibraryVideoId = :remoteId LIMIT 1")
    suspend fun findByRemoteLibraryVideoId(remoteId: String): FileEntity?

    @Query("SELECT * FROM files WHERE status != 'ELIMINADO_DISCO' ORDER BY fechaCreacionEpochMs DESC")
    fun findAll(): Flow<List<FileEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE status != 'ELIMINADO_DISCO'
          AND NOT (',' || platforms || ',' LIKE '%,' || :platform || ',%')
          AND NOT (',' || platformsDiscarded || ',' LIKE '%,' || :platform || ',%')
        ORDER BY fechaCreacionEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun findNextUnpublished(platform: String): FileEntity?

    @Query("SELECT COUNT(*) FROM files WHERE status != 'ELIMINADO_DISCO'")
    fun countAll(): Flow<Int>

    // El siguiente más nuevo que el dado, para la cola de "próximo video" del
    // calendario (Fase 1) — mismo criterio que findNewerAdjacent en desktop.
    @Query(
        """
        SELECT * FROM files
        WHERE status != 'ELIMINADO_DISCO' AND fechaCreacionEpochMs > :afterEpochMs
        ORDER BY fechaCreacionEpochMs ASC
        LIMIT 1
        """,
    )
    suspend fun findNewerAdjacent(afterEpochMs: Long): FileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: FileEntity): Long

    @Update
    suspend fun update(file: FileEntity)
}
