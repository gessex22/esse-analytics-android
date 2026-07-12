package com.esseanalytics.android.core.model

// Mirror del user object que devuelve POST /api/auth/login (backend/).
data class User(
    val id: String,
    val username: String,
    val role: String,       // "todopoderoso" | "editor" | ...
    val tier: String,       // "free" | "premium"
    val isOwner: Boolean,
    val theme: String? = null,
) {
    val isPremium: Boolean get() = isOwner || tier == "premium"
}

// Simple: las 3 plataformas avanzan juntas (auto-descarta las otras al publicar
// una). Avanzado: cada plataforma se controla por separado. Ver
// resolveOthersAsDiscarded en el repo de archivos — este flag decide si esa
// lógica corre o no. Setting a nivel app (DataStore), no por archivo.
enum class WorkflowMode { SIMPLE, AVANZADO }
