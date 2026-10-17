package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.GroupStatsResponse
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import com.esseanalytics.android.core.network.dto.DashboardSummaryDto
import com.esseanalytics.android.core.network.dto.DashboardFollowersDto
import com.esseanalytics.android.core.network.dto.DashboardFollowerDto
import com.esseanalytics.android.core.network.dto.DashboardWeekDto
import com.esseanalytics.android.core.network.dto.DashboardLatestDto
import com.esseanalytics.android.core.network.dto.DashboardVideoDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class DashboardSummaryResult(val data: DashboardSummaryDto, val compatibilityMode: Boolean = false)

/**
 * Caché corta para datos compartidos por Inicio, Calendario, Videos, Subir y
 * Estadísticas. El modo, plataforma y límite distinguen cada consulta; el Mutex
 * también deduplica dos cargas concurrentes de la misma combinación.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: SyncApi,
) {
    // Session-bound DashboardViewModel owns the last good result. No cross-account cache.
    suspend fun getDashboardSummary(timeZone: String): DashboardSummaryResult {
        try {
            return DashboardSummaryResult(api.getDashboardSummary(timeZone))
        } catch (error: HttpException) {
            // Older centrals have no summary route. Authentication, outages and
            // invalid responses must still fail normally, never become empty data.
            if (error.code() != 404 && error.code() != 405) throw error
        }
        val platforms = listOf("youtube", "instagram", "tiktok")
        val latest = coroutineScope {
            platforms.map { platform ->
                async {
                    val item = api.getGroupStats(limit = 1, platform = platform, mode = "recent").items.firstOrNull()
                    val slot = item?.platforms?.get(platform)
                    DashboardLatestDto(platform, slot?.takeIf { it.platformId.isNotBlank() }?.let {
                        DashboardVideoDto(platform, it.platformId, it.platformUrl, it.title.ifBlank { item.fileName.substringBeforeLast('.') },
                            it.thumbnail, it.views.toLong(), item.fecha_creacion, item.fileId, item.fileName)
                    })
                }
            }.awaitAll()
        }
        val zone = ZoneId.of(timeZone)
        val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
        return DashboardSummaryResult(
            DashboardSummaryDto(
                generatedAt = Instant.now().toString(), timeZone = timeZone,
                followers = DashboardFollowersDto(null, true, platforms.map { DashboardFollowerDto(it, null, null, "unavailable") }),
                week = DashboardWeekDto(start.toString(), null, null, null),
                pending = null, latest = latest,
            ), compatibilityMode = true,
        )
    }

    private val calendarMutex = Mutex()
    private val statsMutexes = mutableMapOf<StatsCacheKey, Mutex>()
    private val historyMutex = Mutex()
    private var calendarCache: Timed<List<CalendarConfigDto>>? = null
    // Cada filtro devuelve un conjunto distinto; un único slot podía mostrar
    // el resultado combinado al cambiar a una plataforma (o viceversa).
    private val statsCache = mutableMapOf<StatsCacheKey, Timed<GroupStatsResponse>>()
    private var historyCache: Timed<List<UploadHistoryItemDto>>? = null

    suspend fun getCalendarConfig(force: Boolean = false): List<CalendarConfigDto> = calendarMutex.withLock {
        val cached = calendarCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getCalendarConfig().also { calendarCache = Timed(it) }
    }

    suspend fun getGroupStats(limit: Int = 5, platform: String? = null, force: Boolean = false, mode: String? = null): GroupStatsResponse {
        val key = StatsCacheKey(mode, platform, limit)
        return statsMutexFor(key).withLock {
            val cached = synchronized(statsCache) { statsCache[key] }
            if (!force && cached != null && !cached.expired()) return@withLock cached.value
            api.getGroupStats(limit, platform, mode).also { response ->
                synchronized(statsCache) { statsCache[key] = Timed(response) }
            }
        }
    }

    private fun statsMutexFor(key: StatsCacheKey): Mutex = synchronized(statsMutexes) {
        statsMutexes.getOrPut(key) { Mutex() }
    }

    private data class StatsCacheKey(val mode: String?, val platform: String?, val limit: Int)

    suspend fun getHistory(limit: Int = 1, force: Boolean = false): List<UploadHistoryItemDto> = historyMutex.withLock {
        val cached = historyCache
        if (!force && cached != null && !cached.expired()) return@withLock cached.value
        api.getHistory(limit = limit).items.also { historyCache = Timed(it) }
    }

    // Estadísticas puntuales de un archivo, también usadas por Calendario.
    suspend fun getFileStats(fileId: String? = null, fileName: String? = null): GroupStatsItemDto =
        api.getFileStats(fileId = fileId, fileName = fileName)

    private data class Timed<T>(val value: T, val createdAt: Long = System.currentTimeMillis()) {
        fun expired(): Boolean = System.currentTimeMillis() - createdAt > 30_000L
    }
}
