package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiUsageReporter @Inject constructor(
    tokenStore: TokenStore,
    settingsStore: SettingsStore,
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reporter = ClientApiUsageReporter(
        captureSession = tokenStore::currentSession,
        serverUrl = { settingsStore.serverUrl.first().ifBlank { "https://api.esse-analytics.com" }.trimEnd('/') },
        scope = scope,
        storageFile = File(context.noBackupFilesDir, "api-usage-outbox.json"),
    )

    init {
        scope.launch {
            combine(tokenStore.authState, settingsStore.serverUrl) { _, _ -> Unit }.collect {
                reporter.flushPending()
            }
        }
        scope.launch {
            while (true) { delay(30_000); reporter.flushPending() }
        }
    }

    fun begin(provider: String): (Boolean) -> Unit = reporter.begin(provider)
}

// El mismo transporte se prueba en JVM contra un Lab aislado. La captura de
// credenciales permanece en TokenStore; no hace falta simular Android/Keystore.
internal class ClientApiUsageReporter(
    private val captureSession: () -> Pair<String, String>?,
    private val serverUrl: suspend () -> String,
    private val sender: OkHttpClient = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    storageFile: File? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val queue by lazy { ApiUsageOutbox(storageFile, now) }
    private val flushLock = Mutex()
    private val retryLock = Any()
    private val retryAfter = mutableMapOf<String, Long>()
    private val rejectedTokens = mutableMapOf<String, String>() // Solo memoria.
    private val hourFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH':00:00.000Z'")
        .withZone(ZoneOffset.UTC)

    fun begin(provider: String): (Boolean) -> Unit {
        val (token, userId) = captureSession() ?: return {}
        val hour = hourFormat.format(Instant.ofEpochMilli(now()))
        val base = scope.async { serverUrl() }
        return { failed -> record(provider, failed, userId, token, hour, base) }
    }

    private fun record(provider: String, failed: Boolean, userId: String, token: String,
                       hour: String, base: Deferred<String>) {
        scope.launch {
            try {
                val row = queue.record(usageOrigin(base.await()), userId, provider, hour, failed)
                if (canSend("${row.origin}|${row.userId}", token)) send(row, token)
            } catch (_: Exception) {
                // Una falla de telemetría no altera la llamada al proveedor.
            }
        }
    }

    private fun send(row: UsageBucket, token: String): Boolean {
        val key = "${row.origin}|${row.userId}"
        try {
            val snapshot = Json.encodeToString(ClientUsageSnapshot("android", row.provider, row.clientSessionId,
                row.hour, row.count, row.failures))
            val request = Request.Builder().url("${row.origin}/api/api-usage/client-snapshot")
                .header("Authorization", "Bearer $token")
                .post(snapshot.toRequestBody("application/json".toMediaType())).build()
            sender.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    queue.acknowledge(row)
                    synchronized(retryLock) { retryAfter.remove(key); rejectedTokens.remove(key) }
                    return true
                }
                synchronized(retryLock) {
                    retryAfter[key] = now() + (response.header("Retry-After")?.toLongOrNull() ?: 30L).coerceIn(30L, 900L) * 1000
                    if (response.code == 401 || response.code == 403) rejectedTokens[key] = token
                }
            }
        } catch (_: Exception) { synchronized(retryLock) { retryAfter[key] = now() + 30_000 } }
        return false
    }

    private fun canSend(key: String, token: String): Boolean = synchronized(retryLock) {
        rejectedTokens[key] != token && (rejectedTokens[key] != null || (retryAfter[key] ?: 0) <= now())
    }

    suspend fun flushPending() = flushLock.withLock {
        try {
            val (token, userId) = captureSession() ?: return@withLock
            val origin = usageOrigin(serverUrl())
            val key = "$origin|$userId"
            if (!canSend(key, token)) return@withLock
            for (row in queue.pending(origin, userId)) {
                // Nunca reenvía pendientes de A con el JWT o servidor de B.
                if (captureSession() != (token to userId) || usageOrigin(serverUrl()) != origin) break
                if (!send(row, token)) break
            }
        } catch (_: Exception) { /* El flush no cambia ni borra la sesión. */ }
    }
}

@Serializable
private data class ClientUsageSnapshot(
    val source: String,
    val provider: String,
    val clientSessionId: String,
    val hour: String,
    val count: Int,
    val failures: Int,
)
