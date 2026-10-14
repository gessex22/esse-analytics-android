package com.esseanalytics.android.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.NextVideoDto
import com.esseanalytics.android.core.network.dto.SkipNextRequest
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.Retrofit
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import java.util.UUID

data class PublishedThumbnailKey(
    val eventId: String,
    val platform: String,
    val platformId: String,
    val fileName: String?,
)

data class PublishedThumbnailSources(
    val localFilePath: String? = null,
    val remoteLibraryUrl: String? = null,
    val platformThumbnailUrl: String? = null,
    val cacheNamespace: String,
)

internal fun publishedThumbnailKey(event: UploadHistoryItemDto) = PublishedThumbnailKey(
    eventId = event.id,
    platform = event.platform,
    platformId = event.platformId,
    fileName = event.fileName,
)

private fun UploadHistoryItemDto.thumbnailPublishedLocalDate(): LocalDate? =
    runCatching { Instant.parse(publishedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

private data class ThumbnailSession(val token: String, val userId: String) {
    fun asPair(): Pair<String, String> = token to userId
}

private fun Pair<String, String>.toThumbnailSession() = ThumbnailSession(first, second)

private data class FileStatsCacheKey(val session: ThumbnailSession, val fileName: String)
private data class ThumbnailResolutionKey(val session: ThumbnailSession, val event: PublishedThumbnailKey)

data class CalendarSlot(
    val platform: String,
    val lastPublishedTitle: String,
    val lastPublishedDate: String,
    val intervalDays: Int,
    // El DTO completo (no solo fileName) -- el descarte necesita fileId, y la
    // tarjeta necesita duration/thumbnails, todo lo que ya trae la central.
    val nextVideo: NextVideoDto?,
    // lastPublishedDate + intervalDays -- mismo cálculo que calcNextDate() en
    // frontend/src/data/mockPublishingData.ts y CalendarDateSupport.nextDate
    // (iOS). null si todavía no hay una fecha base (plataforma sin nada
    // publicado todavía, la central manda "").
    val nextDate: LocalDate?,
    // El "próximo" puede vivir en ESTE dispositivo (se grabó/importó acá) o
    // solo en el de otra persona/PC -- local primero (más barato, ya en
    // disco), Biblioteca remota como respaldo.
    val localThumbnailPath: String? = null,
    val remoteThumbnailUrl: String? = null,
    // Mismo criterio para el último publicado -- a diferencia de nextVideo,
    // la central no manda remoteLibraryVideoId para éste, así que si no vive
    // en este dispositivo no hay miniatura posible todavía.
    val lastPublishedLocalThumbnailPath: String? = null,
)

data class CalendarUiData(
    val slots: List<CalendarSlot>,
    // Rediseño 2026-09-01 (paridad con iOS): antes Calendario solo sabía la
    // PRÓXIMA fecha por plataforma (slots) -- el strip semanal necesita
    // además el historial real para poder marcar/mostrar lo YA publicado en
    // su día, no solo lo que falta (ver CalendarDateSupport/CalendarWeekFocus
    // en CalendarView.swift, mismo dato).
    val history: List<UploadHistoryItemDto>,
)

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): isRefreshing/
// refreshError dentro de Success -- mismo criterio que Dashboard/Stats.
sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(
        val data: CalendarUiData,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

// GET /api/sync/calendar-config alimenta el Calendario real de desktop
// (PublishingQueue.tsx) -- la cadencia por plataforma (último publicado,
// cada cuántos días, qué sigue) vive en la central, no en Room local.
// Rediseño 2026-09-01 (paridad visual con iOS, CalendarView.swift): agenda
// semanal Hoy/Mañana + strip de semana en foco + descartar el próximo de una
// plataforma puntual -- antes Android era de solo lectura (Fase 1 del plan
// original, sin discardNext ni historial).
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val syncApi: SyncApi,
    private val fileRepository: FileRepository,
    private val tokenStore: TokenStore,
    private val refreshTracker: RefreshActivityTracker,
    @CentralRetrofit private val retrofit: Retrofit,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val thumbnailLock = Any()
    private val thumbnailSemaphore = Semaphore(2)
    private var thumbnailSession = tokenStore.currentSession()?.toThumbnailSession()
    private var calendarDataSession: ThumbnailSession? = null
    private var thumbnailCacheNamespace: String = UUID.randomUUID().toString()
    private var thumbnailGeneration = 0L
    private var selectedPublishedDay: LocalDate? = null
    private var visiblePublishedEvents: List<UploadHistoryItemDto> = emptyList()
    private val fileStatsCache = mutableMapOf<FileStatsCacheKey, GroupStatsItemDto?>()
    private val fileStatsInFlight = mutableMapOf<FileStatsCacheKey, Deferred<GroupStatsItemDto>>()
    private val thumbnailResolutions = mutableMapOf<ThumbnailResolutionKey, PublishedThumbnailSources?>()
    private val thumbnailJobs = mutableMapOf<ThumbnailResolutionKey, Job>()
    private val _publishedThumbnails = MutableStateFlow<Map<PublishedThumbnailKey, PublishedThumbnailSources>>(emptyMap())
    val publishedThumbnails: StateFlow<Map<PublishedThumbnailKey, PublishedThumbnailSources>> = _publishedThumbnails.asStateFlow()
    private val _publishedThumbnailRevision = MutableStateFlow(0L)
    val publishedThumbnailRevision: StateFlow<Long> = _publishedThumbnailRevision.asStateFlow()

    init {
        viewModelScope.launch {
            tokenStore.authState.collect {
                val current = tokenStore.currentSession()?.toThumbnailSession()
                if (current != thumbnailSession) onThumbnailSessionChanged(current)
            }
        }
        // force=false: en la carga inicial (o al recrearse el ViewModel
        // mientras la caché sigue tibia por otra pantalla) sirve aprovechar
        // SyncRepository -- solo el pull-to-refresh explícito (ver refresh())
        // debe garantizar bypass real de caché.
        load(force = false)
    }

    fun refresh() {
        retryVisibleThumbnails()
        load(force = true)
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            val requestingSession = tokenStore.currentSession()?.toThumbnailSession()
            // Ver comentario igual en DashboardViewModel.refresh().
            val previous = _uiState.value as? CalendarUiState.Success
            _uiState.value = previous?.copy(isRefreshing = true, refreshError = null) ?: CalendarUiState.Loading
            // Señal para AppTopBar -- ver RefreshActivityTracker.
            refreshTracker.setRefreshing("calendar", true)
            _uiState.value = try {
                val slots = syncRepository.getCalendarConfig(force = force).map { it.toSlot() }
                // Best-effort, mismo criterio que CalendarView.swift::load() --
                // si esto falla, el resto del calendario sigue funcionando
                // exactamente igual que antes de que existiera el strip
                // semanal. 60 = margen cómodo para cubrir 2 semanas de
                // historial real.
                val history = runCatching { syncApi.getHistory(limit = 60, offset = 0) }
                    .getOrNull()?.items ?: previous?.data?.history ?: emptyList()
                if (tokenStore.currentSession()?.toThumbnailSession() != requestingSession) return@launch
                calendarDataSession = requestingSession
                _publishedThumbnailRevision.value += 1
                CalendarUiState.Success(CalendarUiData(slots, history))
            } catch (e: Exception) {
                if (tokenStore.currentSession()?.toThumbnailSession() != requestingSession) return@launch
                // Boundary real: llamada a la central, puede fallar por red,
                // rol sin permiso (varios endpoints de /api/sync/* son
                // todopoderoso-only), o estar caída. Con datos previos:
                // banner no bloqueante, se mantiene lo último bueno. Sin
                // datos previos: Error, pantalla completa.
                val message = e.message ?: "No se pudo cargar el calendario."
                previous?.copy(isRefreshing = false, refreshError = message) ?: CalendarUiState.Error(message)
            } finally {
                refreshTracker.setRefreshing("calendar", false)
            }
        }
    }

    fun selectPublishedDay(day: LocalDate) {
        if (selectedPublishedDay == day) return
        selectedPublishedDay = day
        visiblePublishedEvents = emptyList()
        thumbnailGeneration += 1
        cancelThumbnailRequests()
        _publishedThumbnailRevision.value += 1
    }

    fun updateVisiblePublishedEvents(day: LocalDate, events: List<UploadHistoryItemDto>) {
        val currentSession = tokenStore.currentSession()?.toThumbnailSession()
        if (currentSession != thumbnailSession) onThumbnailSessionChanged(currentSession)
        if (selectedPublishedDay != day) selectPublishedDay(day)
        if (currentSession == null || calendarDataSession != currentSession) return

        val dayEvents = events.filter { it.thumbnailPublishedLocalDate() == day }
        visiblePublishedEvents = dayEvents
        dayEvents.forEach { event -> requestPublishedThumbnail(event, currentSession, thumbnailGeneration) }
    }

    private fun onThumbnailSessionChanged(session: ThumbnailSession?) {
        val oldJobs: List<Job>
        val oldStats: List<Deferred<GroupStatsItemDto>>
        synchronized(thumbnailLock) {
            if (thumbnailSession == session) return
            thumbnailSession = session
            calendarDataSession = null
            thumbnailCacheNamespace = UUID.randomUUID().toString()
            thumbnailGeneration += 1
            oldJobs = thumbnailJobs.values.toList()
            oldStats = fileStatsInFlight.values.toList()
            thumbnailJobs.clear()
            fileStatsInFlight.clear()
            fileStatsCache.clear()
            thumbnailResolutions.clear()
            _publishedThumbnails.value = emptyMap()
            _publishedThumbnailRevision.value += 1
            _uiState.value = CalendarUiState.Loading
        }
        oldJobs.forEach(Job::cancel)
        oldStats.forEach { it.cancel() }
        if (session != null) load(force = true)
    }

    private fun retryVisibleThumbnails() {
        val session = tokenStore.currentSession()?.toThumbnailSession()
        if (session == null || session != thumbnailSession || calendarDataSession != session) return
        val visibleKeys = visiblePublishedEvents.map(::publishedThumbnailKey).toSet()
        val visibleNames = visiblePublishedEvents.mapNotNull { it.fileName?.takeIf(String::isNotBlank) }.toSet()
        val jobs: List<Job>
        val stats: List<Deferred<GroupStatsItemDto>>
        synchronized(thumbnailLock) {
            thumbnailGeneration += 1
            thumbnailResolutions.keys.removeAll { it.session == session && it.event in visibleKeys }
            fileStatsCache.keys.removeAll { it.session == session && it.fileName in visibleNames }
            jobs = thumbnailJobs.filterKeys { it.session == session && it.event in visibleKeys }.values.toList()
            thumbnailJobs.keys.removeAll { it.session == session && it.event in visibleKeys }
            stats = fileStatsInFlight.filterKeys { it.session == session && it.fileName in visibleNames }.values.toList()
            fileStatsInFlight.keys.removeAll { it.session == session && it.fileName in visibleNames }
            _publishedThumbnailRevision.value += 1
        }
        jobs.forEach(Job::cancel)
        stats.forEach { it.cancel() }
    }

    private fun cancelThumbnailRequests() {
        val jobs: List<Job>
        val stats: List<Deferred<GroupStatsItemDto>>
        synchronized(thumbnailLock) {
            jobs = thumbnailJobs.values.toList()
            stats = fileStatsInFlight.values.toList()
            thumbnailJobs.clear()
            fileStatsInFlight.clear()
        }
        jobs.forEach(Job::cancel)
        stats.forEach { it.cancel() }
    }

    private fun requestPublishedThumbnail(
        event: UploadHistoryItemDto,
        session: ThumbnailSession,
        generation: Long,
    ) {
        val eventKey = publishedThumbnailKey(event)
        val resolutionKey = ThumbnailResolutionKey(session, eventKey)
        synchronized(thumbnailLock) {
            if (thumbnailSession != session || calendarDataSession != session || thumbnailGeneration != generation) return
            if (thumbnailResolutions.containsKey(resolutionKey) || thumbnailJobs[resolutionKey]?.isActive == true) {
                thumbnailResolutions[resolutionKey]?.let { resolved ->
                    if (resolved != null) _publishedThumbnails.value = _publishedThumbnails.value + (eventKey to resolved)
                }
                return
            }
            if (event.id.isBlank() || event.platformId.isBlank() || event.fileName.isNullOrBlank()) {
                thumbnailResolutions[resolutionKey] = null
                _publishedThumbnails.value = _publishedThumbnails.value - eventKey
                return
            }
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val sources = resolvePublishedThumbnail(event, eventKey, session, generation)
                    synchronized(thumbnailLock) {
                        if (!canPublishThumbnail(session, generation)) return@synchronized
                        thumbnailResolutions[resolutionKey] = sources
                        if (sources == null) {
                            _publishedThumbnails.value = _publishedThumbnails.value - eventKey
                        } else {
                            _publishedThumbnails.value = _publishedThumbnails.value + (eventKey to sources)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    synchronized(thumbnailLock) {
                        if (canPublishThumbnail(session, generation)) {
                            thumbnailResolutions[resolutionKey] = null
                        }
                    }
                } finally {
                    val currentJob = currentCoroutineContext()[Job]
                    synchronized(thumbnailLock) {
                        if (thumbnailJobs[resolutionKey] === currentJob) {
                            thumbnailJobs.remove(resolutionKey)
                        }
                    }
                }
            }
            thumbnailJobs[resolutionKey] = job
            job.start()
        }
    }

    private suspend fun resolvePublishedThumbnail(
        event: UploadHistoryItemDto,
        eventKey: PublishedThumbnailKey,
        session: ThumbnailSession,
        generation: Long,
    ): PublishedThumbnailSources? {
        val fileName = event.fileName?.takeIf(String::isNotBlank) ?: return null
        val stats = fileStatsFor(session, fileName) ?: return null
        if (!canPublishThumbnail(session, generation)) return null
        val platformStats = stats.platforms[event.platform] ?: return null
        if (platformStats.platformId.isBlank() || platformStats.platformId != event.platformId) return null

        val remoteId = stats.remoteLibraryVideoId?.takeIf(String::isNotBlank)
        val localPath = if (remoteId == null) {
            null
        } else {
            try {
                fileRepository.findByRemoteLibraryVideoId(remoteId)?.thumbnailPath
                    ?.takeIf { File(it).isFile }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (!canPublishThumbnail(session, generation)) return null
        val remoteUrl = remoteId?.let {
            remoteLibraryThumbnailUrl(
                retrofit.baseUrl(),
                it,
                stats.thumbnailStoredFileName?.takeIf(String::isNotBlank),
                session.token,
            )
        }
        val platformUrl = platformStats.thumbnail.takeIf(String::isNotBlank)
        if (localPath == null && remoteUrl == null && platformUrl == null) return null
        return PublishedThumbnailSources(
            localFilePath = localPath,
            remoteLibraryUrl = remoteUrl,
            platformThumbnailUrl = platformUrl,
            cacheNamespace = thumbnailCacheNamespace + ":" + generation,
        )
    }

    private suspend fun fileStatsFor(session: ThumbnailSession, fileName: String): GroupStatsItemDto? {
        val key = FileStatsCacheKey(session, fileName)
        val deferred: Deferred<GroupStatsItemDto>
        synchronized(thumbnailLock) {
            if (thumbnailSession != session) return null
            if (fileStatsCache.containsKey(key)) return fileStatsCache[key]
            deferred = fileStatsInFlight[key] ?: viewModelScope.async {
                thumbnailSemaphore.withPermit { syncRepository.getFileStats(fileName = fileName) }
            }.also { fileStatsInFlight[key] = it }
        }
        try {
            val result = deferred.await()
            synchronized(thumbnailLock) {
                if (thumbnailSession == session && tokenStore.currentSession()?.toThumbnailSession() == session) {
                    fileStatsCache[key] = result
                }
            }
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            synchronized(thumbnailLock) {
                if (thumbnailSession == session && tokenStore.currentSession()?.toThumbnailSession() == session) {
                    fileStatsCache[key] = null
                }
            }
            return null
        } finally {
            synchronized(thumbnailLock) {
                if (fileStatsInFlight[key] === deferred && deferred.isCompleted) {
                    fileStatsInFlight.remove(key)
                }
            }
        }
    }

    private fun canPublishThumbnail(session: ThumbnailSession, generation: Long): Boolean =
        thumbnailSession == session &&
            calendarDataSession == session &&
            thumbnailGeneration == generation &&
            tokenStore.currentSession()?.toThumbnailSession() == session

    // Mismo patrón que discardNext en CalendarView.swift: descarta SOLO para
    // esta plataforma puntual (no todo el archivo), y recarga para que el
    // "próximo" avance al siguiente candidato real.
    fun discardNext(slot: CalendarSlot) {
        val fileId = slot.nextVideo?.fileId ?: return
        viewModelScope.launch {
            try {
                syncApi.skipNextCalendarVideo(slot.platform, SkipNextRequest(fileId))
                load(force = true)
            } catch (e: Exception) {
                val previous = _uiState.value as? CalendarUiState.Success ?: return@launch
                // slots no está vacío acá (discardNext solo se puede disparar
                // con la lista visible) -- al banner no bloqueante, no a la
                // pantalla de error completa.
                _uiState.value = previous.copy(refreshError = e.message ?: "No se pudo descartar.")
            }
        }
    }

    // nextVideoId es un ObjectId de Mongo o un título (nunca un id de Room) --
    // la central ya lo resuelve contra FileModel y lo manda listo en
    // nextVideo.title (ver CalendarConfigDto), no hace falta re-resolverlo acá.
    private suspend fun CalendarConfigDto.toSlot(): CalendarSlot {
        val next = nextVideo
        val localPath = next?.let { fileRepository.findByName(it.title)?.thumbnailPath }
        val remoteUrl = if (localPath == null) next?.let(::resolveRemoteThumbnailUrl) else null
        val lastPublishedLocalPath = lastPublishedTitle.takeIf { it.isNotBlank() }
            ?.let { fileRepository.findByName(it)?.thumbnailPath }
        return CalendarSlot(
            platform = platform,
            lastPublishedTitle = lastPublishedTitle,
            lastPublishedDate = lastPublishedDate,
            intervalDays = intervalDays,
            nextVideo = next,
            localThumbnailPath = localPath,
            remoteThumbnailUrl = remoteUrl,
            lastPublishedLocalThumbnailPath = lastPublishedLocalPath,
            // lastPublishedDate llega en "yyyy-MM-dd" (ver
            // local-backend/backend: new Date().toISOString().slice(0, 10)) o ""
            // si la plataforma todavía no tiene nada publicado -- ahí no hay
            // fecha base de la que calcular la próxima.
            nextDate = lastPublishedDate.takeIf { it.isNotBlank() }
                ?.runCatching { LocalDate.parse(this) }
                ?.getOrNull()
                ?.plusDays(intervalDays.toLong()),
        )
    }

    private fun resolveRemoteThumbnailUrl(next: NextVideoDto): String? {
        val videoId = next.remoteLibraryVideoId ?: return null
        return remoteLibraryThumbnailUrl(retrofit.baseUrl(), videoId, next.thumbnailStoredFileName, tokenStore.token)
    }
}
