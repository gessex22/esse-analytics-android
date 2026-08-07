package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
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
    private val statsMutexes = mutableMapOf<String?, Mutex>()
    private val historyMutex = Mutex()
    private var calendarCache: Timed<List<CalendarConfigDto>>? = null
    // Cada filtro devuelve un conjunto distinto; un único slot podía mostrar
    // el resultado combinado al cambiar a una plataforma (o viceversa).
    private val statsCache = mutableMapOf<String?, Timed<GroupStatsResponse>>()
    private var historyCache: Timed<List<UploadHistoryItemDto>>? = null

    suspend fun getCalendarConfig(force: Boolean = false): List<CalendarConfigDto> = calendarMutex.withLock {
        val cached = calendarCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getCalendarConfig().also { calendarCache = Timed(it) }
    }

    suspend fun getGroupStats(limit: Int = 5, platform: String? = null, force: Boolean = false): GroupStatsResponse = statsMutexFor(platform).withLock {
        val cached = synchronized(statsCache) { statsCache[platform] }
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getGroupStats(limit, platform).also { response ->
            synchronized(statsCache) { statsCache[platform] = Timed(response) }
        }
    }

    private fun statsMutexFor(platform: String?): Mutex = synchronized(statsMutexes) {
        statsMutexes.getOrPut(platform) { Mutex() }
    }

    suspend fun getHistory(limit: Int = 1, force: Boolean = false): List<UploadHistoryItemDto> = historyMutex.withLock {
        val cached = historyCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getHistory(limit = limit).items.also { historyCache = Timed(it) }
    }

    // Fallback puntual del Dashboard cuando el último publicado no está en el
    // top-N "completo" de getGroupStats -- sin cache, se pide una sola vez por
    // cada video que necesite resolverse así.
    suspend fun getFileStats(fileId: String? = null, fileName: String? = null): GroupStatsItemDto =
        api.getFileStats(fileId = fileId, fileName = fileName)

    private data class Timed<T>(val value: T, val createdAt: Long = System.currentTimeMillis()) {
        fun expired(): Boolean = System.currentTimeMillis() - createdAt > 30_000L
    }
}
