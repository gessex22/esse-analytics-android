package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Outbox causal de transiciones/links (mirror de la infra ya existente en
// central/iOS). A diferencia de PendingPlatformUpdateEntity (snapshot completo
// por fileName, REEMPLAZO), acá cada fila es una INTENCIÓN puntual y durable,
// persistida ANTES/junto con el cambio local (misma transacción Room, ver
// PlatformTransitionRepository) para que un corte de red o un cierre de la app
// no la pierdan. Se drena en orden de cadena (contentId, platform,
// createdAtEpochMs) para respetar la causalidad de un mismo contenido.
//
// `operationId` y `baseVersion` son durables: se fijan al encolar y no se
// recalculan (no se "inventa" una baseVersion nueva en cada intento). Una fila
// SIN identidad (`contentId` == null) o -- para transiciones -- sin
// `baseVersion` queda EXCLUIDA del envío hasta que se hidrate la identidad/
// revisión desde la central (ver CausalPlatformOutbox.hydratePending); nunca se
// manda a ciegas. `userKey` (id del usuario logueado al encolar) evita enviar
// con otra sesión.
//
// `kind`:
//   KIND_TRANSITION -> POST /api/sync/platform-transition (usa action + baseVersion)
//   KIND_LINK        -> POST /api/sync/manual-platform-link (usa platformId/Url/title/publishedAt, sin baseVersion)
@Entity(
    tableName = "platform_transition_outbox",
    indices = [Index(value = ["userKey", "contentId", "platform"])],
)
data class PlatformTransitionOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userKey: String,
    val kind: String,
    // Identidad causal -- null hasta que se resuelva desde la central. Se guarda
    // remoteLibraryVideoId como handle local para volver a resolver contentId
    // más tarde; fileName es solo para el registro de historial (record-publish)
    // y trazas, NUNCA para inferir un contentId.
    val contentId: String?,
    val remoteLibraryVideoId: String?,
    val fileName: String?,
    val platform: String,
    val action: String?,
    val operationId: String,
    val baseVersion: Long?,
    // Payload de KIND_LINK (manual-platform-link) -- null para transiciones.
    val platformId: String?,
    val platformUrl: String?,
    val title: String?,
    val publishedAt: String?,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
) {
    companion object {
        const val KIND_TRANSITION = "transition"
        const val KIND_LINK = "link"

        // Acciones de una transición (KIND_TRANSITION). Fuente de verdad única
        // -- core:network las reusa al armar el body y core:database las escribe
        // acá, sin literales sueltos repartidos. Mapeo desde el ciclo de badge
        // (pendiente->publicado->descartado->pendiente): publicado -> DISCARD,
        // descartado -> UNLINK, pendiente -> MARK_PUBLISHED (mismo criterio iOS).
        const val ACTION_DISCARD = "discard"
        const val ACTION_UNLINK = "unlink"
        const val ACTION_MARK_PUBLISHED = "mark_published"
    }
}
