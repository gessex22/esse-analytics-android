package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// Puerto Android de la infraestructura causal ya existente en central/iOS
// (POST /api/sync/platform-transition + /api/sync/manual-platform-link). A
// diferencia de UpdateFilePlatformsRequest (snapshot completo por fileName, ver
// SyncDtos.kt), acá cada cambio viaja como una INTENCIÓN puntual identificada
// por (contentId, platform) + operationId, con control de concurrencia
// optimista por baseVersion. La central es la autoridad de la revisión: nunca
// se infiere ni se inventa una versión del lado del cliente (ver
// CausalPlatformOutbox).

// POST /api/sync/platform-transition
//   body {contentId, platform, action, operationId, baseVersion}
//   action ∈ {discard, unlink, mark_published}
//   200 -> {version}                (aplicado; version = nueva revisión)
//   202 -> in_progress              (encolado del lado central; queda pendiente)
//   409 -> stale                    (baseVersion vieja; trae la revisión actual)
//   422 -> operation_mismatch       (terminal; el operationId ya no aplica)
//   401/429/5xx/red                 (reintentable)
@Serializable
data class PlatformTransitionRequest(
    val contentId: String,
    val platform: String,
    val action: String,
    val operationId: String,
    val baseVersion: Long,
)

// Las acciones (discard / unlink / mark_published) tienen su fuente de verdad
// única en PlatformTransitionOutboxEntity.Companion (core:database), donde el
// outbox las persiste. El flusher pasa la acción de cada fila tal cual al body,
// así que acá no se repiten.

// POST /api/sync/manual-platform-link
//   body {contentId, platform, platformId, platformUrl?, title?, publishedAt?, operationId}
//   200 -> {version}
//   202 -> in_progress
//   409 -> stale / missing_identity
//   422 -> mismatch
// NO lleva baseVersion (a diferencia de la transición): guardar un link a mano
// no depende de una revisión previa, solo de que la identidad (contentId)
// exista del lado central.
@Serializable
data class ManualPlatformLinkRequest(
    val contentId: String,
    val platform: String,
    val platformId: String,
    val platformUrl: String? = null,
    val title: String? = null,
    val publishedAt: String? = null,
    val operationId: String,
)

// Body compartido de las dos respuestas causales. Todos los campos opcionales
// a propósito: el código HTTP es el que lleva la semántica (ver
// CausalPlatformOutbox.classify), esto solo aporta la revisión nueva (200) o la
// revisión autoritativa cuando hubo conflicto (409 stale). `error` es
// informativo (operation_mismatch / stale / missing_identity / mismatch) --
// nunca se ramifica por su contenido, solo por el status. El Json de red usa
// ignoreUnknownKeys=true (ver NetworkModule), así que un campo extra o un
// nombre distinto del lado central no rompe la decodificación.
@Serializable
data class PlatformCausalResponse(
    val version: Long? = null,
    val status: String? = null,
    val error: String? = null,
    val contentId: String? = null,
)

// POST /api/sync/resolve-identity -- bootstrap de identidad para un archivo que
// NO pasó por la cola remota (no tiene RemoteLibraryVideoDto del que hidratar
// contentId). La central resuelve su propia identidad canónica a partir del
// fileName (y el remoteLibraryVideoId si existe); el cliente NUNCA fabrica el
// contentId a partir del nombre -- lo pide y lo cachea tal cual lo devuelve la
// central (ver PlatformIdentityStore.resolveContentId). Si la central todavía
// no conoce ese contenido, responde sin `contentId` (o 404) y la transición
// queda excluida hasta el próximo intento -- degradación segura, nunca se
// manda a ciegas.
@Serializable
data class ResolveIdentityRequest(
    val fileName: String,
    val remoteLibraryVideoId: String? = null,
)

@Serializable
data class ResolveIdentityResponse(
    val contentId: String? = null,
    // Revisión actual por plataforma (apiValue -> versión), igual que
    // RemoteLibraryVideoDto.platformRev -- baseVersion real contra la que la
    // central valida una transición. Opcional: sin esto, una transición espera
    // a conocer su revisión antes de salir (queda excluida, nunca inventa base).
    val platformRev: Map<String, Long>? = null,
)
