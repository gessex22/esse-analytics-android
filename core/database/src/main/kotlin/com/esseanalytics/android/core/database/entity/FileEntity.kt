package com.esseanalytics.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Mirror de local-backend/src/models/file.model.ts (SQLite) — ver el plan para
// el mapeo campo por campo. `platforms`/`platformsDiscarded` se guardan como
// CSV de apiValue ("youtube,instagram") vía Converters, no como tabla aparte —
// mismo modelo que el `TEXT` json de SQLite en desktop, más simple en Room.
//
// El índice ÚNICO sobre clientFileId es parte del contrato de identidad: dos
// filas nunca pueden compartirlo, ni siquiera si comparten fileName (ver
// newClientFileId en core:model y MIGRATION_6_7).
@Entity(
    tableName = "files",
    indices = [Index(value = ["clientFileId"], unique = true)],
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Identidad local estable que viaja a la central en resolve-identity junto
    // con el deviceId. El único lugar que la puede fabricar es
    // newClientFileId() al construir el VideoFile de dominio (importación);
    // acá siempre se propaga la que ya existe, así un update nunca la puede
    // "reiniciar" por olvido (el constructor la exige, no tiene default Kotlin).
    //
    // defaultValue = "''" (literal SQL crudo: dos comillas simples) NO es una
    // invitación a insertar vacío: Room siempre bindea el valor explícito en
    // el INSERT, así que el default jamás se aplica en runtime. Existe para que
    // el CREATE TABLE de una instalación NUEVA sea idéntico al ALTER TABLE de
    // MIGRATION_6_7 (SQLite exige un default al agregar una columna NOT NULL a
    // una tabla con filas): si la entidad no lo declarara, Room validaría el
    // schema de las bases migradas contra uno distinto al de las bases nuevas y
    // rechazaría la migración por diferencia de default. Migración y entidad
    // declaran el MISMO default a propósito.
    @ColumnInfo(defaultValue = "''")
    val clientFileId: String,
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
