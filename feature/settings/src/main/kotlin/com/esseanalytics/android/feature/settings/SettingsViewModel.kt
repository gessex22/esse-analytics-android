package com.esseanalytics.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.model.WorkflowMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Junta acá los settings que ya existían sueltos (workflowMode y
// wifiOnlyUploads venían de Fase 0/1 sin pantalla propia) más el nuevo
// selector de tema — todo lee/escribe directo a SettingsStore.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val colorTheme: StateFlow<String> = settingsStore.colorTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "rojo")

    val workflowMode: StateFlow<WorkflowMode> = settingsStore.workflowMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkflowMode.SIMPLE)

    val wifiOnlyUploads: StateFlow<Boolean> = settingsStore.wifiOnlyUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setColorTheme(value: String) {
        viewModelScope.launch { settingsStore.setColorTheme(value) }
    }

    fun setWorkflowMode(mode: WorkflowMode) {
        viewModelScope.launch { settingsStore.setWorkflowMode(mode) }
    }

    fun setWifiOnlyUploads(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWifiOnlyUploads(enabled) }
    }
}
