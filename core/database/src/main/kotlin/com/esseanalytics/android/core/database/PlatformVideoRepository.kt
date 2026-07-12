package com.esseanalytics.android.core.database

import com.esseanalytics.android.core.database.dao.PlatformVideoDao
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity
import com.esseanalytics.android.core.database.util.toDomain
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.PlatformVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Puerto liviano de local-backend/src/db/platform-video.repo.ts. CRUD básico
// para Fase 0/1 — el matching cruzado completo (cross-match, review) es
// Fase 2, se amplía acá cuando llegue esa parte.
@Singleton
class PlatformVideoRepository @Inject constructor(
    private val dao: PlatformVideoDao,
) {
    fun observeByFile(fileId: Long): Flow<List<PlatformVideo>> =
        dao.findByLinkedFile(fileId).map { list -> list.map { it.toDomain() } }

    suspend fun findByPlatformAndId(platform: Platform, platformId: String): PlatformVideo? =
        dao.findByPlatformAndId(platform.apiValue, platformId)?.toDomain()

    suspend fun upsertPublished(
        platform: Platform,
        platformId: String,
        platformUrl: String?,
        linkedFileId: Long,
        title: String? = null,
    ) {
        dao.upsert(
            PlatformVideoEntity(
                platform = platform.apiValue,
                platformId = platformId,
                platformUrl = platformUrl,
                publishedAtEpochMs = System.currentTimeMillis(),
                linkedFileId = linkedFileId,
                matchStatus = "manual",
                title = title,
            ),
        )
    }
}
