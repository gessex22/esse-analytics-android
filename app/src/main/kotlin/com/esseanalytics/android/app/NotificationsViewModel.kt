package com.esseanalytics.android.app

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.network.api.NotificationsApi
import com.esseanalytics.android.core.network.dto.NotificationDeviceRegistrationDto
import com.esseanalytics.android.core.network.dto.NotificationItemDto
import com.esseanalytics.android.core.network.dto.NotificationPreferencesPatchDto
import com.esseanalytics.android.core.network.dto.NotificationQuietHoursDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class NotificationsState(
    val items: List<NotificationItemDto> = emptyList(), val unread: Int = 0, val loading: Boolean = true,
    val error: String? = null, val dailyEnabled: Boolean = true, val externalCategories: List<String> = emptyList(),
    val quietHours: NotificationQuietHoursDto? = null,
    val pushConfigured: Boolean = false, val pushEnabled: Boolean = false, val pushMessage: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: NotificationsApi,
    private val settings: SettingsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutable = MutableStateFlow(NotificationsState())
    val state = mutable.asStateFlow()

    fun refresh() { viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, error = null)
        try {
            var pref = api.preferences()
            if (pref.timeZone == null) pref = api.updatePreferences(NotificationPreferencesPatchDto(timeZone = ZoneId.systemDefault().id))
            val result = api.list()
            val firebaseReady = FirebaseApp.getApps(context).isNotEmpty()
            mutable.value = mutable.value.copy(
                items = result.notifications, unread = result.unreadCount, loading = false,
                dailyEnabled = pref.dailyCalendarEnabled, externalCategories = pref.externalCategories,
                quietHours = pref.quietHours,
                pushConfigured = pref.externalDelivery?.fcm == true && firebaseReady,
                pushEnabled = settings.notificationPushEnabled.first(),
                pushMessage = if (pref.externalDelivery?.fcm != true) "FCM no está configurado en el servidor."
                    else if (!firebaseReady) "Falta configuración Firebase para esta instalación."
                    else null,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutable.value = mutable.value.copy(loading = false, error = "No se pudieron cargar las notificaciones.")
        }
    } }

    fun setDailyEnabled(enabled: Boolean) { viewModelScope.launch {
        runCatching { api.updatePreferences(NotificationPreferencesPatchDto(dailyCalendarEnabled = enabled)) }
            .onSuccess { mutable.value = mutable.value.copy(dailyEnabled = it.dailyCalendarEnabled) }
            .onFailure { mutable.value = mutable.value.copy(error = "No se pudo guardar la preferencia.") }
    } }

    fun setExternalCategory(category: String, enabled: Boolean) { viewModelScope.launch {
        val current = mutable.value.externalCategories
        val next = if (enabled) (current + category).distinct() else current.filterNot { it == category }
        runCatching { api.updatePreferences(NotificationPreferencesPatchDto(externalCategories = next)) }
            .onSuccess { mutable.value = mutable.value.copy(externalCategories = it.externalCategories) }
            .onFailure { mutable.value = mutable.value.copy(error = "No se pudo guardar la categoría.") }
    } }

    fun saveQuietHours(enabled: Boolean, start: String, end: String) { viewModelScope.launch {
        val value = if (enabled) JsonObject(mapOf("start" to JsonPrimitive(start), "end" to JsonPrimitive(end))) else JsonNull
        runCatching { api.updatePreferences(NotificationPreferencesPatchDto(quietHours = value)) }
            .onSuccess { mutable.value = mutable.value.copy(quietHours = it.quietHours) }
            .onFailure { mutable.value = mutable.value.copy(error = "Usa horas válidas (HH:mm) y confirma una zona horaria.") }
    } }

    fun enablePush() { viewModelScope.launch {
        mutable.value = mutable.value.copy(error = null, pushMessage = null)
        try {
            val pref = api.preferences()
            if (pref.externalDelivery?.fcm != true) error("FCM no está configurado en el servidor.")
            if (FirebaseApp.getApps(context).isEmpty()) error("Falta configuración Firebase para esta instalación.")
            if (pref.externalCategories.isEmpty()) error("Elige al menos una categoría para avisos externos.")
            val token = FirebaseMessaging.getInstance().token.awaitValue()
            val installId = settings.getOrCreateInstallId()
            api.registerDevice(NotificationDeviceRegistrationDto(
                installationId = installId,
                environment = if (BuildConfig.DEBUG) "development" else "production",
                token = token,
            ))
            settings.setNotificationPushEnabled(true)
            mutable.value = mutable.value.copy(pushEnabled = true, pushMessage = "Avisos del sistema activados en este dispositivo.")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutable.value = mutable.value.copy(error = error.message ?: "No se pudo activar FCM.")
        }
    } }

    fun disablePush() { viewModelScope.launch {
        val installId = settings.getOrCreateInstallId()
        try {
            api.removeDevice(installId)
            settings.setNotificationPushEnabled(false)
            mutable.value = mutable.value.copy(pushEnabled = false, pushMessage = "Avisos del sistema desactivados.", error = null)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutable.value = mutable.value.copy(error = "No se pudo desregistrar este dispositivo. Inténtalo de nuevo.")
        }
    } }

    fun pushPermissionDenied() {
        mutable.value = mutable.value.copy(pushMessage = "El permiso de notificaciones está denegado en Android.")
    }

    fun markRead(id: String) { viewModelScope.launch {
        val item = mutable.value.items.firstOrNull { it.id == id && it.readAt == null } ?: return@launch
        runCatching { api.markRead(id) }.onSuccess { result ->
            if (result.isSuccessful) {
                mutable.value = mutable.value.copy(
                    items = mutable.value.items.map { if (it.id == id) it.copy(readAt = it.readAt ?: "leído") else it },
                    unread = (mutable.value.unread - 1).coerceAtLeast(0),
                )
                dismissSystemNotification(item.id)
            }
        }
    } }
    fun markAllRead() { viewModelScope.launch {
        val current = mutable.value
        val cutoff = current.items.maxOfOrNull { it.createdAt } ?: return@launch
        val newlyReadIds = current.items.filter { it.readAt == null && it.createdAt <= cutoff }.map { it.id }
        runCatching { api.markAllRead(mapOf("before" to cutoff)) }.onSuccess { result ->
            if (result.isSuccessful) {
                mutable.value = mutable.value.copy(
                    items = mutable.value.items.map { if (it.createdAt <= cutoff) it.copy(readAt = it.readAt ?: "leído") else it },
                    unread = (mutable.value.unread - (result.body()?.updated ?: 0)).coerceAtLeast(0),
                )
                (result.body()?.notificationIds ?: newlyReadIds).forEach(::dismissSystemNotification)
            }
        }
    } }

    private fun dismissSystemNotification(serverId: String) {
        // Keep this identifier in sync with EssenFirebaseMessagingService.onMessageReceived.
        NotificationManagerCompat.from(context).cancel(serverId.hashCode().and(0x7fffffff))
    }
}

private suspend fun com.google.android.gms.tasks.Task<String>.awaitValue(): String = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel(CancellationException("Firebase token request canceled")) }
}
