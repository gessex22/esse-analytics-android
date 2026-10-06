package com.esseanalytics.android.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal fun usageOrigin(base: String): String {
    val uri = URI(base)
    require(uri.scheme in listOf("http", "https") && uri.host != null && uri.userInfo == null)
    return URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, null, null, null).toString()
}

@Serializable
internal data class UsageBucket(
    val origin: String, val userId: String, val provider: String, val hour: String,
    val clientSessionId: String = UUID.randomUUID().toString(),
    var count: Int = 0, var failures: Int = 0,
    var acknowledgedCount: Int = 0, var acknowledgedFailures: Int = 0,
)

@Serializable
private data class UsageState(val version: Int = 1, val rows: List<UsageBucket>)

/** Solo acumulados y alcance; nunca JWT, headers ni URLs de proveedores. */
internal class ApiUsageOutbox(private val file: File? = null, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private var writable = true
    private val rows = runCatching {
        if (file?.exists() == true) {
            val state = Json.decodeFromString<UsageState>(file.readText())
            require(state.version == 1)
            state.rows.filter { valid(it) }.toMutableList()
        } else mutableListOf()
    }.getOrElse { writable = false; mutableListOf() }

    private fun valid(row: UsageBucket): Boolean = runCatching {
        row.origin == usageOrigin(row.origin) && row.userId.isNotBlank()
            && row.provider in listOf("youtube", "instagram", "tiktok")
            && row.hour.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:00:00\\.000Z"))
            && java.time.Instant.parse(row.hour).toEpochMilli() >= now() - 90L * 86_400_000
            && UUID.fromString(row.clientSessionId).toString() == row.clientSessionId
            && row.count in 1..1_000_000 && row.failures in 0..row.count
            && row.acknowledgedCount in 0..row.count && row.acknowledgedFailures in 0..row.failures
    }.getOrDefault(false)

    private fun prune() { rows.removeAll { java.time.Instant.parse(it.hour).toEpochMilli() < now() - 90L * 86_400_000 } }

    private fun save() {
        val destination = file ?: return
        if (!writable) return
        runCatching {
            destination.parentFile?.mkdirs()
            val temporary = File(destination.path + ".tmp")
            FileOutputStream(temporary).use { stream ->
                stream.write(Json.encodeToString(UsageState(rows = rows)).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } // Si el disco falla, la publicación conserva su resultado.
    }

    fun record(origin: String, userId: String, provider: String, hour: String, failed: Boolean): UsageBucket = synchronized(lock) {
        prune()
        val canonical = usageOrigin(origin)
        val row = rows.find { it.origin == canonical && it.userId == userId && it.provider == provider && it.hour == hour }
            ?: UsageBucket(canonical, userId, provider, hour).also { rows.add(it) }
        if (row.count < 1_000_000) {
            row.count++
            if (failed) row.failures++
        }
        save()
        row.copy()
    }

    fun pending(origin: String, userId: String): List<UsageBucket> = synchronized(lock) {
        prune()
        rows.filter { it.origin == usageOrigin(origin) && it.userId == userId
            && (it.count > it.acknowledgedCount || it.failures > it.acknowledgedFailures) }.take(128).map { it.copy() }
    }

    fun acknowledge(sent: UsageBucket) = synchronized(lock) {
        rows.find { it.clientSessionId == sent.clientSessionId }?.let {
            it.acknowledgedCount = maxOf(it.acknowledgedCount, sent.count)
            it.acknowledgedFailures = maxOf(it.acknowledgedFailures, sent.failures)
        }
        save()
    }
}
