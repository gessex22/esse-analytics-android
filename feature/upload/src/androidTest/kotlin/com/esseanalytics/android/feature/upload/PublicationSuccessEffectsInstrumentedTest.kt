package com.esseanalytics.android.feature.upload

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.esseanalytics.android.core.database.EsseAnalyticsDatabase
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.database.PlatformVideoRepository
import com.esseanalytics.android.core.datastore.SettingsStore
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.HistoryOutbox
import com.esseanalytics.android.core.network.api.SyncApi
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicationSuccessEffectsInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val database = Room.inMemoryDatabaseBuilder(context, EsseAnalyticsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val files = FileRepository(database, database.fileDao())
    private val platformVideos = PlatformVideoRepository(database, database.platformVideoDao())
    private val settings = SettingsStore(context)
    private val effects = PublicationSuccessEffects(
        fileRepository = files,
        platformVideoRepository = platformVideos,
        settingsStore = settings,
        historyOutbox = HistoryOutbox(
            syncApi = Proxy.newProxyInstance(SyncApi::class.java.classLoader, arrayOf(SyncApi::class.java)) { _, method, _ ->
                error("Unauthenticated test must enqueue locally, not call ${method.name}")
            } as SyncApi,
            dao = database.pendingHistoryEventDao(),
            causalDao = database.causalPlatformDao(),
            tokenStore = TokenStore(context).also { it.clear() },
            settingsStore = settings,
        ),
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `local success updates Room link and platform state and durably queues exact history identity`() = runBlocking {
        val local = files.findById(files.insert(VideoFile(
            fileName = "camera-original.mp4",
            filePath = "${context.cacheDir}/camera-original.mp4",
            status = FileStatus.PENDIENTE,
            contentStatus = ContentStatus.BORRADOR,
            remoteLibraryVideoId = "remote-library-5",
        )))!!

        effects.recordLocalSuccess(
            file = local,
            platform = Platform.INSTAGRAM,
            result = UploadResult.Success("ig-123", "https://instagram.example/p/ig-123"),
            title = "Edited reel title",
            operationId = "publish-batch-8",
        )

        assertEquals(Platform.INSTAGRAM, files.findById(local.id)!!.platforms.single())
        val link = platformVideos.findByLinkedFileAndPlatform(local.id, Platform.INSTAGRAM)!!
        assertEquals("ig-123", link.platformId)
        assertEquals("Edited reel title", link.title)
        val event = database.pendingHistoryEventDao().getAll().single()
        assertEquals("instagram", event.platform)
        assertEquals("ig-123", event.platformId)
        assertEquals("camera-original.mp4", event.fileName)
        assertEquals("Edited reel title", event.title)
        assertEquals("remote-library-5", event.remoteLibraryVideoId)
        assertEquals(local.clientFileId, event.clientFileId)
        assertEquals("publish-batch-8", event.operationId)
        assertEquals("blocked", event.state) // no account session: retain locally, never send as anonymous
    }

    @Test
    fun `remote success queues remote identity and keeps filename separate from edited title`() = runBlocking {
        effects.recordRemoteSuccess(
            platform = Platform.TIKTOK,
            platformId = "tt-987",
            platformUrl = "https://tiktok.example/@user/video/987",
            fileName = "source-name.mp4",
            title = "A different title",
            remoteLibraryVideoId = "cloud-987",
        )

        val event = database.pendingHistoryEventDao().getAll().single()
        assertEquals("tiktok", event.platform)
        assertEquals("source-name.mp4", event.fileName)
        assertEquals("A different title", event.title)
        assertEquals("cloud-987", event.remoteLibraryVideoId)
        assertNull(event.clientFileId)
        assertNull(event.operationId)
        assertTrue(event.platformUrl!!.contains("987"))
        assertEquals("blocked", event.state)
    }
}
