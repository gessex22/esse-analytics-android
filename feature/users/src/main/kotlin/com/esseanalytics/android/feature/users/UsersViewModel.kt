package com.esseanalytics.android.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.network.api.AuthApi
import com.esseanalytics.android.core.network.dto.AppUserDto
import com.esseanalytics.android.core.network.dto.UpdateTierRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UserStatusFilter(val apiValue: String) { ACTIVE("active"), DELETED("deleted") }

sealed interface UsersUiState {
    data object Loading : UsersUiState
    data class Success(val users: List<AppUserDto>, val total: Int) : UsersUiState
    data class Error(val message: String) : UsersUiState
}

// Mismos 3 endpoints que ya consume frontend/src/components/UsersPanel.tsx --
// pantalla owner-only (la central 403-ea si no sos todopoderoso, no se
// duplica esa validación acá). toggleTier actualiza optimista sin refetch
// (igual que desktop); deactivate SÍ recarga la lista porque el usuario
// tiene que desaparecer de "Activos".
@OptIn(FlowPreview::class)
@HiltViewModel
class UsersViewModel @Inject constructor(
    private val authApi: AuthApi,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _statusFilter = MutableStateFlow(UserStatusFilter.ACTIVE)
    val statusFilter: StateFlow<UserStatusFilter> = _statusFilter.asStateFlow()

    private val _uiState = MutableStateFlow<UsersUiState>(UsersUiState.Loading)
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    private val _togglingUserId = MutableStateFlow<String?>(null)
    val togglingUserId: StateFlow<String?> = _togglingUserId.asStateFlow()

    private val _deactivatingUserId = MutableStateFlow<String?>(null)
    val deactivatingUserId: StateFlow<String?> = _deactivatingUserId.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(_query.debounce(350).distinctUntilChanged(), _statusFilter) { q, status -> q to status }
                .collect { (q, status) -> load(q, status) }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onStatusFilterChange(filter: UserStatusFilter) {
        _statusFilter.value = filter
    }

    fun refresh() = load(_query.value, _statusFilter.value)

    private fun load(query: String, status: UserStatusFilter) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = UsersUiState.Loading
            _uiState.value = try {
                val response = authApi.listUsers(
                    status = status.apiValue,
                    limit = 5,
                    query = query.trim().ifBlank { null },
                )
                UsersUiState.Success(response.users, response.total)
            } catch (e: Exception) {
                // Boundary real: llamada a la central, red/rol/caída.
                UsersUiState.Error(e.message ?: "No se pudieron cargar los usuarios.")
            }
        }
    }

    fun toggleTier(user: AppUserDto) {
        val newTier = if (user.tier == "premium") "free" else "premium"
        val current = _uiState.value
        if (current is UsersUiState.Success) {
            _uiState.value = current.copy(
                users = current.users.map { if (it.id == user.id) it.copy(tier = newTier) else it },
            )
        }
        viewModelScope.launch {
            _togglingUserId.value = user.id
            runCatching { authApi.updateUserTier(user.id, UpdateTierRequest(newTier)) }
            _togglingUserId.value = null
        }
    }

    fun deactivate(user: AppUserDto) {
        viewModelScope.launch {
            _deactivatingUserId.value = user.id
            runCatching { authApi.deactivateUser(user.id) }
            _deactivatingUserId.value = null
            refresh()
        }
    }
}
