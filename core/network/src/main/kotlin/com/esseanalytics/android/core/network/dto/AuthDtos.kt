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
    val theme: String? = null,
)

@Serializable
data class LoginResponse(val token: String, val user: UserDto)

@Serializable
data class LinkInstallRequest(val installId: String)
