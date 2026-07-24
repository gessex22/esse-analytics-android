package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupStatsSlotDto(
    val platformId: String,
    val platformUrl: String,
    val title: String,
    val thumbnail: String,
    val views: Int,
    val likes: Int,
    val comments: Int,
)

@Serializable
data class GroupStatsItemDto(
    val fileId: String,
    val fileName: String,
    val fecha_creacion: String,
    val platforms: Map<String, GroupStatsSlotDto>,
    // Cuando hay match en Biblioteca remota (por fileName, resuelto server-side
    // -- ver getGroupStats en sync.controller.ts), permiten pedir la miniatura
    // puntual de ESE video en vez de traer un batch aparte de la cola remota y
    // adivinar por nombre (mismo fix que StatsView/GroupStatsCard en iOS,
    // commit "Corrige congelamiento... optimiza rendimiento de red").
    val remoteLibraryVideoId: String? = null,
    val thumbnailStoredFileName: String? = null,
)

@Serializable
data class GroupStatsResponse(val items: List<GroupStatsItemDto>)

@Serializable
data class CalendarConfigDto(
    val platform: String,
    val lastPublishedTitle: String,
    val lastPublishedDate: String,
    val intervalDays: Int,
    val lastVideoId: String? = null,
    val nextVideoId: String? = null,
    // La central YA resuelve nextVideoId (que es un ObjectId de Mongo o un
    // título, nunca un id de Room) contra FileModel server-side -- ver
    // sync.controller.ts, GET /api/sync/calendar-config. Usar esto directo en
    // vez de tratar nextVideoId como id local (CalendarViewModel lo hacía mal,
    // por eso siempre mostraba "Sin definir").
    val nextVideo: NextVideoDto? = null,
)

@Serializable
data class NextVideoDto(
    val fileId: String,
    val title: String,
    val duration: String,
)

// Mismos DTOs que ya consume frontend/src/components/SyncPanel.tsx --
// feature:sync (Fase 2, "Vincular con archivo local" + "Emparejar entre
// plataformas"). Ver frontend/src/services/api.ts para el shape original.

@Serializable
data class SyncCandidateDto(
    val _id: String,
    val file_name: String,
    // Double, NO Int -- mismo motivo que duracion_segundos en BackupFileDto
    // (ver ese comentario): FileModel.duracion_segundos es un Number sin
    // restricción de entero, y llega con decimales reales (ej. 27.656).
    val duracion_segundos: Double,
    val fecha_creacion: String? = null,
    val formato: String? = null,
)

@Serializable
data class SyncReviewItemDto(
    val _id: String,
    val platformId: String,
    val platformUrl: String,
    val title: String,
    val thumbnail: String,
    // Double, NO Int -- PlatformVideoModel.durationSeconds es un Number sin
    // restricción de entero, mismo riesgo que duracion_segundos arriba.
    val durationSeconds: Double,
    val publishedAt: String,
    val views: Int = 0,
    val candidates: List<SyncCandidateDto> = emptyList(),
)

@Serializable
data class SyncReviewResponseDto(
    val total: Int,
    val page: Int,
    val totalPages: Int,
    val items: List<SyncReviewItemDto>,
)

@Serializable
data class SyncStatsDto(
    val youtube: Int,
    val instagram: Int,
    val tiktok: Int,
    val linked: Int,
    val revisar: Int,
    val sinMatch: Int,
)

@Serializable
data class PlatformRecentItemDto(
    val platformId: String,
    val title: String,
    val thumbnail: String? = null,
    val publishedAt: String? = null,
    val platformUrl: String? = null,
)

@Serializable
data class PlatformRecentPageDto(
    val items: List<PlatformRecentItemDto>,
    val nextCursor: String? = null,
)

@Serializable
data class CrossMatchResolvedSlotDto(
    val platformId: String,
    val platformUrl: String,
    val title: String,
    val thumbnail: String? = null,
)

@Serializable
data class CrossMatchResolvedDto(
    val youtube: CrossMatchResolvedSlotDto? = null,
    val instagram: CrossMatchResolvedSlotDto? = null,
    val tiktok: CrossMatchResolvedSlotDto? = null,
)

@Serializable
data class CrossMatchCandidateDto(
    val fileId: String,
    val fileName: String,
    val fecha_creacion: String? = null,
    val resolved: CrossMatchResolvedDto,
)

@Serializable
data class CrossMatchCandidatesResponseDto(
    val items: List<CrossMatchCandidateDto>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
)

@Serializable
data class ConfirmLinkRequest(val fileId: String)

// POST /api/sync/record-publish (alias de /api/sync/history, ver
// recordUploadEvent en backup.controller.ts) -- registro central puntual de
// UNA publicación, independiente de Biblioteca remota. Antes de esto solo
// iOS (SyncAPI.recordPublish) lo llamaba; sin esto, un link cargado a mano
// desde Android no aparecía en Estadísticas/Sincronizar (getGroupStats,
// getCrossMatchCandidates) aunque el archivo local ya lo tuviera.
@Serializable
data class RecordPublishRequest(
    val platform: String,
    val platformId: String,
    val platformUrl: String? = null,
    val fileName: String? = null,
    val title: String? = null,
    val publishedAt: String? = null,
)

@Serializable
data class TriggerSyncResponse(val ok: Boolean, val total: Int, val upserted: Int)

@Serializable
data class ResolveCrossMatchSlotRequest(
    val fileId: String,
    val platform: String,
    val platformId: String,
    val title: String? = null,
    val thumbnail: String? = null,
    val publishedAt: String? = null,
    val platformUrl: String? = null,
)
