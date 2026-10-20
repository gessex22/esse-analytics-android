package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class NotificationItemDto(
    val id: String, val category: String, val title: String, val body: String,
    val destination: String? = null, val createdAt: String, val readAt: String? = null,
)
@Serializable data class NotificationsResponseDto(
    val notifications: List<NotificationItemDto>, val unreadCount: Int, val nextBefore: String? = null,
)
@Serializable data class NotificationReadResponseDto(val ok: Boolean, val updated: Int? = null)
@Serializable data class NotificationPushCapabilitiesDto(val fcm: Boolean = false, val apns: Boolean = false, val webpush: Boolean = false)
@Serializable data class NotificationPreferencesDto(
    val timeZone: String? = null, val dailyCalendarEnabled: Boolean = true, val dailyTime: String? = null,
    val externalCategories: List<String> = emptyList(), val quietHours: NotificationQuietHoursDto? = null,
    val externalDelivery: NotificationPushCapabilitiesDto? = null,
)
@Serializable data class NotificationQuietHoursDto(val start: String, val end: String)
@Serializable data class NotificationPreferencesPatchDto(
    val timeZone: String? = null, val dailyCalendarEnabled: Boolean? = null, val externalCategories: List<String>? = null,
    val quietHours: JsonElement? = null,
)
@Serializable data class NotificationDeviceRegistrationDto(
    val installationId: String, val platform: String = "android", val environment: String,
    val provider: String = "fcm", val token: String, val permission: String = "granted",
)
