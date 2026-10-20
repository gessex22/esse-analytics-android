package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.NotificationsResponseDto
import com.esseanalytics.android.core.network.dto.NotificationPreferencesDto
import com.esseanalytics.android.core.network.dto.NotificationPreferencesPatchDto
import com.esseanalytics.android.core.network.dto.NotificationDeviceRegistrationDto
import com.esseanalytics.android.core.network.dto.NotificationReadResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

interface NotificationsApi {
    @GET("api/notifications") suspend fun list(): NotificationsResponseDto
    @POST("api/notifications/{id}/read") suspend fun markRead(@Path("id") id: String): Response<NotificationReadResponseDto>
    @POST("api/notifications/read-all") suspend fun markAllRead(@Body body: Map<String, String?> = emptyMap()): Response<NotificationReadResponseDto>
    @GET("api/notifications/preferences") suspend fun preferences(): NotificationPreferencesDto
    @PATCH("api/notifications/preferences") suspend fun updatePreferences(@Body body: NotificationPreferencesPatchDto): NotificationPreferencesDto
    @PUT("api/notifications/devices") suspend fun registerDevice(@Body body: NotificationDeviceRegistrationDto): NotificationReadResponseDto
    @DELETE("api/notifications/devices/{installationId}") suspend fun removeDevice(@Path("installationId") installationId: String): NotificationReadResponseDto
}
