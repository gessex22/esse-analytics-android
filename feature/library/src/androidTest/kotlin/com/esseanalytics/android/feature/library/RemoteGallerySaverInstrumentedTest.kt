package com.esseanalytics.android.feature.library

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.esseanalytics.android.core.database.EsseAnalyticsDatabase
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryListResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryUploadResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteGallerySaverInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resolver: ContentResolver get() = context.contentResolver

    private fun video(name: String) = RemoteLibraryVideoDto(
        _id = "remote-fixture",
        fileName = name,
        sizeBytes = 4,
    )

    private fun api(stream: suspend (String) -> ResponseBody) = object : RemoteLibraryApi {
        override suspend fun listVideos(limit: Int, skip: Int): RemoteLibraryListResponse = error("unused")
        override suspend fun uploadThumbnail(id: String, thumbnail: MultipartBody.Part): RemoteLibraryUploadResponse = error("unused")
        override suspend fun streamVideo(id: String): ResponseBody = stream(id)
        override suspend fun updatePlatforms(id: String, body: com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest): RemoteLibraryUploadResponse = error("unused")
        override suspend fun deleteVideo(id: String) = error("unused")
    }

    private fun queryByName(name: String): Cursor? = resolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.IS_PENDING),
        "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
        arrayOf(name),
        null,
    )

    @Test
    fun `success completes MediaStore row and saves the exact stream bytes`() {
        val name = "codex-gallery-${System.nanoTime()}.mp4"
        val bytes = byteArrayOf(1, 7, 3, 9)
        val saver = RemoteGallerySaver(context, api { id ->
            assertEquals("remote-fixture", id)
            bytes.toResponseBody("video/mp4".toMediaType())
        })

        try {
            assertEquals(GallerySaveResult.Saved, kotlinx.coroutines.runBlocking { saver.save(video(name)) })
            val uri = queryByName(name).use { cursor ->
                val found = requireNotNull(cursor)
                assertTrue(found.moveToFirst())
                assertEquals(0, found.getInt(found.getColumnIndexOrThrow(MediaStore.Video.Media.IS_PENDING)))
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(found.getLong(found.getColumnIndexOrThrow(MediaStore.Video.Media._ID)).toString())
                    .build()
            }
            val saved = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
            assertArrayEquals(bytes, saved)
        } finally {
            queryByName(name).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    resolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build(), null, null)
                }
            }
        }
    }

    @Test
    fun `stream failure removes the pending MediaStore row`() {
        val name = "codex-gallery-fail-${System.nanoTime()}.mp4"
        val saver = RemoteGallerySaver(context, api { throw IOException("fixture stream failed") })

        val result = kotlinx.coroutines.runBlocking { saver.save(video(name)) }

        assertTrue(result is GallerySaveResult.Failed)
        queryByName(name).use { cursor -> assertTrue(cursor == null || cursor.count == 0) }
    }

    @Test
    fun `delete removes app owned files and marks the Room row deleted`() {
        val db = Room.inMemoryDatabaseBuilder(context, EsseAnalyticsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = FileRepository(db, db.fileDao())
        val local = File(context.cacheDir, "owned-${System.nanoTime()}.mp4").apply { writeBytes(byteArrayOf(4, 5)) }
        val thumbnail = File(context.cacheDir, "thumb-${System.nanoTime()}.jpg").apply { writeBytes(byteArrayOf(7)) }

        try {
            val id = kotlinx.coroutines.runBlocking {
                repository.insert(
                    VideoFile(
                        fileName = local.name,
                        filePath = local.absolutePath,
                        status = FileStatus.PENDIENTE,
                        contentStatus = ContentStatus.BORRADOR,
                        thumbnailPath = thumbnail.absolutePath,
                    ),
                )
            }
            val persisted = requireNotNull(kotlinx.coroutines.runBlocking { repository.findById(id) })

            kotlinx.coroutines.runBlocking { DeleteVideoUseCase(context, repository).delete(persisted) }

            assertTrue(!local.exists())
            assertTrue(!thumbnail.exists())
            assertEquals(FileStatus.ELIMINADO_DISCO, requireNotNull(kotlinx.coroutines.runBlocking { repository.findById(id) }).status)
        } finally {
            local.delete()
            thumbnail.delete()
            db.close()
        }
    }
}
