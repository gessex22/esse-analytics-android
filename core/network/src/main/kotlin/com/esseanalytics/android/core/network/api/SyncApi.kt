package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.ConfirmLinkRequest
import com.esseanalytics.android.core.network.dto.CrossMatchCandidatesResponseDto
import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import com.esseanalytics.android.core.network.dto.PlatformRecentPageDto
import com.esseanalytics.android.core.network.dto.ResolveCrossMatchSlotRequest
import com.esseanalytics.android.core.network.dto.SyncReviewResponseDto
import com.esseanalytics.android.core.network.dto.SyncStatsDto
import com.esseanalytics.android.core.network.dto.TriggerSyncResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Cubre lo que necesita el MVP (Calendario básico + Estadísticas + Sync de
// Fase 2). Mismos endpoints que ya consume frontend/src/components/SyncPanel.tsx.
interface SyncApi {
    @GET("api/sync/calendar-config")
    suspend fun getCalendarConfig(): List<CalendarConfigDto>

    @PATCH("api/sync/calendar-config/{platform}")
    suspend fun updateCalendarConfig(@Path("platform") platform: String, @Body body: Map<String, String>)

    @GET("api/sync/group-stats")
    suspend fun getGroupStats(@Query("limit") limit: Int = 5): GroupStatsResponse

    @GET("api/sync/stats")
    suspend fun getSyncStats(): SyncStatsDto

    @GET("api/sync/review")
    suspend fun getReview(@Query("page") page: Int, @Query("limit") limit: Int = 15): SyncReviewResponseDto

    @POST("api/sync/review/{id}/link")
    suspend fun confirmLink(@Path("id") id: String, @Body body: ConfirmLinkRequest)

    @POST("api/sync/review/{id}/orphan")
    suspend fun markOrphan(@Path("id") id: String)

    @POST("api/sync/youtube")
    suspend fun triggerYoutubeSync(): TriggerSyncResponse

    // Página de videos EN VIVO de una plataforma, para el emparejado manual
    // entre redes. Pasar el nextCursor de la respuesta anterior para seguir.
    @GET("api/sync/platform-recent/{platform}")
    suspend fun getPlatformRecent(
        @Path("platform") platform: String,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): PlatformRecentPageDto

    // Archivos locales que ya tienen las 3 badges de plataforma -- punto de
    // partida para completar los links que falten.
    @GET("api/sync/cross-match/candidates")
    suspend fun getCrossMatchCandidates(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20,
    ): CrossMatchCandidatesResponseDto

    // Confirma que un video puntual de una plataforma es ESTE archivo local.
    @POST("api/sync/cross-match/resolve")
    suspend fun resolveCrossMatchSlot(@Body body: ResolveCrossMatchSlotRequest)
}
