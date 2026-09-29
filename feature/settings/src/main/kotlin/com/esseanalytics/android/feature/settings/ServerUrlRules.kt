package com.esseanalytics.android.feature.settings

import java.net.URI

sealed interface ServerUrlValidation {
    data class Valid(val storedValue: String, val healthCheckBase: String) : ServerUrlValidation
    data class Invalid(val reason: String) : ServerUrlValidation
}

// Reglas puras (JVM, sin Android) de normalización/validación del campo de
// servidor — testeables sin emulador (ServerUrlRulesTest). Espejo del criterio
// de SettingsStore.setServerUrl (vacío = central) y del armado de rutas de
// NetworkModule (base sin barra final + "/api/..."): storedValue es lo que se
// persiste (vacío para Central), healthCheckBase es la base contra la que se
// prueba GET /api/health antes de guardar.
object ServerUrlRules {
    const val CENTRAL_URL = "https://api.esse-analytics.com/"

    fun validate(rawInput: String): ServerUrlValidation {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return ServerUrlValidation.Valid(
                storedValue = "",
                healthCheckBase = CENTRAL_URL.trimEnd('/'),
            )
        }
        val uri = runCatching { URI(input) }.getOrNull()
            ?: return ServerUrlValidation.Invalid("Ingresá una URL válida, por ejemplo http://192.168.1.50:4000")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ServerUrlValidation.Invalid("La URL debe empezar con http:// o https://")
        }
        if (uri.host.isNullOrBlank()) {
            return ServerUrlValidation.Invalid("La URL debe incluir un host, por ejemplo http://192.168.1.50:4000")
        }
        val normalized = input.trimEnd('/')
        return ServerUrlValidation.Valid(storedValue = normalized, healthCheckBase = normalized)
    }
}
