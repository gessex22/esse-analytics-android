package com.esseanalytics.android.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.CatalogRegistrationOutbox
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.DashboardPendingActionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject

sealed interface PendingActionState {
    data object Idle : PendingActionState
    data object Loading : PendingActionState
    data class Local(val id: Long) : PendingActionState
    data class Remote(val id: String) : PendingActionState
    data class Message(val text: String, val canImport: Boolean = false) : PendingActionState
}
@HiltViewModel
class DashboardPendingActionViewModel @Inject constructor(
    private val files: FileRepository, private val identity: CausalPlatformDao,
    private val settings: SettingsStore, private val tokens: TokenStore, private val api: SyncApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<PendingActionState>(PendingActionState.Idle)
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var last: DashboardPendingActionDto? = null
    private var generation = 0L
    fun clear() { _state.value = PendingActionState.Idle }
    fun cancel() { ++generation; job?.cancel(); clear() }
    private var cloudAllowed = false
    fun retry() { last?.let { open(it, cloudAllowed) } }
    fun open(action: DashboardPendingActionDto, canUseCloudStorage: Boolean) {
        val requestId = ++generation
        cloudAllowed = canUseCloudStorage
        job?.cancel(); last = action; _state.value = PendingActionState.Loading
        val session = tokens.currentSession()
        job = viewModelScope.launch {
            try {
                requireNotNull(session)
                val server = settings.serverUrl.first()
                val userKey = CatalogRegistrationOutbox.scope(session.second, server)
                val canonicalId = action.contentId?.takeIf(String::isNotBlank)
                // A central file id is never a Room row id. Match only scoped learned identities.
                val local = if (canonicalId == null) null else files.observeAll().first().firstOrNull {
                    identity.findContentId(userKey, CausalKeys.forClientFile(it.clientFileId)) == canonicalId && readable(it.filePath)
                }
                val result = if (local != null) PendingActionState.Local(local.id) else {
                    val detail = api.getVideoDetail(fileId = action.fileId ?: action.contentId, fileName = action.fileName.takeIf { action.fileId == null && action.contentId == null && it.isNotBlank() })
                    detail.remoteLibraryVideoId?.takeIf(String::isNotBlank)?.let { if (canUseCloudStorage) PendingActionState.Remote(it) else PendingActionState.Message("Este video está en la biblioteca remota. Importa el original en este teléfono para continuar con tu acceso actual.", true) }
                        ?: PendingActionState.Message("El original no está disponible en este teléfono ni en la biblioteca remota. Importa el archivo original para continuar.", true)
                }
                ensureActive()
                if (requestId != generation) return@launch
                _state.value = if (session == tokens.currentSession() && settings.serverUrl.first() == server) result else PendingActionState.Message("La sesión cambió. Actualiza el Dashboard.")
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if (requestId == generation) _state.value = PendingActionState.Message(
                    if (e is retrofit2.HttpException && e.code() in listOf(404, 405)) "El servidor necesita actualizarse para abrir el detalle de este pendiente."
                    else "No se pudo abrir este pendiente. Comprueba la conexión y vuelve a intentarlo."
                )
            }
        }
    }
    private suspend fun readable(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (path.startsWith("content://")) context.contentResolver.openAssetFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
            else File(if(path.startsWith("file://")) Uri.parse(path).path.orEmpty() else path).let { it.isFile && it.canRead() }
        }.getOrDefault(false)
    }
}
