package com.esseanalytics.android.core.network

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

// La central firma el JWT con {id, username, role, tier, isOwner, iat, exp} —
// login/register solo devuelven username/role/tier/isOwner en el `user`
// object (SIN id), así que el id de usuario se lee del propio token, no de la
// respuesta. No hay endpoint de refresh (7 días de vida) — expiresAt() se usa
// para deslogear proactivamente ANTES de que una subida en curso reciba un 401
// a mitad de camino.
object JwtUtils {
    private val json = Json { ignoreUnknownKeys = true }

    fun decodeUserId(token: String): String? = payloadOf(token)?.get("id")?.jsonPrimitive?.content

    fun expiresAtEpochSeconds(token: String): Long? = payloadOf(token)?.get("exp")?.jsonPrimitive?.content?.toLongOrNull()

    fun isExpiredOrExpiringSoon(token: String, withinSeconds: Long = 5 * 60): Boolean {
        val exp = expiresAtEpochSeconds(token) ?: return true
        val nowSeconds = System.currentTimeMillis() / 1000
        return exp - nowSeconds <= withinSeconds
    }

    private fun payloadOf(token: String): kotlinx.serialization.json.JsonObject? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
        }.getOrNull()
    }
}
