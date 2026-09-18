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
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

// Contrato mínimo que necesita AuthAuthenticator (core:network) para decidir
// si un 401 tardío corresponde a la sesión central vigente. Separado de la
// clase concreta para poder testear esa decisión con un fake en memoria, sin
// arrastrar EncryptedSharedPreferences/Context a un test JVM plano.
interface TokenSessionGuard {
    val token: String?

    // Atómico: solo limpia token+usuario (y publica LoggedOut) si la
    // credencial guardada en este momento sigue siendo expectedToken. Evita
    // la ventana check-then-clear entre leer "¿sigue vigente?" y borrar.
    fun clearIfCurrent(expectedToken: String): Boolean
}

// Guarda el JWT (7 días de vida, SIN refresh token — ver backend/src/controllers/
// auth.controller.ts) y el user object, cifrados con Keystore vía
// EncryptedSharedPreferences. No hay endpoint de refresh: cuando expira, hay
// que re-loguear — ver AuthAuthenticator en core:network para el manejo de 401.
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenSessionGuard {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "essenalytics_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Espejo en memoria del token guardado, mantenido en sync con save()/clear()
    // vía AtomicReference para que clearIfCurrent() pueda comparar-y-limpiar de
    // forma atómica (sin volver a tocar disco para el check).
    private val tokenGuard = TokenGuard(prefs.getString(KEY_TOKEN, null))

    // Serializa save()/clear()/clearIfCurrent() entre si: el compareAndSet de
    // tokenGuard ya decide atómicamente SI hay que escribir, pero sin este
    // lock un save() concurrente podria colarse entre ese check y el
    // prefs.edit().clear() de abajo, y clearIfCurrent() terminaria borrando
    // una sesión nueva que ya habia ganado la carrera en el guard.
    private val writeLock = Any()

    private val _authState = MutableStateFlow(readAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val token: String? get() = tokenGuard.get()
    val currentUser: User? get() = readUser()

    fun save(token: String, user: User) = synchronized(writeLock) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, json.encodeToString(UserDto.fromDomain(user)))
            .apply()
        tokenGuard.set(token)
        _authState.value = AuthState.LoggedIn(user)
    }

    fun updateUser(user: User) {
        prefs.edit().putString(KEY_USER, json.encodeToString(UserDto.fromDomain(user))).apply()
        _authState.value = AuthState.LoggedIn(user)
    }

    fun clear() = synchronized(writeLock) {
        prefs.edit().clear().apply()
        tokenGuard.set(null)
        _authState.value = AuthState.LoggedOut
    }

    override fun clearIfCurrent(expectedToken: String): Boolean = synchronized(writeLock) {
        if (!tokenGuard.clearIfCurrent(expectedToken)) return@synchronized false
        prefs.edit().clear().apply()
        _authState.value = AuthState.LoggedOut
        true
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

// Wrapper puro (sin Android/Context) sobre AtomicReference: aislado acá para
// poder testear la semántica de compare-and-clear (incl. carreras) con JUnit
// plano, sin necesitar EncryptedSharedPreferences.
internal class TokenGuard(initial: String?) {
    private val current = AtomicReference(initial)

    fun get(): String? = current.get()

    fun set(token: String?) {
        current.set(token)
    }

    // true solo si `current` era exactamente expectedToken (y ya quedó en
    // null); false si no matcheaba — deja el valor guardado intacto.
    fun clearIfCurrent(expectedToken: String): Boolean = current.compareAndSet(expectedToken, null)
}
