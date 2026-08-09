package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.HealthApi
import javax.inject.Inject
import javax.inject.Singleton

// Único punto de verdad para "¿el servidor configurado es el Laboratorio?" --
// SIEMPRE por chequeo en vivo de GET /api/health (environment == "lab"), nunca
// por heurística de URL/puerto. Mirror exacto de LabModeStatus.swift (iOS) y
// useBackendType.ts (desktop). Retrofit ya apunta al servidor configurado en
// Ajustes (ver NetworkModule::provideRetrofit) -- health() pega ahí mismo, sin
// necesitar la URL aparte.
@Singleton
class LabModeStatus @Inject constructor(
    private val healthApi: HealthApi,
) {
    suspend fun isActive(): Boolean =
        runCatching { healthApi.health().environment == "lab" }.getOrDefault(false)
}
