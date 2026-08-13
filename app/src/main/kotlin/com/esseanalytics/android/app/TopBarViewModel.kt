package com.esseanalytics.android.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.datastore.RefreshActivityTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): puente entre
// RefreshActivityTracker (singleton, sin scope de Compose propio) y
// AppTopBar -- se pide vía hiltViewModel() igual que SessionViewModel,
// scopeado al mismo ViewModelStoreOwner (la Activity, en esta navegación de
// un solo NavHost), así que sobrevive mientras la app está en primer plano.
@HiltViewModel
class TopBarViewModel @Inject constructor(
    tracker: RefreshActivityTracker,
) : ViewModel() {
    val isAnyRefreshing: StateFlow<Boolean> = tracker.activeKeys
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
