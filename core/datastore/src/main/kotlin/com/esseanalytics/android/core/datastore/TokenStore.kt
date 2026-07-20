package com.esseanalytics.android.core.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.esseanalytics.android.core.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

// Guarda el JWT (7 días de vida, SIN refresh token — ver backend/src/controllers/
// auth.controller.ts) y el user object, cifrados con Keystore vía
// EncryptedSharedPreferences. No hay endpoint de refresh: cuando expira, hay
// que re-loguear — ver AuthAuthenticator en core:network para el manejo de 401.
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "essenalytics_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _authState = MutableStateFlow(readAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val currentUser: User? get() = readUser()

    fun save(token: String, user: User) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, json.encodeToString(UserDto.fromDomain(user)))
            .apply()
        _authState.value = AuthState.LoggedIn(user)
    }

    fun updateUser(user: User) {
        prefs.edit().putString(KEY_USER, json.encodeToString(UserDto.fromDomain(user))).apply()
        _authState.value = AuthState.LoggedIn(user)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.LoggedOut
    }

    private fun readUser(): User? {
        val raw = prefs.getString(KEY_USER, null) ?: return null
        return runCatching { json.decodeFromString<UserDto>(raw).toDomain() }.getOrNull()
    }

    private fun readAuthState(): AuthState {
        val user = readUser()
        return if (token != null && user != null) AuthState.LoggedIn(user) else AuthState.LoggedOut
    }

    companion object {
        private const val KEY_TOKEN = "jwt"
        private const val KEY_USER = "user"
    }
}

sealed interface AuthState {
    data class LoggedIn(val user: User) : AuthState
    data object LoggedOut : AuthState
}

@kotlinx.serialization.Serializable
private data class UserDto(
    val id: String,
    val username: String,
    val role: String,
    val tier: String,
    val isOwner: Boolean,
    val hasCloudStorage: Boolean = false,
    val theme: String? = null,
) {
    fun toDomain() = User(id, username, role, tier, isOwner, hasCloudStorage, theme)

    companion object {
        fun fromDomain(u: User) = UserDto(u.id, u.username, u.role, u.tier, u.isOwner, u.hasCloudStorage, u.theme)
    }
}
