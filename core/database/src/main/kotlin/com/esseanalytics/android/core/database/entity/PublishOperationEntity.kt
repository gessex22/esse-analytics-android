package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Journal durable anti-duplicados de publicación — un intento persistido POR
// (userKey, sourceId, platform). Sobrevive cerrar la app: un reintento
// posterior encuentra y continúa ESTA fila, nunca crea otra. Lo usan YouTube
// (sesión resumible de Google) e Instagram (contenedor de Meta); sin este
// registro, un reintento con el handle perdido crearía una SEGUNDA
// sesión/contenedor, arriesgando un video/publicación duplicada si la primera
// en realidad sí había terminado. Referencia conceptual: PublishOperation.swift
// en essenalytics-ios (mismo modelo de fases y checkpoints).
//
// Identidad de la clave: sourceId es VideoFile.clientFileId — NUNCA fileName,
// path ni el rowid de Room: el nombre se repite entre archivos distintos y el
// path cambia; clientFileId es estable por archivo (ver newClientFileId en
// core:model). El índice ÚNICO real sobre (userKey, sourceId, platform) es el
// contrato que garantiza un solo registro durable por clave — un segundo
// insert para la misma clave falla ruidosamente en vez de pisar.
//
// `operationId` es DURABLE: nace con la fila y no se refresca ni se reemplaza
// cuando el caller pasa otro (el operationId del batch de UI no autoriza a
// reemplazar un journal existente). El único camino a un operationId nuevo es
// PublishOperationStore.startNewAttempt, transición EXPLÍCITA desde
// SESSION_INVALIDATED sin evidencia fuerte de publicación.
//
// Nunca guarda tokens, rutas de archivo ni bytes del video -- solo lo mínimo
// para reanudar/reconciliar contra la plataforma remota: el handle de
// sesión/contenedor, el progreso confirmado, y si el punto de no retorno (el
// chunk final de YouTube, el media_publish de Instagram) ya se cruzó — la
// línea que separa "seguro reintentar" de "ambiguo" cuando se pierde la
// respuesta. Los checkpoints se persisten ANTES de la llamada remota
// irreversible correspondiente (ver PublishOperationStore).
@Entity(
    tableName = "publish_operations",
    indices = [Index(value = ["userKey", "sourceId", "platform"], unique = true)],
)
data class PublishOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Aislamiento por sesión, mismo criterio que platform_transition_outbox.
    val userKey: String,
    // VideoFile.clientFileId (ver arriba).
    val sourceId: String,
    // Platform.apiValue ("youtube" / "instagram"). Las fases de las dos
    // plataformas comparten nombres (pending/confirmed/blocked) con el MISMO
    // significado; nunca se comparan filas de distinta plataforma entre sí.
    val platform: String,
    // DURABLE (ver arriba). Identifica ESTE intento ante la central.
    val operationId: String,
    // Una de las fases de abajo. Texto plano a propósito: la política pura
    // (PublishOperationPolicy / InstagramOperationPolicy) decide qué hacer con
    // valores crudos/corruptos sin tocar Room, testeable en JVM.
    val phase: String = PHASE_PENDING,
    // --- YouTube ---
    val ytSessionURL: String? = null,
    val ytBytesConfirmed: Long = 0,
    val ytTotalBytes: Long? = null,
    // Se persiste ANTES de que el chunk final (el que completa ytTotalBytes)
    // salga a la red, no después de la respuesta.
    val ytFinalChunkSent: Boolean = false,
    // --- Instagram ---
    // InstagramUploadStage.rawValue ("original"/"normalized"/"trimmed60s").
    // Se persiste ANTES de crear el contenedor de esa variante, para que un
    // reinicio a mitad de un avance de etapa retome la etapa correcta.
    val igStage: String? = null,
    val igContainerId: String? = null,
    val igUploadURI: String? = null,
    // Punto de no retorno de Instagram: se persiste ANTES de pedir
    // media_publish. Meta no ofrece forma de reconciliarlo sin volver a
    // llamarlo (duplicaría la publicación), así que en true el desenlace es
    // ambiguo hasta verificación manual si no llegó a confirmarse un
    // resultPlatformId.
    val igPublishRequested: Boolean = false,
    // --- Resultado ---
    val resultPlatformId: String? = null,
    val resultURL: String? = null,
    val lastError: String? = null,
    // --- Evidencia de confirmación humana (recuperación de publicación
    // ambigua) ---
    // null = la fila nunca pasó por una confirmación humana. Después de
    // PublishOperationStore.confirmNotPublished vale CONFIRMED_NOT_PUBLISHED:
    // una persona revisó la plataforma y verificó que el video NO se publicó,
    // evidencia que se conserva INCLUSO cuando la fila vuelve a pending (ver
    // docs/ambiguous-publish-recovery-design-2026-09-22.md). No es un handle
    // reutilizable: no participa de decide(), solo de soporte/auditoría.
    val lastConfirmationAction: String? = null,
    // Epoch ms del momento de la confirmación (soporte: cuándo se revisó).
    val lastConfirmationAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    companion object {
        const val PHASE_PENDING = "pending"
        // YouTube.
        const val PHASE_SESSION_CREATED = "sessionCreated"
        const val PHASE_FINAL_CHUNK_SENT = "finalChunkSent"
        const val PHASE_SESSION_INVALIDATED = "sessionInvalidated"
        // Instagram.
        const val PHASE_CONTAINER_CREATED = "containerCreated"
        const val PHASE_PUBLISH_REQUESTED = "publishRequested"
        // Compartidas.
        const val PHASE_CONFIRMED = "confirmed"
        const val PHASE_BLOCKED = "blocked"

        // Acción de confirmación humana que habilita UN intento nuevo desde
        // una fila `blocked` (ver PublishOperationStore.confirmNotPublished y
        // el diseño docs/ambiguous-publish-recovery-design-2026-09-22.md,
        // «Recuperación de publicación ambigua sin duplicados»): la persona
        // revisó la plataforma y confirmó que el video NO se publicó. Es el
        // ÚNICO motivo de confirmación — limpiar una fila sin este registro
        // queda prohibido; si el video SÍ aparece, corresponde vincularlo por
        // el flujo de Matching, no re-subirlo.
        const val CONFIRMED_NOT_PUBLISHED = "confirmed_not_published"
    }
}
