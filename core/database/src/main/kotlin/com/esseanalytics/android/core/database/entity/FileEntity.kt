package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Mirror de local-backend/src/models/file.model.ts (SQLite) — ver el plan para
// el mapeo campo por campo. `platforms`/`platformsDiscarded` se guardan como
// CSV de apiValue ("youtube,instagram") vía Converters, no como tabla aparte —
// mismo modelo que el `TEXT` json de SQLite en desktop, más simple en Room.
@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val status: String,            // FileStatus.name
    val contentStatus: String,     // ContentStatus.name
    val platforms: String = "",            // CSV de Platform.apiValue
    val platformsDiscarded: String = "",   // CSV de Platform.apiValue
    val duracionSegundos: Int? = null,
    val resolucion: String? = null,
    val formato: String? = null,
    val thumbnailPath: String? = null,
    val fechaCreacionEpochMs: Long? = null,
    val scheduledDateEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    // Link explícito a RemoteLibraryVideoDto._id cuando este archivo se bajó
    // de Nube para publicar (ver ImportUseCase.importFromRemoteLibrary) --
    // mismo campo que remoteLibraryVideoId en FileEntity.swift (iOS). Sin
    // esto, la dedup contra Nube en Videos → Todos solo podía comparar por
    // fileName, que se rompe fácil (nombres repetidos, doble extensión).
    val remoteLibraryVideoId: String? = null,
)
