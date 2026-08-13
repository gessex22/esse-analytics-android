package com.esseanalytics.android.core.datastore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): pedido explícito
// del usuario -- el spinner de refresh vive en la barra superior compartida
// (AppTopBar en EsseAnalyticsNavHost.kt), a la izquierda del avatar, en vez
// de metido en el contenido de cada pantalla.
//
// A diferencia de iOS (donde AppTopBar se instancia UNA VEZ POR PANTALLA,
// así que cada una puede mostrar directo su propio `isLoading` local), en
// Android la barra es UNA sola instancia compartida a nivel de
// MainAppScaffold, fuera del alcance de los ViewModels de
// Dashboard/Calendar/Stats (cada uno con su propio hiltViewModel() dentro
// de su pantalla). Este singleton es el punto de encuentro: cada ViewModel
// marca su propia clave al arrancar/terminar un refresh, la barra solo
// necesita saber si el conjunto no está vacío.
@Singleton
class RefreshActivityTracker @Inject constructor() {
    private val _activeKeys = MutableStateFlow<Set<String>>(emptySet())
    val activeKeys: StateFlow<Set<String>> = _activeKeys.asStateFlow()

    fun setRefreshing(key: String, refreshing: Boolean) {
        _activeKeys.update { current ->
            if (refreshing) current + key else current - key
        }
    }
}
