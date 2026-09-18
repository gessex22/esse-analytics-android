package com.esseanalytics.android.core.database

import androidx.room.withTransaction
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.ACTION_DISCARD
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.ACTION_MARK_PUBLISHED
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.ACTION_UNLINK
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.KIND_LINK
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.KIND_TRANSITION
import com.esseanalytics.android.core.database.util.toDomain
import com.esseanalytics.android.core.database.util.toEntity
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Puerta de entrada para persistir INTENCIONES causales (transición de badge o
// link manual) en el outbox durable. A diferencia de FileRepository (estado
// local puro) o PlatformUpdateOutbox (snapshot legado), acá cada llamada
// encola una fila puntual que el CausalPlatformOutbox drena más tarde contra la
// central. Vive en core:database para poder encolar la intención EN LA MISMA
// transacción Room que el cambio local (ver toggleAndEnqueue): si el insert
// del outbox falla, el badge no cambia -- nunca queda un cambio local sin su
// intención causal, ni al revés.
//
// El `userKey` (id del usuario logueado) lo pasa el caller -- core:database no
// depende de la sesión/red a propósito. La identidad (contentId) se resuelve
// desde el cache local (content_identity); si todavía no se conoce, la fila
// queda con contentId=null y el flusher la resuelve por resolve-identity antes
// de enviar (nunca se infiere del fileName acá).
@Singleton
class PlatformTransitionRepository @Inject constructor(
    private val db: EsseAnalyticsDatabase,
    private val fileDao: FileDao,
    private val causalDao: CausalPlatformDao,
) {
    // Ciclo de badge (pendiente->publicado->descartado->pendiente) + intención
    // causal, atómico. Devuelve el archivo actualizado, o null si no se pudo
    // (archivo inexistente, o el insert del outbox falló y Room revirtió todo).
    // La acción se deriva del estado ANTES del toggle: publicado -> discard,
    // descartado -> unlink, pendiente -> mark_published.
    suspend fun toggleAndEnqueue(fileId: Long, platform: Platform, userKey: String): VideoFile? =
        db.withTransaction {
            val file = fileDao.findById(fileId)?.toDomain() ?: return@withTransaction null
            val action: String
            val newPlatforms: List<Platform>
            val newDiscarded: List<Platform>
            when {
                platform in file.platforms -> {
                    action = ACTION_DISCARD
                    newPlatforms = file.platforms.filter { it != platform }
                    newDiscarded = if (platform in file.platformsDiscarded) file.platformsDiscarded else file.platformsDiscarded + platform
                }
                platform in file.platformsDiscarded -> {
                    action = ACTION_UNLINK
                    newPlatforms = file.platforms
                    newDiscarded = file.platformsDiscarded.filter { it != platform }
                }
                else -> {
                    action = ACTION_MARK_PUBLISHED
                    newPlatforms = file.platforms + platform
                    newDiscarded = file.platformsDiscarded
                }
            }
            enqueueTransitionInternal(
                userKey = userKey,
                contentId = resolveCachedContentId(userKey, file.remoteLibraryVideoId, file.fileName),
                remoteLibraryVideoId = file.remoteLibraryVideoId,
                fileName = file.fileName,
                platform = platform,
                action = action,
            )
            val updated = file.copy(
                platforms = newPlatforms,
                platformsDiscarded = newDiscarded,
                updatedAt = Instant.now(),
            )
            fileDao.update(updated.toEntity())
            updated
        }

    // Link manual de un archivo local -- identidad desde cache, base N/A (los
    // links no llevan baseVersion). El registro de historial (record-publish)
    // ya no se dispara acá: el manual-platform-link ES el registro causal (ver
    // VideoDetailViewModel.saveLink).
    suspend fun enqueueManualLink(
        userKey: String,
        fileName: String,
        remoteLibraryVideoId: String?,
        platform: Platform,
        platformId: String,
        platformUrl: String?,
        title: String?,
        publishedAt: String?,
    ) = db.withTransaction {
        enqueueLinkInternal(
            userKey = userKey,
            contentId = resolveCachedContentId(userKey, remoteLibraryVideoId, fileName),
            remoteLibraryVideoId = remoteLibraryVideoId,
            fileName = fileName,
            platform = platform,
            platformId = platformId,
            platformUrl = platformUrl,
            title = title,
            publishedAt = publishedAt,
        )
    }

    // Borrar el link de un archivo local -> transición unlink causal.
    suspend fun enqueueUnlink(
        userKey: String,
        fileName: String,
        remoteLibraryVideoId: String?,
        platform: Platform,
    ) = db.withTransaction {
        enqueueTransitionInternal(
            userKey = userKey,
            contentId = resolveCachedContentId(userKey, remoteLibraryVideoId, fileName),
            remoteLibraryVideoId = remoteLibraryVideoId,
            fileName = fileName,
            platform = platform,
            action = ACTION_UNLINK,
        )
    }

    // Editor de un video de la cola remota: la identidad (contentId) y la
    // revisión (platformRev) YA vienen en el DTO -- se pasan explícitas. El
    // caller (RemoteVideoEditViewModel) ya validó que existan (si no, falla y
    // revierte la UI sin llegar acá).
    suspend fun enqueueKnownTransition(
        userKey: String,
        contentId: String,
        remoteLibraryVideoId: String?,
        fileName: String?,
        platform: Platform,
        action: String,
        baseVersion: Long?,
    ) = db.withTransaction {
        enqueueTransitionInternal(
            userKey = userKey,
            contentId = contentId,
            remoteLibraryVideoId = remoteLibraryVideoId,
            fileName = fileName,
            platform = platform,
            action = action,
            knownBaseVersion = baseVersion,
        )
    }

    suspend fun enqueueKnownLink(
        userKey: String,
        contentId: String,
        remoteLibraryVideoId: String?,
        fileName: String?,
        platform: Platform,
        platformId: String,
        platformUrl: String?,
        title: String?,
        publishedAt: String?,
    ) = db.withTransaction {
        enqueueLinkInternal(
            userKey = userKey,
            contentId = contentId,
            remoteLibraryVideoId = remoteLibraryVideoId,
            fileName = fileName,
            platform = platform,
            platformId = platformId,
            platformUrl = platformUrl,
            title = title,
            publishedAt = publishedAt,
        )
    }

    // --- internos (siempre dentro de withTransaction) --------------------

    private suspend fun enqueueTransitionInternal(
        userKey: String,
        contentId: String?,
        remoteLibraryVideoId: String?,
        fileName: String?,
        platform: Platform,
        action: String,
        knownBaseVersion: Long? = null,
    ) {
        val existing = existingForTarget(userKey, platform, contentId, remoteLibraryVideoId, fileName)
        // Dedup SOLO del mismo cambio pendiente: si la última fila encolada de
        // esta cadena es una transición idéntica (misma acción), no se apila
        // otra. Un cambio distinto (o el mismo separado por otros) sí encola --
        // la causalidad [A,B,A] se preserva.
        val tail = existing.maxByOrNull { it.createdAtEpochMs * 1_000_000 + it.id }
        if (tail != null && tail.kind == KIND_TRANSITION && tail.action == action) return
        // baseVersion durable SOLO en la cabeza de la cadena. Si esta fila es la
        // cabeza (no hay pendientes previos del mismo target): se usa la base
        // conocida (editor remoto, desde platformRev) o, si no, la revisión real
        // cacheada. Si es DESCENDIENTE (ya hay pendientes): base=null a
        // propósito -- se resuelve perezosamente cuando llegue a ser cabeza,
        // contra la revisión que dejó su ancestro al aplicarse (nunca hereda una
        // base vieja ni se inventa una).
        val baseVersion = when {
            existing.isNotEmpty() -> null
            knownBaseVersion != null -> knownBaseVersion
            contentId != null -> causalDao.findRevision(userKey, contentId, platform.apiValue)
            else -> null
        }
        causalDao.insertOutbox(
            PlatformTransitionOutboxEntity(
                userKey = userKey,
                kind = KIND_TRANSITION,
                contentId = contentId,
                remoteLibraryVideoId = remoteLibraryVideoId,
                fileName = fileName,
                platform = platform.apiValue,
                action = action,
                operationId = UUID.randomUUID().toString(),
                baseVersion = baseVersion,
                platformId = null,
                platformUrl = null,
                title = null,
                publishedAt = null,
            ),
        )
    }

    private suspend fun enqueueLinkInternal(
        userKey: String,
        contentId: String?,
        remoteLibraryVideoId: String?,
        fileName: String?,
        platform: Platform,
        platformId: String,
        platformUrl: String?,
        title: String?,
        publishedAt: String?,
    ) {
        val existing = existingForTarget(userKey, platform, contentId, remoteLibraryVideoId, fileName)
        val tail = existing.maxByOrNull { it.createdAtEpochMs * 1_000_000 + it.id }
        if (tail != null && tail.kind == KIND_LINK && tail.platformId == platformId && tail.platformUrl == platformUrl) return
        causalDao.insertOutbox(
            PlatformTransitionOutboxEntity(
                userKey = userKey,
                kind = KIND_LINK,
                contentId = contentId,
                remoteLibraryVideoId = remoteLibraryVideoId,
                fileName = fileName,
                platform = platform.apiValue,
                action = null,
                operationId = UUID.randomUUID().toString(),
                baseVersion = null,
                platformId = platformId,
                platformUrl = platformUrl,
                title = title,
                publishedAt = publishedAt,
            ),
        )
    }

    private suspend fun resolveCachedContentId(
        userKey: String,
        remoteLibraryVideoId: String?,
        fileName: String?,
    ): String? {
        remoteLibraryVideoId?.let { causalDao.findContentId(userKey, CausalKeys.forRemote(it))?.let { c -> return c } }
        fileName?.let { causalDao.findContentId(userKey, CausalKeys.forFile(it))?.let { c -> return c } }
        return null
    }

    private suspend fun existingForTarget(
        userKey: String,
        platform: Platform,
        contentId: String?,
        remoteLibraryVideoId: String?,
        fileName: String?,
    ): List<PlatformTransitionOutboxEntity> =
        causalDao.pendingForPlatform(userKey, platform.apiValue).filter { row ->
            (contentId != null && row.contentId == contentId) ||
                (remoteLibraryVideoId != null && row.remoteLibraryVideoId == remoteLibraryVideoId) ||
                (fileName != null && row.fileName == fileName)
        }
}
