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
    val durationSeconds: Int? = null,
    val resolution: String? = null,
    val formato: String? = null,
    val platforms: List<String> = emptyList(),
    val platformsDiscarded: List<String> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
data class RemoteLibraryListResponse(val videos: List<RemoteLibraryVideoDto>)

@Serializable
data class RemoteLibraryUploadResponse(val ok: Boolean, val video: RemoteLibraryVideoDto)

@Serializable
data class UpdateRemoteLibraryPlatformsRequest(
    val platforms: List<String>? = null,
    val platformsDiscarded: List<String>? = null,
)
