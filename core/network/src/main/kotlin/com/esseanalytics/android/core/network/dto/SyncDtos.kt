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
)

// Mismos DTOs que ya consume frontend/src/components/SyncPanel.tsx --
// feature:sync (Fase 2, "Vincular con archivo local" + "Emparejar entre
// plataformas"). Ver frontend/src/services/api.ts para el shape original.

@Serializable
data class SyncCandidateDto(
    val _id: String,
    val file_name: String,
    val duracion_segundos: Int,
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
    val durationSeconds: Int,
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
