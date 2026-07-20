package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.BackupFileDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.time.Instant

// Envuelve las 3 fuentes que puede mostrar Videos (Parte D/E del plan): local
// (VideoFile, id: Long, Room), la cola remota con bytes reales (RemoteLibraryVideoDto,
// _id: String, publicable) y el catálogo de solo lectura del backup automático
// del escritorio (BackupFileDto, sin id propio -- solo metadata, sin bytes, no
// se puede publicar/reproducir desde acá). No se pueden fusionar en un solo
// data class porque los ids y las acciones disponibles son de tipos distintos.
sealed interface LibraryListItem {
    val displayName: String
    val sortInstant: Instant

    data class Local(val file: VideoFile) : LibraryListItem {
        override val displayName get() = file.fileName
        override val sortInstant: Instant get() = file.createdAt
    }

    data class Remote(val video: RemoteLibraryVideoDto) : LibraryListItem {
        override val displayName get() = video.fileName
        // createdAt de Mongo llega como ISO string y puede faltar en registros
        // viejos -- Instant.EPOCH los manda al final de la lista en vez de
        // romper el sort.
        override val sortInstant: Instant
            get() = video.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH
    }

    data class BackupCatalog(val entry: BackupFileDto) : LibraryListItem {
        override val displayName get() = entry.file_name
        override val sortInstant: Instant
            get() = entry.fecha_creacion?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH
    }
}

enum class LibraryFilter { ALL, LOCAL, REMOTE, BACKUP_CATALOG }
