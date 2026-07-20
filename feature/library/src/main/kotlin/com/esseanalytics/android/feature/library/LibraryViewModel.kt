package com.esseanalytics.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.AuthState
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.BackupApi
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.dto.BackupFileDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    fileRepository: FileRepository,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val remoteLibraryApi: RemoteLibraryApi,
    private val backupApi: BackupApi,
    tokenStore: TokenStore,
) : ViewModel() {

    // Premium + entitlement de storage aparte (ver requireCloudStorage en la
    // central) -- reactivo a tokenStore.authState para que un refreshUser()
    // que cambie el entitlement se refleje sin tener que reloguear la UI.
    val canUseCloudStorage: StateFlow<Boolean> = tokenStore.authState
        .map { (it as? AuthState.LoggedIn)?.user?.canUseCloudStorage == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Catálogo de solo lectura (backup automático del escritorio) -- gratis
    // para todo premium (requirePremium en la central), no requiere el
    // entitlement de storage aparte que sí pide la cola remota de arriba.
    val canSeeBackupCatalog: StateFlow<Boolean> = tokenStore.authState
        .map { (it as? AuthState.LoggedIn)?.user?.isPremium == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _remoteVideos = MutableStateFlow<List<RemoteLibraryVideoDto>>(emptyList())
    private val _backupFiles = MutableStateFlow<List<BackupFileDto>>(emptyList())

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    // flowOn(Default): con el catálogo de backup (Parte E) esto fusiona y
    // ordena hasta ~1000+ ítems -- sin esto corría en el dispatcher del
    // colector (viewModelScope = Main), y CADA emisión de CUALQUIERA de las
    // 4 fuentes (incluido Room re-emitiendo por cambios que no tocan la
    // lista en sí) volvía a ordenar todo en el hilo principal, trabando la
    // UI. La UI sigue viendo el StateFlow normal, solo el cómputo se mueve.
    val items: StateFlow<List<LibraryListItem>> = combine(
        fileRepository.observeAll(),
        _remoteVideos,
        _backupFiles,
        _filter,
    ) { local, remote, backup, filter ->
        val merged = buildList {
            if (filter == LibraryFilter.ALL || filter == LibraryFilter.LOCAL) {
                addAll(local.map { LibraryListItem.Local(it) })
            }
            if (filter == LibraryFilter.ALL || filter == LibraryFilter.REMOTE) {
                addAll(remote.map { LibraryListItem.Remote(it) })
            }
            if (filter == LibraryFilter.ALL || filter == LibraryFilter.BACKUP_CATALOG) {
                addAll(backup.map { LibraryListItem.BackupCatalog(it) })
            }
        }
        merged.sortedByDescending { it.sortInstant }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    // Se llama desde la pantalla apenas canUseCloudStorage es true (ver
    // LibraryScreen) -- si falla (sin red, entitlement recién revocado) se
    // traga el error y deja la lista remota como estaba, el usuario igual ve
    // sus locales sin interrupción. withContext(IO): la respuesta del
    // catálogo puede traer cientos de objetos (ver refreshBackupCatalog) --
    // parsear el JSON explícitamente fuera de Main evita depender de en qué
    // hilo reanuda Retrofit la corrutina.
    fun refreshRemote() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remoteLibraryApi.listVideos().videos } }
                .onSuccess { _remoteVideos.value = it }
        }
    }

    // Mismo criterio que refreshRemote(): se traga el error, el catálogo
    // simplemente queda vacío si falla. Acá SÍ importa el withContext(IO) --
    // esto puede traer 1000+ registros (ver el caso real de `esse`, 1137),
    // deserializarlos en Main trababa la UI apenas se abría Videos.
    fun refreshBackupCatalog() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { backupApi.listFiles().files } }
                .onSuccess { _backupFiles.value = it }
        }
    }

    fun delete(item: LibraryListItem) {
        viewModelScope.launch {
            when (item) {
                is LibraryListItem.Local -> deleteVideoUseCase.delete(item.file)
                is LibraryListItem.Remote -> {
                    runCatching { remoteLibraryApi.deleteVideo(item.video._id) }
                    refreshRemote()
                }
                // Sin acción de borrado -- es un mirror de solo lectura, no
                // hay nada que la app pueda eliminar del lado de la central
                // ni de la PC dueña desde acá (ver LibraryScreen, sin botón
                // de borrar para este caso).
                is LibraryListItem.BackupCatalog -> Unit
            }
        }
    }
}
