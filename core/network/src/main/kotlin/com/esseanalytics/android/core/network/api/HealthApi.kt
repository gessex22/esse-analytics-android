package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.HealthResponseDto
import retrofit2.http.GET

interface HealthApi {
    @GET("api/health")
    suspend fun health(): HealthResponseDto
}
