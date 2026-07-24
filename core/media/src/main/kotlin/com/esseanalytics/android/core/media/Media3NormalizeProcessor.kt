package com.esseanalytics.android.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Recodifica a un baseline compatible con Meta -- vía Media3 Transformer
// (androidx.media3, Google, licencia Apache), NO ffmpeg/libx264 (ver el
// comentario de licencia al inicio de VideoProcessors.kt). Mismos límites que
// normalizeForMeta en desktop (local-backend/src/services/video-normalize.service.ts):
// cap 1080x1920 (sin upscalear, preservando aspecto), H.264/AAC, ~6 Mbps de
// video. Transformer no expone un CRF directo (los encoders de hardware de
// Android trabajan por bitrate objetivo, no por CRF), así que el control de
// calidad acá es el cap de bitrate en vez del crf 21 + maxrate/bufsize exactos
// de ffmpeg -- mismo espíritu (evitar archivos pesados que Meta rechace), sin
// pretender ser un port 1:1 de los flags.
@Singleton
class Media3NormalizeProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaProber: MediaProber,
) : NormalizeProcessor {

    override suspend fun normalize(input: File, output: File): Result<Unit> = runCatching {
        val info = mediaProber.probe(MediaSource.LocalFile(input))
        val target = scaledDimensions(info.width, info.height)

        // Transformer exige un Looper (arranca/reporta por Listener) -- tiene
        // que crearse y usarse desde un hilo con uno, el principal es el más
        // simple sin tener que armar un HandlerThread propio para esto.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(MAX_VIDEO_BITRATE)
                            .build(),
                    )
                    .build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    .build()

                val effects = mutableListOf<Effect>()
                if (target != null) {
                    effects += Presentation.createForWidthAndHeight(target.first, target.second, Presentation.LAYOUT_SCALE_TO_FIT)
                }

                val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setEffects(Effects(emptyList(), effects))
                    .build()

                output.parentFile?.mkdirs()
                transformer.start(editedMediaItem, output.absolutePath)

                continuation.invokeOnCancellation { transformer.cancel() }
            }
        }
    }

    // Mismo criterio que scale=min(iw,MAX_WIDTH):min(ih,MAX_HEIGHT):force_original_aspect_ratio=decrease
    // en desktop -- reescala SOLO si excede el cap (nunca agranda), preservando
    // el aspect ratio original. null si ya entra tal cual (no hace falta el
    // efecto Presentation). Dimensiones pares -- varios encoders de hardware
    // rechazan impares.
    private fun scaledDimensions(width: Int?, height: Int?): Pair<Int, Int>? {
        if (width == null || height == null || width <= 0 || height <= 0) return null
        val scale = minOf(1f, MAX_WIDTH.toFloat() / width, MAX_HEIGHT.toFloat() / height)
        if (scale >= 1f) return null
        val targetWidth = (width * scale).toInt().let { it - (it % 2) }
        val targetHeight = (height * scale).toInt().let { it - (it % 2) }
        return targetWidth to targetHeight
    }

    private companion object {
        const val MAX_WIDTH = 1080
        const val MAX_HEIGHT = 1920
        const val MAX_VIDEO_BITRATE = 6_000_000 // 6 Mbps, mismo cap que -maxrate 6M en desktop
    }
}
