package com.esseanalytics.android.feature.auth

import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.User
import com.esseanalytics.android.core.network.JwtUtils
import com.esseanalytics.android.core.network.api.AuthApi
import com.esseanalytics.android.core.network.dto.LinkInstallRequest
import com.esseanalytics.android.core.network.dto.LoginRequest
import com.esseanalytics.android.core.network.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
) {
    suspend fun login(username: String, password: String): Result<User> = runCatching {
        val response = authApi.login(LoginRequest(username, password))
        val userId = JwtUtils.decodeUserId(response.token)
            ?: error("El token no trae id de usuario — revisar el JWT que devuelve la central")
        val user = response.user.toDomain(userId)
        tokenStore.save(response.token, user)
        ensureInstallLinked()
        user
    }

    // POST /api/auth/link-install una sola vez por instalación — requerido
    // antes de cualquier operación destructiva (ver auth.routes.ts). No-fatal:
    // si falla (sin red, etc.) el login ya se completó, se reintenta después.
    private suspend fun ensureInstallLinked() {
        runCatching {
            val installId = settingsStore.getOrCreateInstallId()
            authApi.linkInstall(LinkInstallRequest(installId))
        }
    }

    suspend fun refreshUser(): Result<User> = runCatching {
        val dto = authApi.me()
        val current = tokenStore.currentUser ?: error("No hay sesión activa")
        val refreshed = dto.toDomain(current.id)
        tokenStore.updateUser(refreshed)
        refreshed
    }

    fun logout() = tokenStore.clear()

    private fun UserDto.toDomain(id: String) = User(id, username, role, tier, isOwner, hasCloudStorage, theme)
}
