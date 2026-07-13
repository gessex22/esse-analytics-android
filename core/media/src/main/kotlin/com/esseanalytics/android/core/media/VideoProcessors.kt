package com.esseanalytics.android.core.media

import android.net.Uri
import java.io.File

// Interfaces puras. Decisión tomada con el usuario: SIN ffmpeg -- libx264
// (el codec que hubiera hecho falta para Normalize) es GPL, y esta es una
// app comercial de código cerrado, no se puede empaquetar. Los comandos
// ffmpeg exactos documentados en el KDoc de cada uno son la referencia de
// comportamiento a igualar (portados de local-backend/src/services
// /thumbnail.service.ts y video-normalize.service.ts), no algo que se vaya a
// ejecutar tal cual -- las implementaciones reales usan el SDK de Android
// (MediaExtractor/MediaMuxer/MediaCodec), ver el comentario al final del archivo.

// Un video que se importa por el selector SAF puede quedarse como Uri
// persistente (ContentUri) en vez de copiarse a storage privado (ver
// ImportUseCase) -- Share Sheet no soporta permisos persistentes, ahí SIEMPRE
// hay LocalFile. MediaProber/ThumbnailGenerator necesitan poder leer los dos
// sin que el resto de la app sepa la diferencia.
sealed interface MediaSource {
    data class LocalFile(val file: File) : MediaSource
    data class ContentUri(val uri: Uri) : MediaSource

    companion object {
        // VideoFile.filePath (core:database) guarda uno de los dos formatos
        // como un solo String -- este es el único lugar que necesita saber
        // distinguirlos por prefijo, todo lo demás ya trabaja con MediaSource.
        fun fromStoredPath(path: String): MediaSource =
            if (path.startsWith("content://")) ContentUri(Uri.parse(path)) else LocalFile(File(path))
    }
}

data class MediaInfo(val durationSeconds: Double?, val width: Int?, val height: Int?)

interface MediaProber {
    suspend fun probe(source: MediaSource): MediaInfo
}

/**
 * Miniatura 320×180 JPG, estilo YT Studio (fondo desenfocado, sin barras de
 * letterbox). Seek a min(3, duration*0.1)s (o 1s si la duración es desconocida).
 *
 * Filtro ffmpeg exacto a replicar:
 * ```
 * [0:v]scale=320:180:force_original_aspect_ratio=increase,crop=320:180,boxblur=20:5[bg];
 * [0:v]scale=320:180:force_original_aspect_ratio=decrease[fg];
 * [bg][fg]overlay=(W-w)/2:(H-h)/2[out]
 * ```
 * `-frames:v 1 -update 1`.
 */
interface ThumbnailGenerator {
    suspend fun generate(input: File, output: File): Result<Unit>
}

/**
 * Recorte a un máximo de 60s, SIN recodificar (stream-copy) — para cuando
 * Instagram/TikTok rechazan un video por duración.
 *
 * ffmpeg equivalente: `-c copy -avoid_negative_ts make_zero -movflags +faststart`,
 * clip clampeado a [1,60]s. Implementado sin ffmpeg vía MediaExtractor/
 * MediaMuxer, ver AndroidTrimProcessor.kt.
 */
interface TrimProcessor {
    suspend fun trim(input: File, output: File, startSec: Double = 0.0, maxDurationSec: Double = 60.0): Result<Unit>
}

/**
 * Recodifica a un baseline compatible con Meta — SOLO bajo pedido manual del
 * usuario (no automático, ver la nota de "fallback de compatibilidad" en el
 * plan: recodificar en el teléfono es lento/gasta batería).
 *
 * ffmpeg equivalente: `libx264 -pix_fmt yuv420p -preset veryfast -crf 21 -maxrate 6M
 * -bufsize 12M -r 30 -vf "scale=min(iw\,1080):min(ih\,1920):force_original_aspect_ratio=decrease:force_divisible_by=2"`,
 * audio `aac -b:a 128k`, `-movflags +faststart`. Sin implementación todavía --
 * necesita un pipeline MediaCodec de decode+encode real (mucho más código que
 * TrimProcessor, que es solo remux), y sin forma de probarlo en este entorno.
 */
interface NormalizeProcessor {
    suspend fun normalize(input: File, output: File): Result<Unit>
}

// MediaProber (AndroidMediaProber.kt) y TrimProcessor (AndroidTrimProcessor.kt)
// ya tienen implementación real, sin ffmpeg -- bindings en di/MediaModule.kt.
// ThumbnailGenerator (la miniatura CON blur de fondo, no confundir con
// AndroidFrameThumbnailGenerator que ya existe y hace un center-crop simple
// sin blur) y NormalizeProcessor siguen pendientes -- no por falta de decidir
// un motor de ffmpeg (ya se decidió no usar ninguno, ver el comentario del
// inicio del archivo), sino porque ambos necesitan más trabajo de verdad:
// un decode+encode con MediaCodec para Normalize, un blur real (RenderEffect
// API 31+ o un box blur a mano para 26-30) para el thumbnail con fondo.
