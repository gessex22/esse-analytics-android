package com.esseanalytics.android.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Duración/resolución no necesitan ffmpeg -- MediaMetadataRetriever es parte
// del SDK de Android y alcanza para esto (a diferencia de la miniatura con
// blur/recorte/normalización, que sí necesitan un ffmpeg de verdad, ver
// VideoProcessors.kt y el comentario en build.gradle.kts).
@Singleton
class AndroidMediaProber @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaProber {
    override suspend fun probe(source: MediaSource): MediaInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            when (source) {
                is MediaSource.LocalFile -> retriever.setDataSource(source.file.absolutePath)
                is MediaSource.ContentUri -> retriever.setDataSource(context, source.uri)
            }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            MediaInfo(
                durationSeconds = durationMs?.let { it / 1000.0 },
                width = width,
                height = height,
            )
        } finally {
            retriever.release()
        }
    }
}
