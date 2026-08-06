package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// installationId/deviceName/source (Fase 5, auditoría central): opcionales
// del lado del backend -- un login sin estos campos sigue funcionando, solo
// sin evento de auditoría (mismo criterio que LoginRequest de iOS, mirror
// 1:1 de este archivo).
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val installationId: String? = null,
    val deviceName: String? = null,
    val source: String? = null,
)
@Serializable
data class RegisterRequest(val username: String, val password: String, val email: String? = null)

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
