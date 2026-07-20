package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// Mirror de BackupFileModel (backend/src/models/backup-file.model.ts) -- SOLO
// metadata, la sube el backup automático del escritorio (pushFilesToCloud).
// Sin file_path/url a propósito: no hay forma de reproducir ni publicar esto
// desde el teléfono, el video real solo existe en la PC dueña.
@Serializable
data class BackupFileDto(
    val file_name: String,
    val platforms: List<String> = emptyList(),
    val platforms_discarded: List<String> = emptyList(),
    val content_status: String? = null,
    val tipo_contenido: String? = null,
    val scheduled_date: String? = null,
    // Double, NO Int -- viene del ffprobe del escritorio con decimales reales
    // (ej. 28.329s), a diferencia de duracionSegundos en RemoteLibraryVideoDto
    // (siempre entero, Android lo trunca con .toInt() antes de subir). Un Int
    // acá tira SerializationException apenas aparece el primer decimal --
    // kotlinx decodifica la lista entera de una, así que un solo registro con
    // decimales rompía TODO el catálogo en silencio (ver LibraryViewModel
    // .refreshBackupCatalog(), el runCatching se traga la excepción).
    val duracion_segundos: Double? = null,
    val resolucion: String? = null,
    val formato: String? = null,
    val fecha_creacion: String? = null,
)

@Serializable
data class BackupFilesResponse(
    val files: List<BackupFileDto> = emptyList(),
    val total: Int = 0,
    val video_folder: String? = null,
)
