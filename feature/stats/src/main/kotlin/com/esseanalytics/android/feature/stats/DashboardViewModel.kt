package com.esseanalytics.android.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.CausalPlatformOutbox
import com.esseanalytics.android.core.network.HistoryOutbox
import com.esseanalytics.android.core.network.PlatformUpdateOutbox
import com.esseanalytics.android.core.network.SyncRepository
import com.esseanalytics.android.core.network.dto.DashboardSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.io.IOException
import retrofit2.HttpException
import javax.inject.Inject

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val data: DashboardSummaryDto, val isRefreshing: Boolean = false, val refreshError: String? = null, val compatibilityMode: Boolean = false) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val refreshTracker: RefreshActivityTracker,
    private val historyOutbox: HistoryOutbox,
    private val platformUpdateOutbox: PlatformUpdateOutbox,
    private val causalPlatformOutbox: CausalPlatformOutbox,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()
    val dashboardStyle = settingsStore.dashboardStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "current")
    private var loadJob: Job? = null
    private var generation = 0L

    init { refresh() }

    fun refresh() {
        val requestedGeneration = ++generation
        val requestedSession = tokenStore.token
        loadJob?.cancel()
        // Preserve existing publication recovery; refresh never waits for outboxes.
        viewModelScope.launch { runCatching { historyOutbox.flush() } }
        viewModelScope.launch { runCatching { platformUpdateOutbox.flush() } }
        viewModelScope.launch { runCatching { causalPlatformOutbox.flush() } }
        loadJob = viewModelScope.launch {
            val previous = _uiState.value as? DashboardUiState.Success
            _uiState.value = previous?.copy(isRefreshing = true, refreshError = null) ?: DashboardUiState.Loading
            refreshTracker.setRefreshing("dashboard", true)
            try {
                val result = syncRepository.getDashboardSummary(ZoneId.systemDefault().id)
                currentCoroutineContext().ensureActive()
                _uiState.value = if (requestedSession != tokenStore.token) DashboardUiState.Error("La sesión cambió. Actualiza los datos.") else DashboardUiState.Success(result.data, compatibilityMode = result.compatibilityMode)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when (error) {
                    is HttpException -> when (error.code()) {
                        401 -> "Tu sesión venció. Vuelve a iniciar sesión."
                        403 -> "Tu cuenta no tiene acceso a este resumen."
                        404, 405 -> "El servidor necesita una actualización para mostrar el Dashboard."
                        in 500..599 -> "El servidor no pudo cargar el Dashboard. Vuelve a intentarlo."
                        else -> "No se pudo actualizar el Dashboard. Vuelve a intentarlo."
                    }
                    is IOException -> "No se pudo conectar con el servidor. Comprueba tu conexión y vuelve a intentarlo."
                    else -> "La respuesta del servidor no se pudo leer. Vuelve a intentarlo."
                }
                _uiState.value = if (requestedSession != tokenStore.token) DashboardUiState.Error("La sesión cambió. Actualiza los datos.")
                    else previous?.copy(isRefreshing = false, refreshError = message) ?: DashboardUiState.Error(message)
            } finally {
                if (generation == requestedGeneration) refreshTracker.setRefreshing("dashboard", false)
            }
        }
    }
}
