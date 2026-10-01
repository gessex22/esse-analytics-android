package com.esseanalytics.android.app

/** Evita repetir GET /api/auth/me al volver varias veces rápidamente a primer plano. */
internal class EntitlementsRefreshGate(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clockMs: () -> Long,
) {
    private var lastUserId: String? = null
    private var lastAttemptAtMs: Long? = null

    @Synchronized
    fun shouldRefresh(userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false

        val now = clockMs()
        val elapsed = lastAttemptAtMs?.let { now - it }
        if (userId == lastUserId && elapsed != null && elapsed < minIntervalMs) return false

        lastUserId = userId
        lastAttemptAtMs = now
        return true
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
