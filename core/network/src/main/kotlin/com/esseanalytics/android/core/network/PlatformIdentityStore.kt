package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.dto.ResolveIdentityRequest
import javax.inject.Inject
import javax.inject.Singleton

// Resolución de identidad causal para el flusher -- best-effort. Un miss
// (central no conoce el contenido, red caída, fila sin clientFileId) devuelve
// null y la fila queda excluida hasta el próximo intento; nunca se fabrica un
// contentId ni se lo deduce del nombre.
interface CausalIdentityResolver {
    suspend fun resolveContentId(
        userKey: String,
        remoteLibraryVideoId: String?,
        clientFileId: String?,
        fileName: String?,
    ): String?
}

// Id de instalación estable que exige el contrato de resolve-identity. Existe
// como interfaz (y no como dependencia directa de SettingsStore) para poder
// resolver identidad en un test JVM sin DataStore/Context -- ver NetworkModule
// para el binding real.
interface DeviceIdProvider {
    suspend fun deviceId(): String
}

// Núcleo de la identidad causal, SIN sesión (recibe el userKey ya resuelto),
// igual que CausalFlusher -- así se puede ejercitar entero con fakes de
// DAO/API. Dos caminos de hidratación, ambos desde datos REALES de la central:
//
//  1. hydrateFromRemote: al listar la cola remota (RemoteLibraryViewModel),
//     cada RemoteLibraryVideoDto trae contentId + platformRev. La clave que se
//     guarda es la del _id remoto; además, si ESE video ya se bajó a este
//     teléfono, se guarda también la clave por su clientFileId (evidencia
//     inequívoca: el archivo local registró ese remoteLibraryVideoId al
//     importarse). Nunca se guarda una clave por fileName.
//  2. resolve: bootstrap para archivos locales (sin DTO remoto) vía
//     POST /api/sync/resolve-identity, mandando (deviceId, clientFileId).
class CausalIdentityResolution @Inject constructor(
    private val syncApi: SyncApi,
    private val causalDao: CausalPlatformDao,
    private val fileDao: FileDao,
    private val deviceIdProvider: DeviceIdProvider,
) {
    suspend fun hydrateFromRemote(userKey: String, videos: List<RemoteLibraryVideoDto>) {
        for (video in videos) {
            val contentId = video.contentId ?: continue
            causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forRemote(video._id), contentId))
            // El archivo local se busca por remoteLibraryVideoId (link explícito
            // guardado al importar desde Nube), NO por fileName.
            val localClientFileId = fileDao.findByRemoteLibraryVideoId(video._id)?.clientFileId
            if (localClientFileId != null) {
                causalDao.upsertIdentity(
                    ContentIdentityEntity(userKey, CausalKeys.forClientFile(localClientFileId), contentId),
                )
            }
            video.platformRev?.forEach { (platform, revision) ->
                causalDao.upsertRevision(PlatformRevisionEntity(userKey, contentId, platform, revision))
            }
            // Se completan las filas del outbox que estaban esperando esta
            // identidad. La revisión NO se propaga a la baseVersion de las filas
            // en bloque -- eso lo hace el flusher perezosamente sobre la cabeza.
            causalDao.hydrateContentIdByRemoteId(userKey, video._id, contentId)
            localClientFileId?.let { causalDao.hydrateContentIdByClientFileId(userKey, it, contentId) }
        }
    }

    suspend fun resolve(
        userKey: String,
        remoteLibraryVideoId: String?,
        clientFileId: String?,
        fileName: String?,
    ): String? {
        // Cache primero -- evita pegarle a la central por algo ya resuelto (por
        // el pull o por un resolve anterior). Solo por handles de identidad
        // reales: un cache por fileName haría que dos videos homónimos
        // compartieran contentId, que es justo el bug que esto corrige.
        remoteLibraryVideoId?.let {
            causalDao.findContentId(userKey, CausalKeys.forRemote(it))?.let { cached -> return cached }
        }
        clientFileId?.let {
            causalDao.findContentId(userKey, CausalKeys.forClientFile(it))?.let { cached -> return cached }
        }
        // Sin clientFileId no hay pedido posible: el contrato exige
        // (deviceId, clientFileId) no vacíos. Fila legacy/ambigua -> se
        // conserva encolada y NO se toca la red.
        val localId = clientFileId?.takeIf { it.isNotBlank() } ?: return null
        val deviceId = runCatching { deviceIdProvider.deviceId() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val response = runCatching {
            syncApi.resolveIdentity(
                ResolveIdentityRequest(
                    fileName = fileName.orEmpty(),
                    remoteLibraryVideoId = remoteLibraryVideoId,
                    deviceId = deviceId,
                    clientFileId = localId,
                ),
            )
        }.getOrNull() ?: return null
        val contentId = response.contentId ?: return null
        causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forClientFile(localId), contentId))
        remoteLibraryVideoId?.let {
            causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forRemote(it), contentId))
        }
        response.platformRev?.forEach { (platform, revision) ->
            causalDao.upsertRevision(PlatformRevisionEntity(userKey, contentId, platform, revision))
        }
        return contentId
    }
}

// Wrapper inyectable: aporta el userKey del usuario logueado (aislamiento por
// sesión -- la identidad aprendida con una cuenta no se mezcla con otra) y
// delega en CausalIdentityResolution. Mismo patrón que
// CausalPlatformOutbox/CausalFlusher.
@Singleton
class PlatformIdentityStore @Inject constructor(
    private val resolution: CausalIdentityResolution,
    private val tokenStore: TokenStore,
) : CausalIdentityResolver {

    suspend fun hydrateFromRemote(videos: List<RemoteLibraryVideoDto>) {
        val userKey = tokenStore.currentUser?.id ?: return
        resolution.hydrateFromRemote(userKey, videos)
    }

    override suspend fun resolveContentId(
        userKey: String,
        remoteLibraryVideoId: String?,
        clientFileId: String?,
        fileName: String?,
    ): String? = resolution.resolve(userKey, remoteLibraryVideoId, clientFileId, fileName)
}
