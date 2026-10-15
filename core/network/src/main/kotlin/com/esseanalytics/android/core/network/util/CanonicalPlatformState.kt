package com.esseanalytics.android.core.network.util

import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.BackupFileDto

data class CanonicalPlatformState(
    val published: Set<Platform>,
    val discarded: Set<Platform>,
)

/** Matches PC metadata only when its filename identifies one local and one catalog entry. */
fun matchCanonicalPlatformState(
    file: VideoFile,
    catalog: List<BackupFileDto>,
    localFiles: List<VideoFile> = listOf(file),
): CanonicalPlatformState? {
    val localNames = listOfNotNull(file.fileName, file.importSourceName)
        .map(String::trim)
        .filter(String::isNotEmpty)
    val candidates = catalog.filter { remote ->
        localNames.any { it.equals(remote.file_name.trim(), ignoreCase = true) }
    }
    if (candidates.size != 1) return null

    val remote = candidates.single()
    val localMatches = localFiles.filter { local ->
        listOfNotNull(local.fileName, local.importSourceName)
            .any { it.trim().equals(remote.file_name.trim(), ignoreCase = true) }
    }
    if (localMatches.size != 1 || localMatches.single().id != file.id) return null

    val localDuration = file.duracionSegundos?.toDouble()
    val remoteDuration = remote.duracion_segundos
    if (localDuration != null && remoteDuration != null && kotlin.math.abs(localDuration - remoteDuration) > 2.0) {
        return null
    }

    val published = remote.platforms.mapNotNull(Platform::fromApiValue).toSet()
    val discarded = remote.platforms_discarded.mapNotNull(Platform::fromApiValue).toSet() - published
    return CanonicalPlatformState(published, discarded)
}

fun VideoFile.withCanonicalPlatformState(state: CanonicalPlatformState?): VideoFile {
    if (state == null) return this
    val published = (platforms + state.published).distinct()
    val discarded = (platformsDiscarded + state.discarded).distinct().filterNot { it in published }
    return copy(platforms = published, platformsDiscarded = discarded)
}
