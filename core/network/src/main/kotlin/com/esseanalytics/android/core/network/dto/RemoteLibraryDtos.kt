package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// Mismo shape que devuelve RemoteLibraryVideoModel (backend/src/models/
// remote-library-video.model.ts) serializado por Mongoose -- _id como
// identificador Mongo estándar, igual que el resto de los DTOs de sync
// (ver SyncCandidateDto/SyncReviewItemDto).
@Serializable
data class RemoteLibraryVideoDto(
    val _id: String,
    val fileName: String,
    val sizeBytes: Long,
    // Double, no Int -- la central lo guarda tal cual lo manda el cliente que
    // probó el video (AVFoundation en iOS, AndroidMediaProber acá), que da
    // segundos con decimales (ej. 27.656). Con Int, listar Nube tiraba
    // "Unexpected symbol '.' in numeric literal" apenas había un video subido
    // desde iOS en la lista -- Nube entera dejaba de cargar para todos.
    val durationSeconds: Double? = null,
    val resolution: String? = null,
    val formato: String? = null,
    val platforms: List<String> = emptyList(),
    val platformsDiscarded: List<String> = emptyList(),
    // Faltaba -- sin esto no había forma de saber si un video de la cola YA
    // tenía un link guardado (para precargar el editor) ni de mostrarlo. Ver
    // RemoteVideoEditViewModel.
    val platformLinks: List<RemoteLibraryPlatformLinkDto> = emptyList(),
    val createdAt: String? = null,
    // Faltaba del todo -- sin este campo no había forma de saber si el video
    // tenía miniatura ni de armar su URL (ver remoteLibraryThumbnailUrl), así
    // que Nube nunca mostró una miniatura real, siempre el ícono genérico.
    val thumbnailStoredFileName: String? = null,
)

@Serializable
data class RemoteLibraryListResponse(val videos: List<RemoteLibraryVideoDto>)

@Serializable
data class RemoteLibraryUploadResponse(val ok: Boolean, val video: RemoteLibraryVideoDto)

// Vínculo real archivo↔publicación (platformId/URL) -- platforms/
// platformsDiscarded de arriba solo dicen SI/NO por plataforma, esto manda el
// link real de esa publicación. Mirror de RemoteLibraryPlatformLinkDTO en iOS
// y platformLinkSchema en remote-library-video.model.ts (platform/platformId/
// publishedAt requeridos ahí, platformUrl opcional).
@Serializable
data class RemoteLibraryPlatformLinkDto(
    val platform: String,
    val platformId: String,
    val platformUrl: String? = null,
    val publishedAt: String,
)

@Serializable
data class UpdateRemoteLibraryPlatformsRequest(
    val platforms: List<String>? = null,
    val platformsDiscarded: List<String>? = null,
    // Default null, no [] -- el controller (updateRemoteLibraryVideoPlatforms)
    // solo toca platformLinks si viene un array con al menos 1 elemento, así
    // que togglear el badge sin editar ningún link (ver
    // VideoDetailViewModel.togglePlatform) no pisa nada.
    val platformLinks: List<RemoteLibraryPlatformLinkDto>? = null,
)
