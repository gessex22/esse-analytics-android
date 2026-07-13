package com.esseanalytics.android.core.model

import java.time.Instant

// Mirror del "files" de local-backend (local-backend/src/models/file.model.ts /
// db/database.ts) — ver el plan en essenalytics-plan.md para el mapeo exacto.
enum class FileStatus { PENDIENTE, PROCESANDO, ELIMINADO_DISCO, ERROR }

enum class ContentStatus { BORRADOR, PUBLICADO, PROCESANDO, DESCARTADO }

data class VideoFile(
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val status: FileStatus,
    val contentStatus: ContentStatus,
    val platforms: List<Platform> = emptyList(),
    val platformsDiscarded: List<Platform> = emptyList(),
    val duracionSegundos: Int? = null,
    val resolucion: String? = null,
    val formato: String? = null,
    val thumbnailPath: String? = null,
    val fechaCreacion: Instant? = null,
    val scheduledDate: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    val isFullyResolved: Boolean
        get() = Platform.publishable.all { it in platforms || it in platformsDiscarded }
}
