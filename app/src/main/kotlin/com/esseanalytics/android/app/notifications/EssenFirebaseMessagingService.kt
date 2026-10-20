package com.esseanalytics.android.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.esseanalytics.android.app.MainActivity
import com.esseanalytics.android.app.BuildConfig
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.NotificationsApi
import com.esseanalytics.android.core.network.dto.NotificationDeviceRegistrationDto
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class EssenFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notificationsApi: NotificationsApi
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var tokenStore: TokenStore

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (!settingsStore.notificationPushEnabled.first()) return@launch
            if (tokenStore.currentSession() == null) return@launch
            val installationId = settingsStore.getOrCreateInstallId()
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@EssenFirebaseMessagingService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!allowed) {
                runCatching { notificationsApi.removeDevice(installationId) }
                settingsStore.setNotificationPushEnabled(false)
                return@launch
            }
            runCatching {
                notificationsApi.registerDevice(NotificationDeviceRegistrationDto(
                    installationId = installationId,
                    environment = if (BuildConfig.DEBUG) "development" else "production",
                    token = token,
                    permission = "granted",
                ))
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: return
        val body = message.data["body"] ?: message.notification?.body ?: return
        val serverId = message.data["notificationId"] ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (!settingsStore.notificationPushEnabled.first()) return@launch
            val currentUserId = tokenStore.currentSession()?.second ?: return@launch
            if (message.data["recipientId"] != currentUserId) return@launch
            NotificationChannels.ensure(this@EssenFirebaseMessagingService)
            val target = runCatching { JSONObject(message.data["target"] ?: "{}").optString("route") }.getOrNull()
                ?.takeIf { it in setOf("calendar", "history", "connections", "notifications") }
            val notificationId = serverId.hashCode().and(0x7fffffff)
            val open = Intent(this@EssenFirebaseMessagingService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_NOTIFICATION_TARGET, target ?: "notifications")
            }
            val pending = PendingIntent.getActivity(this@EssenFirebaseMessagingService, notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this@EssenFirebaseMessagingService, NotificationChannels.CHANNEL_ID)
                .setSmallIcon(com.esseanalytics.android.app.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(this@EssenFirebaseMessagingService).notify(notificationId, notification)
        }
    }
}

object NotificationChannels {
    const val CHANNEL_ID = "esse_updates"
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Avisos de EsseAnalytics", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos que elegiste recibir de la bandeja de EsseAnalytics"
        })
    }
}
