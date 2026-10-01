package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import kotlinx.coroutines.flow.Flow

// DAO del journal anti-duplicados (ver PublishOperationEntity). Superficie
// mínima a propósito: find/insert/update. El índice único de la entidad +
// ABORT hacen que una colisión de clave sea un fallo RUIDOSO que se propaga
// (un error de disco/store nunca se confunde con "no hay fila" — eso
// convertiría un fallo de persistencia en luz verde para una sesión nueva).
@Dao
interface PublishOperationDao {
    @Query(
        "SELECT * FROM publish_operations " +
            "WHERE userKey = :userKey AND sourceId = :sourceId AND platform = :platform LIMIT 1",
    )
    suspend fun find(userKey: String, sourceId: String, platform: String): PublishOperationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PublishOperationEntity): Long

    @Update
    suspend fun update(entity: PublishOperationEntity)

    // Filas `blocked` del usuario, vivas (Flow): alimentan la UI de
    // recuperación de publicación ambigua (botón «Revisé la plataforma y el
    // video no se publicó», ver UploadScreen/UploadViewModel y el diseño
    // docs/ambiguous-publish-recovery-design-2026-09-22.md). La fase va en
    // literal (no hay constantes en @Query) y tiene que coincidir con
    // PublishOperationEntity.PHASE_BLOCKED; el filtro por sourceId del
    // archivo se hace en el ViewModel, que conoce el VideoFile del lote.
    @Query(
        "SELECT * FROM publish_operations " +
            "WHERE userKey = :userKey AND phase = 'blocked'",
    )
    fun observeBlocked(userKey: String): Flow<List<PublishOperationEntity>>
}
