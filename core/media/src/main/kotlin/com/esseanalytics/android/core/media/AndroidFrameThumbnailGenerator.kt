package com.esseanalytics.android.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// Miniatura de UN frame, sin ffmpeg -- a diferencia de ThumbnailGenerator
// (VideoProcessors.kt), que documenta el filtro exacto con fondo desenfocado
// estilo YT Studio que YA corre en desktop y necesita ffmpeg de verdad para
// reproducirlo. Esto es deliberadamente más simple (un frame, recortado al
// centro para llenar el aspect ratio pedido, sin blur) -- alcanza para tener
// ALGO en la biblioteca mientras se decide el motor de ffmpeg (ver el
// comentario en build.gradle.kts de este módulo). Cuando esa decisión se
// tome, ImportUseCase puede pasar a usar el ThumbnailGenerator real y esta
// clase queda solo como fallback.
@Singleton
class AndroidFrameThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Frame en un instante puntual, SIN recortar/escalar -- para la portada
    // que el usuario elige a mano al publicar (mismo criterio que desktop:
    // frontend/src/components/YoutubeUploadView.tsx, ThumbnailScrubber, un
    // <video>+<canvas> capturando el frame en el que quedó el scrubber; acá
    // MediaMetadataRetriever hace lo mismo sin necesitar ffmpeg ni un player).
    suspend fun captureFrame(source: MediaSource, atMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            setDataSource(retriever, source)
            retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun generate(source: MediaSource, output: File, targetWidth: Int = 320, targetHeight: Int = 180): Boolean =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                setDataSource(retriever, source)

                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val seekAtUs = (minOf(3_000L, (durationMs * 0.1).toLong()) * 1000)

                val frame = retriever.getFrameAtTime(seekAtUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                    ?: return@withContext false

                val cropped = centerCrop(frame, targetWidth, targetHeight)
                output.parentFile?.mkdirs()
                FileOutputStream(output).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (cropped !== frame) frame.recycle()
                cropped.recycle()
                true
            } catch (e: Exception) {
                // Boundary real: video corrupto, formato no soportado por el
                // decoder del dispositivo, etc. -- no es motivo de bloquear
                // el resto de la importación, solo se queda sin miniatura.
                false
            } finally {
                retriever.release()
            }
        }

    private fun setDataSource(retriever: MediaMetadataRetriever, source: MediaSource) {
        when (source) {
            is MediaSource.LocalFile -> retriever.setDataSource(source.file.absolutePath)
            is MediaSource.ContentUri -> retriever.setDataSource(context, source.uri)
        }
    }

    // Llena el aspect ratio pedido recortando el sobrante (sin barras de
    // letterbox, sin deformar) -- versión simple de lo que hace desktop con
    // scale+crop antes de aplicarle el blur de fondo.
    private fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val targetRatio = targetWidth.toFloat() / targetHeight
        val sourceRatio = source.width.toFloat() / source.height

        val (cropWidth, cropHeight) = if (sourceRatio > targetRatio) {
            (source.height * targetRatio).toInt() to source.height
        } else {
            source.width to (source.width / targetRatio).toInt()
        }
        val x = (source.width - cropWidth) / 2
        val y = (source.height - cropHeight) / 2

        val cropped = Bitmap.createBitmap(source, x, y, cropWidth, cropHeight)
        if (cropped.width == targetWidth && cropped.height == targetHeight) return cropped

        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        // createBitmap/createScaledBitmap devuelven la MISMA instancia de
        // origen cuando no hace falta transformar nada -- solo reciclar el
        // intermedio si de verdad se creó una copia nueva.
        if (scaled !== cropped && cropped !== source) cropped.recycle()
        return scaled
    }
}
