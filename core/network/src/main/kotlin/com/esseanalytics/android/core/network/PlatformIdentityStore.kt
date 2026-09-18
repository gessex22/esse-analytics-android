package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.dto.ResolveIdentityRequest
import javax.inject.Inject
import javax.inject.Singleton

// Resolución de identidad causal para el flusher -- best-effort. Un miss
// (central no conoce el contenido, red caída) devuelve null y la fila queda
// excluida hasta el próximo intento; nunca se fabrica un contentId.
interface CausalIdentityResolver {
    suspend fun resolveContentId(userKey: String, remoteLibraryVideoId: String?, fileName: String?): String?
}

// Fuente única de identidad/revisión causal. Dos caminos de hidratación, ambos
// desde datos REALES de la central (nunca inferidos de un fileName):
//  1. hydrateFromRemote: al listar la cola remota (RemoteLibraryViewModel),
//     cada RemoteLibraryVideoDto trae contentId + platformRev.
//  2. resolveContentId: bootstrap para archivos locales (sin DTO remoto) vía
//     POST /api/sync/resolve-identity -- la central resuelve SU identidad a
//     partir del fileName; acá solo se cachea lo que devuelve.
@Singleton
class PlatformIdentityStore @Inject constructor(
    private val syncApi: SyncApi,
    private val causalDao: CausalPlatformDao,
    private val tokenStore: TokenStore,
) : CausalIdentityResolver {

    // Hidratación desde el pull de la cola remota. Escribe identidad (por _id y
    // por fileName) + revisiones, y completa las filas del outbox que estaban
    // esperando esa identidad. La revisión NO se propaga a la baseVersion de las
    // filas en bloque -- eso lo hace el flusher perezosamente sobre la cabeza.
    suspend fun hydrateFromRemote(videos: List<RemoteLibraryVideoDto>) {
        val userKey = tokenStore.currentUser?.id ?: return
        for (video in videos) {
            val contentId = video.contentId ?: continue
            causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forRemote(video._id), contentId))
            causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forFile(video.fileName), contentId))
            video.platformRev?.forEach { (platform, revision) ->
                causalDao.upsertRevision(PlatformRevisionEntity(userKey, contentId, platform, revision))
            }
            causalDao.hydrateContentIdByRemoteId(userKey, video._id, contentId)
            causalDao.hydrateContentIdByFileName(userKey, video.fileName, contentId)
        }
    }

    override suspend fun resolveContentId(
        userKey: String,
        remoteLibraryVideoId: String?,
        fileName: String?,
    ): String? {
        // Cache primero -- evita pegarle a la central por algo ya resuelto (por
        // el pull o por un resolve anterior).
        remoteLibraryVideoId?.let {
            causalDao.findContentId(userKey, CausalKeys.forRemote(it))?.let { cached -> return cached }
        }
        fileName?.let {
            causalDao.findContentId(userKey, CausalKeys.forFile(it))?.let { cached -> return cached }
        }
        // resolve-identity necesita al menos el fileName (la central resuelve SU
        // identidad a partir de ahí -- no es inferencia del cliente).
        val name = fileName ?: return null
        val response = runCatching {
            syncApi.resolveIdentity(ResolveIdentityRequest(fileName = name, remoteLibraryVideoId = remoteLibraryVideoId))
        }.getOrNull() ?: return null
        val contentId = response.contentId ?: return null
        causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forFile(name), contentId))
        remoteLibraryVideoId?.let {
            causalDao.upsertIdentity(ContentIdentityEntity(userKey, CausalKeys.forRemote(it), contentId))
        }
        response.platformRev?.forEach { (platform, revision) ->
            causalDao.upsertRevision(PlatformRevisionEntity(userKey, contentId, platform, revision))
        }
        return contentId
    }
}
