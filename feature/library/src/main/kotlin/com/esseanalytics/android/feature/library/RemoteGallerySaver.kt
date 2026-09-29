package com.esseanalytics.android.feature.library

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteGallerySaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteLibraryApi: RemoteLibraryApi,
) {
    internal suspend fun save(video: RemoteLibraryVideoDto): GallerySaveResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val extension = video.fileName.substringAfterLast('.', "mp4").lowercase()
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, video.fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeTypeFor(extension))
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EsseAnalytics")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext GallerySaveResult.Failed(
            "No se pudo crear el archivo en la galería.",
        )

        try {
            remoteLibraryApi.streamVideo(video._id).byteStream().use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: error("No se pudo escribir el video.")
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) {
                "No se pudo finalizar el video en la galería."
            }
            GallerySaveResult.Saved
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            if (error is CancellationException) throw error
            GallerySaveResult.Failed(error.message ?: "No se pudo guardar el video en la galería.")
        }
    }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        else -> "video/${extension.ifBlank { "mp4" }}"
    }
}
