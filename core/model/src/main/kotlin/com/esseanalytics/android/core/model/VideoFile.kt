package com.esseanalytics.android.core.model

import java.time.Instant
import java.util.UUID

// Mirror del "files" de local-backend (local-backend/src/models/file.model.ts /
// db/database.ts) — ver el plan en essenalytics-plan.md para el mapeo exacto.
enum class FileStatus { PENDIENTE, PROCESANDO, ELIMINADO_DISCO, ERROR }

enum class ContentStatus { BORRADOR, PUBLICADO, PROCESANDO, DESCARTADO }

// Identidad local estable de UN archivo, generada UNA sola vez al importarlo y
// nunca más. UUID puro a propósito: NO se deriva del nombre, del path, del id
// remoto ni de un timestamp, porque todos esos cambian (o se repiten) y la
// central identifica el contenido por (deviceId, clientFileId) en
// POST /api/sync/resolve-identity. Dos archivos con el MISMO fileName tienen
// que ser dos identidades distintas, y renombrar un archivo no tiene que
// cambiar la suya.
fun newClientFileId(): String = UUID.randomUUID().toString()

data class VideoFile(
    val id: Long = 0,
    // Ver newClientFileId(): se genera acá una única vez (valor por defecto al
    // construir un VideoFile nuevo en la importación) y de ahí en más viaja
    // intacto por mappers/repositorios/copy(). `id` NO sirve para esto: es el
    // rowid de Room, local a esta base y sin significado para la central.
    val clientFileId: String = newClientFileId(),
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
    val remoteLibraryVideoId: String? = null,
) {
    val isFullyResolved: Boolean
        get() = Platform.publishable.all { it in platforms || it in platformsDiscarded }
}
