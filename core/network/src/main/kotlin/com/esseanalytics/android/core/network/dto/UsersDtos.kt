package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// Mismo shape que ya consume frontend/src/components/UsersPanel.tsx contra
// GET /api/auth/users -- pantalla owner-only, la central es la que valida
// el rol (403 si no sos todopoderoso), acá no se duplica esa lógica.
@Serializable
data class AppUserDto(
    val id: String,
    val username: String,
    val role: String,
    val tier: String,
    val hasCloudStorage: Boolean = false,
    val status: String,
    val email: String? = null,
    val linkedPlatforms: List<String>? = null,
    val youtubeChannel: String? = null,
    val youtubeChannelUrl: String? = null,
    val instagramAccount: String? = null,
    val tiktokAccount: String? = null,
    val firstLinkedAt: String? = null,
    val createdAt: String? = null,
    val deletedAt: String? = null,
)

@Serializable
data class UsersListResponse(val users: List<AppUserDto> = emptyList(), val total: Int = 0)

@Serializable
data class UpdateTierRequest(val tier: String)

@Serializable
data class UpdateCloudStorageRequest(val hasCloudStorage: Boolean)
