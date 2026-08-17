package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Envuelve las 3 fuentes que puede mostrar Videos (Parte D/E del plan +
// Biblioteca LAN 2026-08-17): local (VideoFile, id: Long, Room), la cola
// remota con bytes reales (RemoteLibraryVideoDto, _id: String, publicable) y
// un video real de una PC autorizada en la LAN ahora mismo (LocalPcVideoDto,
// reproducible/publicable vía local-backend). No se pueden fusionar en un
// solo data class porque los ids y las acciones disponibles son de tipos
// distintos.
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

    // Reemplaza a BackupCatalog (mirror de metadata sin bytes, gateado por
    // isPremium). FIX 2026-08-17 (mismo criterio que LibraryView.swift en
    // iOS, commit del mismo día): un video real de una PC autorizada y viva
    // en la LAN ahora mismo -- reproducible y publicable, no un mirror de
    // solo lectura. baseUrl viaja EN EL ÍTEM, no en un campo global de la
    // pantalla, porque puede haber más de una PC autorizada al mismo tiempo
    // (ver LanPcDiscoveryStore). Sin PC alcanzable, el chip directamente no
    // existe (ver LibraryViewModel.canSeeLanLibrary) -- no hay fallback a un
    // mirror sin bytes, que antes se veía como un placeholder casi negro.
    data class LanVideo(val video: LocalPcVideoDto, val baseUrl: String) : LibraryListItem {
        override val displayName get() = video.fileName
        override val sortInstant: Instant get() = localPcParsedFechaCreacion(video.fecha_creacion)
    }
}

enum class LibraryFilter { ALL, LOCAL, REMOTE, LAN }

private val SQLITE_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

// TEXT libre de SQLite -- puede llegar como ISO-8601 o como
// "yyyy-MM-dd HH:mm:ss" (formato nativo de datetime() de SQLite), a
// diferencia de BackupFileDto.fecha_creacion (siempre ISO, viene de Mongo).
// Doble intento con fallback a EPOCH, mismo criterio que
// LocalBackendUploadAPI.swift en iOS.
internal fun localPcParsedFechaCreacion(raw: String?): Instant {
    if (raw == null) return Instant.EPOCH
    val iso = runCatching { Instant.parse(raw) }.getOrNull()
    if (iso != null) return iso
    val sqlite = runCatching {
        LocalDateTime.parse(raw, SQLITE_DATETIME_FORMAT).atZone(ZoneId.systemDefault()).toInstant()
    }.getOrNull()
    return sqlite ?: Instant.EPOCH
}
