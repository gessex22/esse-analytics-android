package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.KIND_LINK
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.ManualPlatformLinkRequest
import com.esseanalytics.android.core.network.dto.PlatformCausalResponse
import com.esseanalytics.android.core.network.dto.PlatformTransitionRequest
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

// Núcleo del drenado causal, sin dependencias de Android (recibe el userKey ya
// resuelto) para poder testearlo con fakes de DAO/API/resolver. El wrapper
// CausalPlatformOutbox le pasa el userKey desde TokenStore.
//
// Algoritmo:
//  1. bootstrap de identidad para filas sin contentId (resolve-identity).
//  2. drenado por CADENA (contentId, platform): se envía SOLO la cabeza; si
//     aplica/queda stale/es terminal, sale y se avanza a la siguiente (que
//     rebasea contra la revisión ya avanzada); si es in_progress o
//     reintentable, se frena la cadena -- ningún descendiente sale con una base
//     que ya quedaría vieja.
class CausalFlusher @Inject constructor(
    private val dao: CausalPlatformDao,
    private val syncApi: SyncApi,
    private val identityResolver: CausalIdentityResolver,
    private val json: Json,
) {
    suspend fun flush(userKey: String) {
        bootstrapIdentities(userKey)
        drainChains(userKey)
    }

    private suspend fun bootstrapIdentities(userKey: String) {
        val unresolved = dao.getUnresolved(userKey)
        val attempted = HashSet<Pair<String?, String?>>()
        for (row in unresolved) {
            val handle = row.remoteLibraryVideoId to row.fileName
            if (!attempted.add(handle)) continue
            val contentId = identityResolver
                .resolveContentId(userKey, row.remoteLibraryVideoId, row.fileName) ?: continue
            row.remoteLibraryVideoId?.let { dao.hydrateContentIdByRemoteId(userKey, it, contentId) }
            row.fileName?.let { dao.hydrateContentIdByFileName(userKey, it, contentId) }
        }
    }

    private suspend fun drainChains(userKey: String) {
        // getResolvable ya viene ordenado por contentId, platform, createdAt, id.
        // groupBy conserva ese orden (LinkedHashMap), así cada cadena queda en
        // orden causal.
        val chains = dao.getResolvable(userKey).groupBy { it.contentId to it.platform }
        for ((_, chain) in chains) {
            drainChain(userKey, chain)
        }
    }

    private suspend fun drainChain(userKey: String, chain: List<PlatformTransitionOutboxEntity>) {
        for (original in chain) {
            var row = original
            // baseVersion perezosa de la cabeza: si es transición sin base y
            // todavía no se conoce la revisión real, se frena TODA la cadena (no
            // se inventa una base). Una vez resuelta, se persiste (durable y
            // estable -- no se recalcula en reintentos).
            if (row.kind != KIND_LINK && row.baseVersion == null) {
                val base = dao.findRevision(userKey, row.contentId ?: break, row.platform) ?: break
                row = row.copy(baseVersion = base)
                dao.updateOutbox(row)
            }
            when (val outcome = send(row)) {
                is CausalOutcome.Applied -> { advanceRevision(userKey, row, outcome.version); dao.deleteOutbox(row) }
                is CausalOutcome.Stale -> { advanceRevision(userKey, row, outcome.version); dao.deleteOutbox(row) }
                CausalOutcome.Terminal -> dao.deleteOutbox(row)
                CausalOutcome.InProgress -> return@drainChain
                CausalOutcome.Retry -> { dao.updateOutbox(row.copy(attempts = row.attempts + 1)); return@drainChain }
            }
        }
    }

    private suspend fun send(row: PlatformTransitionOutboxEntity): CausalOutcome {
        val contentId = row.contentId ?: return CausalOutcome.Terminal
        val result = if (row.kind == KIND_LINK) {
            runCatching {
                syncApi.manualPlatformLink(
                    ManualPlatformLinkRequest(
                        contentId = contentId,
                        platform = row.platform,
                        platformId = row.platformId.orEmpty(),
                        platformUrl = row.platformUrl,
                        title = row.title,
                        publishedAt = row.publishedAt,
                        operationId = row.operationId,
                    ),
                )
            }
        } else {
            runCatching {
                syncApi.platformTransition(
                    PlatformTransitionRequest(
                        contentId = contentId,
                        platform = row.platform,
                        action = row.action.orEmpty(),
                        operationId = row.operationId,
                        baseVersion = row.baseVersion ?: return CausalOutcome.Retry,
                    ),
                )
            }
        }
        return toOutcome(result.getOrNull())
    }

    // response == null -> excepción (red/timeout). En 2xx la versión viene en el
    // body; en 409 viene en el errorBody (Retrofit no lo parsea solo con
    // Response<T>). isLenient/ignoreUnknownKeys del Json evita romper por un
    // shape distinto -- si no se puede leer la versión, se termina el conflicto
    // igual y la revisión se corrige en el próximo pull.
    private fun toOutcome(response: Response<PlatformCausalResponse>?): CausalOutcome {
        if (response == null) return classifyCausalResponse(null, null)
        val version = if (response.isSuccessful) response.body()?.version else parseErrorVersion(response)
        return classifyCausalResponse(response.code(), version)
    }

    private fun parseErrorVersion(response: Response<PlatformCausalResponse>): Long? = runCatching {
        val raw = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        json.decodeFromString(PlatformCausalResponse.serializer(), raw).version
    }.getOrNull()

    private suspend fun advanceRevision(userKey: String, row: PlatformTransitionOutboxEntity, version: Long?) {
        val contentId = row.contentId ?: return
        if (version == null) return
        dao.upsertRevision(PlatformRevisionEntity(userKey, contentId, row.platform, version))
    }
}

// Wrapper inyectable: obtiene el userKey del usuario logueado (aislamiento por
// sesión -- un cambio encolado por un usuario no se manda con la sesión de
// otro) y delega en CausalFlusher. Se drena en cada apertura/refresh del
// Dashboard (ver DashboardViewModel) y tras un toggle/link local, igual que
// HistoryOutbox/PlatformUpdateOutbox.
@Singleton
class CausalPlatformOutbox @Inject constructor(
    private val flusher: CausalFlusher,
    private val tokenStore: TokenStore,
) {
    suspend fun flush() {
        val userKey = tokenStore.currentUser?.id ?: return
        flusher.flush(userKey)
    }
}
