package com.esseanalytics.android.feature.ingest

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.esseanalytics.android.core.database.EsseAnalyticsDatabase
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.MediaInfo
import com.esseanalytics.android.core.media.MediaProber
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.network.CatalogRegistrationOutbox
import com.esseanalytics.android.core.network.DeviceIdProvider
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.dto.RemoteLibraryListResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryUploadResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.dto.UpdateRemoteLibraryPlatformsRequest
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportUseCaseInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val database = Room.inMemoryDatabaseBuilder(context, EsseAnalyticsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = FileRepository(database, database.fileDao())

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun createUseCase(api: RemoteLibraryApi) = ImportUseCase(
        context = context,
        fileRepository = repository,
        mediaProber = object : MediaProber {
            override suspend fun probe(source: MediaSource) = MediaInfo(null, null, null)
        },
        thumbnailGenerator = AndroidFrameThumbnailGenerator(context),
        settingsStore = SettingsStore(context),
        remoteLibraryApi = api,
        catalogRegistrationOutbox = CatalogRegistrationOutbox(
            database = database,
            api = Proxy.newProxyInstance(SyncApi::class.java.classLoader, arrayOf(SyncApi::class.java)) { _, method, _ ->
                error("Unexpected central call in unauthenticated import test: ${method.name}")
            } as SyncApi,
            dao = database.pendingCatalogRegistrationDao(),
            displayNameDao = database.pendingCatalogNameDao(),
            causalDao = database.causalPlatformDao(),
            fileDao = database.fileDao(),
            tokenStore = TokenStore(context).also { it.clear() },
            settingsStore = SettingsStore(context),
            deviceIdProvider = object : DeviceIdProvider {
                override suspend fun deviceId() = "instrumented-device"
            },
            context = context,
        ),
        nameAllocator = ImportVideoNameAllocator(context, repository),
    )

    private fun video(id: String) = RemoteLibraryVideoDto(
        _id = id,
        fileName = "remote-$id.mp4",
        sizeBytes = 6,
    )

    private fun api(stream: suspend (String) -> ResponseBody) = object : RemoteLibraryApi {
        override suspend fun listVideos(limit: Int, skip: Int): RemoteLibraryListResponse = error("unused")
        override suspend fun uploadThumbnail(id: String, thumbnail: MultipartBody.Part): RemoteLibraryUploadResponse = error("unused")
        override suspend fun streamVideo(id: String): ResponseBody = stream(id)
        override suspend fun updatePlatforms(id: String, body: UpdateRemoteLibraryPlatformsRequest): RemoteLibraryUploadResponse = error("unused")
        override suspend fun deleteVideo(id: String) = error("unused")
    }

    @Test
    fun `remote import commits bytes and reuses the Room identity on a second request`() = runBlocking {
        val bytes = byteArrayOf(11, 12, 13, 14, 15, 16)
        val downloads = AtomicInteger()
        val useCase = createUseCase(api {
            downloads.incrementAndGet()
            bytes.toResponseBody("video/mp4".toMediaType())
        })

        val first = useCase.importFromRemoteLibrary(video("cloud-once")) as ImportResult.Success
        try {
            val second = useCase.importFromRemoteLibrary(video("cloud-once")) as ImportResult.Success

            assertEquals(1, downloads.get())
            assertEquals(first.file.id, second.file.id)
            assertEquals(first.file.clientFileId, second.file.clientFileId)
            assertEquals("cloud-once", second.file.remoteLibraryVideoId)
            assertTrue(File(first.file.filePath).isFile)
            assertTrue(File(first.file.filePath).readBytes().contentEquals(bytes))
            assertEquals(1, repository.observeCount().let { it.first() })
        } finally {
            File(first.file.filePath).delete()
        }
    }

    @Test
    fun `remote import removes a partially written file when stream fails`() = runBlocking {
        val before = File(context.filesDir, "videos").listFiles()?.toSet().orEmpty()
        val body = object : ResponseBody() {
            override fun contentType() = "video/mp4".toMediaType()
            override fun contentLength() = 6L
            override fun source() = object : ForwardingSource(Buffer().write(byteArrayOf(1, 2, 3))) {
                private var firstRead = true
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (firstRead) {
                        firstRead = false
                        return super.read(sink, byteCount)
                    }
                    throw IOException("stream interrupted")
                }
            }.buffer()
        }
        val useCase = createUseCase(api { body })

        val result = useCase.importFromRemoteLibrary(video("cloud-interrupted"))

        assertTrue(result is ImportResult.Error)
        assertEquals(before, File(context.filesDir, "videos").listFiles()?.toSet().orEmpty())
        assertEquals(0, repository.observeCount().let { it.first() })
    }

    @Test
    fun `remote import propagates cancellation and leaves no partially committed row`() = runBlocking {
        val before = File(context.filesDir, "videos").listFiles()?.toSet().orEmpty()
        val useCase = createUseCase(api { throw CancellationException("test cancelled") })

        val thrown = runCatching { useCase.importFromRemoteLibrary(video("cloud-cancelled")) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(before, File(context.filesDir, "videos").listFiles()?.toSet().orEmpty())
        assertEquals(0, repository.observeCount().let { it.first() })
    }
}
