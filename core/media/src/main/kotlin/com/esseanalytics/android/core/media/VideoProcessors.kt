package com.esseanalytics.android.core.media

import java.io.File

// Interfaces puras — no dependen de qué motor de ffmpeg se termine usando.
// Los comandos exactos a replicar (mismo filtro/mismos flags que ya corren en
// producción en desktop) están documentados en el KDoc de cada uno; portados
// de local-backend/src/services/thumbnail.service.ts y video-normalize.service.ts.

data class MediaInfo(val durationSeconds: Double?, val width: Int?, val height: Int?)

interface MediaProber {
    suspend fun probe(input: File): MediaInfo
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

// Implementaciones + binding de Hilt: pendientes de Fase 1, una vez resuelto
// qué motor de ffmpeg se usa — ver el comentario en build.gradle.kts.
