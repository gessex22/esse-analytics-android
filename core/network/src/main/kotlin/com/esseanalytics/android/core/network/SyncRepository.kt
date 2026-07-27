package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caché corta para datos compartidos por Inicio, Calendario, Videos, Subir y
 * Estadísticas. Evita que cambiar de pestaña vuelva a pedir inmediatamente el
 * mismo calendar-config; el Mutex también deduplica dos cargas concurrentes.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: SyncApi,
) {
    private val calendarMutex = Mutex()
    private val statsMutex = Mutex()
    private val historyMutex = Mutex()
    private var calendarCache: Timed<List<CalendarConfigDto>>? = null
    private var statsCache: Timed<GroupStatsResponse>? = null
    private var historyCache: Timed<List<UploadHistoryItemDto>>? = null

    suspend fun getCalendarConfig(force: Boolean = false): List<CalendarConfigDto> = calendarMutex.withLock {
        val cached = calendarCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getCalendarConfig().also { calendarCache = Timed(it) }
    }

    suspend fun getGroupStats(limit: Int = 5, force: Boolean = false): GroupStatsResponse = statsMutex.withLock {
        val cached = statsCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getGroupStats(limit).also { statsCache = Timed(it) }
    }

    suspend fun getHistory(limit: Int = 1, force: Boolean = false): List<UploadHistoryItemDto> = historyMutex.withLock {
        val cached = historyCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getHistory(limit = limit).items.also { historyCache = Timed(it) }
    }

    private data class Timed<T>(val value: T, val createdAt: Long = System.currentTimeMillis()) {
        fun expired(): Boolean = System.currentTimeMillis() - createdAt > 30_000L
    }
}
