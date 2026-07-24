package com.esseanalytics.android.core.database.util

import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.PlatformVideo
import com.esseanalytics.android.core.model.VideoFile
import java.time.Instant

internal fun String.toPlatformSet(): Set<Platform> =
    if (isBlank()) emptySet() else split(",").mapNotNull { Platform.fromApiValue(it.trim()) }.toSet()

internal fun Set<Platform>.toCsv(): String = joinToString(",") { it.apiValue }

fun FileEntity.toDomain() = VideoFile(
    id = id,
    fileName = fileName,
    filePath = filePath,
    status = runCatching { FileStatus.valueOf(status) }.getOrDefault(FileStatus.ERROR),
    contentStatus = runCatching { ContentStatus.valueOf(contentStatus) }.getOrDefault(ContentStatus.BORRADOR),
    platforms = platforms.toPlatformSet().toList(),
    platformsDiscarded = platformsDiscarded.toPlatformSet().toList(),
    duracionSegundos = duracionSegundos,
    resolucion = resolucion,
    formato = formato,
    thumbnailPath = thumbnailPath,
    fechaCreacion = fechaCreacionEpochMs?.let { Instant.ofEpochMilli(it) },
    scheduledDate = scheduledDateEpochMs?.let { Instant.ofEpochMilli(it) },
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    remoteLibraryVideoId = remoteLibraryVideoId,
)

fun VideoFile.toEntity() = FileEntity(
    id = id,
    fileName = fileName,
    filePath = filePath,
    status = status.name,
    contentStatus = contentStatus.name,
    platforms = platforms.toSet().toCsv(),
    platformsDiscarded = platformsDiscarded.toSet().toCsv(),
    duracionSegundos = duracionSegundos,
    resolucion = resolucion,
    formato = formato,
    thumbnailPath = thumbnailPath,
    fechaCreacionEpochMs = fechaCreacion?.toEpochMilli(),
    scheduledDateEpochMs = scheduledDate?.toEpochMilli(),
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
    remoteLibraryVideoId = remoteLibraryVideoId,
)

fun PlatformVideoEntity.toDomain() = PlatformVideo(
    id = id,
    platform = Platform.fromApiValue(platform) ?: Platform.YOUTUBE,
    platformId = platformId,
    platformUrl = platformUrl,
    publishedAt = publishedAtEpochMs?.let { Instant.ofEpochMilli(it) },
    linkedFileId = linkedFileId,
    matchStatus = matchStatus,
    title = title,
    description = description,
    views = views,
    likes = likes,
    comments = comments,
)
