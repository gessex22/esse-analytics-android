package com.esseanalytics.android.core.media

import android.net.Uri
import java.io.File

// Interfaces puras — no dependen de qué motor de ffmpeg se termine usando.
// Los comandos exactos a replicar (mismo filtro/mismos flags que ya corren en
// producción en desktop) están documentados en el KDoc de cada uno; portados
// de local-backend/src/services/thumbnail.service.ts y video-normalize.service.ts.

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
 * ffmpeg exacto: `-c copy -avoid_negative_ts make_zero -movflags +faststart`,
 * clip clampeado a [1,60]s.
 */
interface TrimProcessor {
    suspend fun trim(input: File, output: File, startSec: Double = 0.0, maxDurationSec: Double = 60.0): Result<Unit>
}

/**
 * Recodifica a un baseline compatible con Meta — SOLO bajo pedido manual del
 * usuario (no automático, ver la nota de "fallback de compatibilidad" en el
 * plan: recodificar en el teléfono es lento/gasta batería).
 *
 * ffmpeg exacto: `libx264 -pix_fmt yuv420p -preset veryfast -crf 21 -maxrate 6M
 * -bufsize 12M -r 30 -vf "scale=min(iw\,1080):min(ih\,1920):force_original_aspect_ratio=decrease:force_divisible_by=2"`,
 * audio `aac -b:a 128k`, `-movflags +faststart`.
 */
interface NormalizeProcessor {
    suspend fun normalize(input: File, output: File): Result<Unit>
}

// MediaProber ya tiene implementación real (AndroidMediaProber.kt, sin
// ffmpeg) y su binding en di/MediaModule.kt. Thumbnail/Trim/Normalize siguen
// pendientes, una vez resuelto qué motor de ffmpeg se usa — ver el comentario
// en build.gradle.kts.
