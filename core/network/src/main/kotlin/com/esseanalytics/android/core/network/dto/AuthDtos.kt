package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import com.esseanalytics.android.core.model.EffectiveCapability
import com.esseanalytics.android.core.model.EffectiveEntitlements

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
data class EffectiveCapabilityDto(
    val enabled: Boolean,
    val limit: Int? = null,
    val sources: List<String> = emptyList(),
) {
    fun toDomain() = EffectiveCapability(enabled, limit, sources)
}

@Serializable
data class EffectiveEntitlementsDto(
    val userId: String,
    val isOwner: Boolean,
    val capabilities: Map<String, EffectiveCapabilityDto>,
) {
    fun toDomain() = EffectiveEntitlements(userId, isOwner, capabilities.mapValues { it.value.toDomain() })
}

@Serializable(with = AuthMeResponseSerializer::class)
data class AuthMeResponse(val user: UserDto, val entitlements: EffectiveEntitlementsDto? = null)

@Serializable
private data class AuthMeResponseEnvelope(
    val user: UserDto,
    val entitlements: EffectiveEntitlementsDto? = null,
)

/** Acepta tanto el envelope vigente `{ user, entitlements }` como el user plano legado. */
private object AuthMeResponseSerializer : KSerializer<AuthMeResponse> {
    override val descriptor: SerialDescriptor = AuthMeResponseEnvelope.serializer().descriptor

    override fun deserialize(decoder: Decoder): AuthMeResponse {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("AuthMeResponse requiere un formato JSON")
        val root = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("La respuesta de /api/auth/me debe ser un objeto")
        val userElement = root["user"] ?: JsonObject(root.filterKeys { it != "entitlements" })
        val user = jsonDecoder.json.decodeFromJsonElement<UserDto>(userElement)
        val entitlements = root["entitlements"]
            ?.takeUnless { it == JsonNull }
            ?.let { jsonDecoder.json.decodeFromJsonElement<EffectiveEntitlementsDto>(it) }
        return AuthMeResponse(user, entitlements)
    }

    override fun serialize(encoder: Encoder, value: AuthMeResponse) {
        encoder.encodeSerializableValue(
            AuthMeResponseEnvelope.serializer(),
            AuthMeResponseEnvelope(value.user, value.entitlements),
        )
    }
}

@Serializable
data class LinkInstallRequest(val installId: String)
