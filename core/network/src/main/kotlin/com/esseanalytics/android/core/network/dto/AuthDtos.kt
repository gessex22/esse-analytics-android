package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class UserDto(
    val username: String,
    val role: String,
    val tier: String,
    val isOwner: Boolean,
    // Plan aparte de tier==='premium' -- default false para no romper la
    // deserialización si la central todavía no lo manda (ver Parte D del plan).
    val hasCloudStorage: Boolean = false,
    val theme: String? = null,
)

@Serializable
data class LoginResponse(val token: String, val user: UserDto)

@Serializable
data class LinkInstallRequest(val installId: String)
