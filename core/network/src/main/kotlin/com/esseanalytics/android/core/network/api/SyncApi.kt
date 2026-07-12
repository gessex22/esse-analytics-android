package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

// Cubre lo que necesita el MVP (Calendario básico + Estadísticas de Fase 2).
// El resto de /api/sync/* (review, cross-match, youtube trigger/list) se
// agrega cuando se construya esa pantalla — ver el plan, sección "Fases".
interface SyncApi {
    @GET("api/sync/calendar-config")
    suspend fun getCalendarConfig(): List<CalendarConfigDto>

    @PATCH("api/sync/calendar-config/{platform}")
    suspend fun updateCalendarConfig(@Path("platform") platform: String, @retrofit2.http.Body body: Map<String, String>)

    @GET("api/sync/group-stats")
    suspend fun getGroupStats(@Query("limit") limit: Int = 5): GroupStatsResponse
}
