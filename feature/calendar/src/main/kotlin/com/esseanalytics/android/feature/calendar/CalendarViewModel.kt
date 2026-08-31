package com.esseanalytics.android.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.CalendarConfigDto
import com.esseanalytics.android.core.network.dto.NextVideoDto
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
    val nextFileName: String?,
    // lastPublishedDate + intervalDays -- mismo cálculo que calcNextDate() en
    // frontend/src/data/mockPublishingData.ts (consumido por
    // PublishingQueue.tsx en desktop). null si todavía no hay una fecha base
    // (plataforma sin nada publicado todavía, la central manda "").
    val nextDate: LocalDate?,
    // El "próximo" puede vivir en ESTE dispositivo (se grabó/importó acá) o
    // solo en el de otra persona/PC -- local primero (más barato, ya en
    // disco), Biblioteca remota como respaldo. Mismo criterio que
    // thumbnailUrl() en StatsViewModel, pero con el paso local que ahí no
    // existe (group-stats siempre trae videos ya publicados en las 3 redes,
    // pero "próximo" es justo lo contrario: todavía sin publicar).
    val localThumbnailPath: String? = null,
    val remoteThumbnailUrl: String? = null,
)

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): isRefreshing/
// refreshError dentro de Success -- mismo criterio que Dashboard/Stats.
sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(
        val slots: List<CalendarSlot>,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

// GET /api/sync/calendar-config alimenta el Calendario real de desktop
// (PublishingQueue.tsx) -- la cadencia por plataforma (último publicado,
// cada cuántos días, qué sigue) vive en la central, no en Room local. Acá se
// consume en modo lectura; drag-drop/edición de fechas queda para Fase 2.
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
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
                CalendarUiState.Success(syncRepository.getCalendarConfig(force = force).map { it.toSlot() })
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

    // nextVideoId es un ObjectId de Mongo o un título (nunca un id de Room) --
    // la central ya lo resuelve contra FileModel y lo manda listo en
    // nextVideo.title (ver CalendarConfigDto), no hace falta re-resolverlo acá.
    private suspend fun CalendarConfigDto.toSlot(): CalendarSlot {
        val next = nextVideo
        val localPath = next?.let { fileRepository.findByName(it.title)?.thumbnailPath }
        val remoteUrl = if (localPath == null) next?.let(::resolveRemoteThumbnailUrl) else null
        return CalendarSlot(
            platform = platform,
            lastPublishedTitle = lastPublishedTitle,
            lastPublishedDate = lastPublishedDate,
            intervalDays = intervalDays,
            nextFileName = next?.title,
            localThumbnailPath = localPath,
            remoteThumbnailUrl = remoteUrl,
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
