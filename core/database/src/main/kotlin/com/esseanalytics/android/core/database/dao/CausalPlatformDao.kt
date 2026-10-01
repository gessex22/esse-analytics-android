package com.esseanalytics.android.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity

// DAO único de la infraestructura causal -- identidad (content_identity),
// revisión (platform_revision) y outbox (platform_transition_outbox). Se
// mantienen juntas porque el flush y la hidratación las cruzan en la misma
// transacción (ver CausalPlatformOutbox / PlatformTransitionRepository).
@Dao
interface CausalPlatformDao {

    // --- Identidad -------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentity(entity: ContentIdentityEntity)

    @Query("SELECT contentId FROM content_identity WHERE userKey = :userKey AND localKey = :localKey LIMIT 1")
    suspend fun findContentId(userKey: String, localKey: String): String?

    // --- Revisión --------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRevision(entity: PlatformRevisionEntity)

    @Query(
        "SELECT revision FROM platform_revision " +
            "WHERE userKey = :userKey AND contentId = :contentId AND platform = :platform LIMIT 1",
    )
    suspend fun findRevision(userKey: String, contentId: String, platform: String): Long?

    // --- Outbox ----------------------------------------------------------
    @Insert
    suspend fun insertOutbox(entity: PlatformTransitionOutboxEntity): Long

    @Update
    suspend fun updateOutbox(entity: PlatformTransitionOutboxEntity)

    @Delete
    suspend fun deleteOutbox(entity: PlatformTransitionOutboxEntity)

    @Query("SELECT * FROM platform_transition_outbox ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getAllOutbox(): List<PlatformTransitionOutboxEntity>

    // Filas de ESTE usuario con identidad ya resuelta, en orden de cadena
    // (contentId, platform) y por antigüedad dentro de la cadena. El flusher
    // agrupa por (contentId, platform) y envía SOLO la cabeza de cada cadena:
    // un descendiente nunca sale con una baseVersion vieja porque no se toca
    // hasta que su ancestro se resuelve (200/409/422) y avanza la revisión.
    @Query(
        "SELECT * FROM platform_transition_outbox " +
            "WHERE userKey = :userKey AND contentId IS NOT NULL " +
            "ORDER BY contentId ASC, platform ASC, createdAtEpochMs ASC, id ASC",
    )
    suspend fun getResolvable(userKey: String): List<PlatformTransitionOutboxEntity>

    // Filas de ESTE usuario sin identidad todavía Y con un handle local con el
    // que se pueda resolver -- candidatas al bootstrap por resolve-identity (ver
    // CausalFlusher.bootstrapIdentities). El filtro por clientFileId NO es una
    // optimización: resolve-identity exige (deviceId, clientFileId), así que una
    // fila sin clientFileId no es resoluble y pedirla sería mandar una
    // identidad vacía a la central. Esas filas (legacy de v6 sin evidencia
    // inequívoca, ver MIGRATION_6_7) se CONSERVAN en la tabla, simplemente no
    // se envían -- falla cerrado, nunca se adivina por fileName.
    @Query(
        "SELECT * FROM platform_transition_outbox " +
            "WHERE userKey = :userKey AND contentId IS NULL AND clientFileId IS NOT NULL",
    )
    suspend fun getUnresolved(userKey: String): List<PlatformTransitionOutboxEntity>

    // Contraparte de getUnresolved: filas atascadas sin identidad NI handle
    // local. Solo para diagnóstico/UI (no se envían nunca) -- existe para que
    // "se conserva la fila" sea verificable y no un efecto invisible.
    @Query(
        "SELECT COUNT(*) FROM platform_transition_outbox " +
            "WHERE userKey = :userKey AND contentId IS NULL AND clientFileId IS NULL",
    )
    suspend fun countBlockedLegacy(userKey: String): Int

    // Pendientes del mismo usuario+plataforma (cualquier kind) -- base para el
    // dedup y para saber si una cadena (target, platform) ya tiene una fila
    // encolada. La igualdad final de "target" (contentId | remoteLibraryVideoId |
    // clientFileId, con nulls) se resuelve en Kotlin (ver targetMatches).
    @Query("SELECT * FROM platform_transition_outbox WHERE userKey = :userKey AND platform = :platform")
    suspend fun pendingForPlatform(userKey: String, platform: String): List<PlatformTransitionOutboxEntity>

    // Hidratación de identidad al aprenderla de la central: completa las filas
    // que quedaron esperando. Solo toca filas SIN resolver -- nunca pisa un
    // contentId ya fijado. La baseVersion NO se hidrata en bloque a propósito:
    // se resuelve perezosamente sobre la cabeza de cada cadena en el flusher,
    // para que un descendiente no herede una base que corresponde a su ancestro.
    @Query(
        "UPDATE platform_transition_outbox SET contentId = :contentId " +
            "WHERE userKey = :userKey AND remoteLibraryVideoId = :remoteLibraryVideoId AND contentId IS NULL",
    )
    suspend fun hydrateContentIdByRemoteId(userKey: String, remoteLibraryVideoId: String, contentId: String)

    // Reemplaza a la hidratación por fileName (borrada): el nombre podía
    // completar de golpe filas de DOS archivos distintos que se llamaran igual,
    // atribuyéndoles el mismo contentId. clientFileId es 1:1 con el archivo.
    @Query(
        "UPDATE platform_transition_outbox SET contentId = :contentId " +
            "WHERE userKey = :userKey AND clientFileId = :clientFileId AND contentId IS NULL",
    )
    suspend fun hydrateContentIdByClientFileId(userKey: String, clientFileId: String, contentId: String)
}
