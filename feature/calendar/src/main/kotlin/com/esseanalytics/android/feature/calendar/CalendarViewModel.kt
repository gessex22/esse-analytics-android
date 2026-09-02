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
import com.esseanalytics.android.core.network.dto.NextVideoDto
import com.esseanalytics.android.core.network.dto.SkipNextRequest
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import java.time.LocalDate
import javax.inject.Inject

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

    init {
        // force=false: en la carga inicial (o al recrearse el ViewModel
        // mientras la caché sigue tibia por otra pantalla) sirve aprovechar
        // SyncRepository -- solo el pull-to-refresh explícito (ver refresh())
        // debe garantizar bypass real de caché.
        load(force = false)
    }

    fun refresh() {
        load(force = true)
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
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
                CalendarUiState.Success(CalendarUiData(slots, history))
            } catch (e: Exception) {
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
