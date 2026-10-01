package com.esseanalytics.android.core.model

// Mirror del user object que devuelve POST /api/auth/login (backend/).
data class User(
    val id: String,
    val username: String,
    val role: String,       // "todopoderoso" | "editor" | ...
    val tier: String,       // "free" | "premium"
    val isOwner: Boolean,
    // Plan APARTE de tier==='premium' -- aloja bytes de video reales en la
    // central (Biblioteca remota general, ver requireCloudStorage en backend/).
    val hasCloudStorage: Boolean = false,
    val theme: String? = null,
    val entitlements: EffectiveEntitlements? = null,
) {
    val isPremium: Boolean get() = entitlements?.let {
        it.capabilities["catalog.backup"]?.enabled == true
    } ?: (isOwner || tier == "premium")
    val canUseCloudStorage: Boolean get() = entitlements?.let {
        it.capabilities["library.cloud"]?.enabled == true
    } ?: (isOwner || (tier == "premium" && hasCloudStorage))
}

data class EffectiveCapability(
    val enabled: Boolean,
    val limit: Int? = null,
    val sources: List<String> = emptyList(),
)

data class EffectiveEntitlements(
    val userId: String,
    val isOwner: Boolean,
    val capabilities: Map<String, EffectiveCapability>,
)

// Simple: las 3 plataformas avanzan juntas (auto-descarta las otras al publicar
// una). Avanzado: cada plataforma se controla por separado. Ver
// resolveOthersAsDiscarded en el repo de archivos — este flag decide si esa
// lógica corre o no. Setting a nivel app (DataStore), no por archivo.
enum class WorkflowMode { SIMPLE, AVANZADO }
