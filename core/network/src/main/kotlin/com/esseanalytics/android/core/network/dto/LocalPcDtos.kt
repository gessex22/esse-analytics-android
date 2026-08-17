package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// Mirror de la fila que devuelve GET /api/videos en local-backend
// (local-backend/src/controllers/video.controller.ts) -- a diferencia de
// BackupFileDto (mirror de metadata SIN bytes, servido por la central desde
// el backup automático), esto sale DIRECTO de la PC por LAN (ver
// docs/lan-library-auto-switch-design-2026-08-16.md, sección 2). Solo
// file_id.file_name está exclusivamente anidado -- duracion_segundos/
// resolucion/formato/fecha_creacion vienen duplicados top-level y adentro de
// file_id, se leen del top-level acá (no hace falta el nestedContainer que
// necesitó iOS con Codable).
@Serializable
data class LocalPcFileIdDto(
    val file_name: String,
)

@Serializable
data class LocalPcVideoDto(
    val _id: String,
    val file_id: LocalPcFileIdDto,
    val platforms: List<String> = emptyList(),
    val platforms_discarded: List<String> = emptyList(),
    // Double, NO Int -- mismo motivo que BackupFileDto.duracion_segundos: sale
    // del ffprobe de la PC con decimales reales (ej. 28.329s). Un Int acá
    // rompería TODO el catálogo en silencio apenas apareciera el primer
    // registro con decimales (kotlinx decodifica la lista entera de una).
    val duracion_segundos: Double? = null,
    val resolucion: String? = null,
    val formato: String? = null,
    // TEXT libre de SQLite -- puede llegar como ISO-8601 o como
    // "yyyy-MM-dd HH:mm:ss" (formato nativo de datetime() de SQLite), a
    // diferencia de BackupFileDto.fecha_creacion (siempre ISO, viene de
    // Mongo). Ver localPcParsedFechaCreacion en LibraryListItem.
    val fecha_creacion: String? = null,
) {
    val fileName: String get() = file_id.file_name
}

@Serializable
data class LocalPcVideosResponse(
    val results: List<LocalPcVideoDto> = emptyList(),
)

// Bodies de POST /api/{youtube,instagram,tiktok}/upload contra local-backend
// -- server-side y sin bytes (la PC lee su propio archivo del disco y lo
// sube ella misma; el celular solo manda metadata), mismo contrato que ya
// usa LocalBackendUploadAPI.swift en iOS. fileId es el mismo _id que trae
// LocalPcVideoDto.
@Serializable
data class LocalPcYoutubeUploadRequest(
    val fileId: String,
    val title: String,
    val description: String = "",
    val privacyStatus: String = "public",
)

@Serializable
data class LocalPcInstagramUploadRequest(
    val fileId: String,
    val caption: String = "",
    val crossPostFacebook: Boolean = false,
)

@Serializable
data class LocalPcTiktokUploadRequest(
    val fileId: String,
    val title: String = "",
    // "PUBLIC_TO_EVERYONE" | "SELF_ONLY" -- mismo mapeo de 2 valores que ya
    // usa TiktokUploader.mapPrivacyLevel en feature:upload (metadata.privacyStatus
    // "public" -> PUBLIC_TO_EVERYONE, cualquier otro valor -> SELF_ONLY).
    val privacyLevel: String,
)
