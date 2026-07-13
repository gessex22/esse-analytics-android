package com.esseanalytics.android.feature.ingest

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface IngestUiState {
    data object Idle : IngestUiState
    data object Importing : IngestUiState
    data class Success(val file: VideoFile) : IngestUiState
    data class Duplicate(val existing: VideoFile) : IngestUiState
    data class Error(val message: String) : IngestUiState
}

@HiltViewModel
class IngestViewModel @Inject constructor(
    private val importUseCase: ImportUseCase,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<IngestUiState>(IngestUiState.Idle)
    val uiState: StateFlow<IngestUiState> = _uiState.asStateFlow()

    val deleteOriginalAfterImport: StateFlow<Boolean> = settingsStore.deleteOriginalAfterImport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDeleteOriginalAfterImport(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDeleteOriginalAfterImport(enabled) }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = IngestUiState.Importing
            // Importa todos secuencialmente; el estado final que ve el
            // usuario es el del último. Selección múltiple con progreso por
            // archivo queda para cuando la biblioteca tenga esa UI (Fase 2).
            var lastState: IngestUiState = IngestUiState.Idle
            uris.forEach { uri ->
                lastState = when (val result = importUseCase.import(uri)) {
                    is ImportResult.Success -> IngestUiState.Success(result.file)
                    is ImportResult.Duplicate -> IngestUiState.Duplicate(result.existing)
                    is ImportResult.Error -> IngestUiState.Error(result.message)
                }
            }
            _uiState.value = lastState
        }
    }

    fun resetState() {
        _uiState.value = IngestUiState.Idle
    }
}
