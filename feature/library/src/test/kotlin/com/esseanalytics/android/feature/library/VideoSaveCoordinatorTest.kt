package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.feature.ingest.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSaveCoordinatorTest {
    @Test
    fun `una descarga bloquea el otro destino hasta terminar`() {
        val coordinator = VideoSaveCoordinator()

        assertTrue(coordinator.begin(VideoSaveTarget.APP))
        assertTrue(coordinator.isRunning())
        assertFalse(coordinator.begin(VideoSaveTarget.GALLERY))

        coordinator.complete(VideoSaveTarget.APP, VideoSaveStatus.Saved)

        assertFalse(coordinator.isRunning())
        assertEquals(VideoSaveStatus.Saved, coordinator.appStatus.value)
        assertTrue(coordinator.begin(VideoSaveTarget.GALLERY))
    }

    @Test
    fun `un fallo permite reintentar pero un destino guardado no se repite`() {
        val coordinator = VideoSaveCoordinator()

        assertTrue(coordinator.begin(VideoSaveTarget.GALLERY))
        coordinator.complete(VideoSaveTarget.GALLERY, VideoSaveStatus.Failed("red"))
        assertTrue(coordinator.begin(VideoSaveTarget.GALLERY))
        coordinator.complete(VideoSaveTarget.GALLERY, VideoSaveStatus.Saved)

        assertFalse(coordinator.begin(VideoSaveTarget.GALLERY))
    }

    @Test
    fun `success y duplicate de importacion cuentan como guardado`() {
        val file = VideoFile(
            fileName = "video.mp4",
            filePath = "video.mp4",
            status = FileStatus.PENDIENTE,
            contentStatus = ContentStatus.BORRADOR,
        )

        assertEquals(VideoSaveStatus.Saved, ImportResult.Success(file).toVideoSaveStatus())
        assertEquals(VideoSaveStatus.Saved, ImportResult.Duplicate(file).toVideoSaveStatus())
        assertEquals(
            VideoSaveStatus.Failed("falló"),
            ImportResult.Error("falló").toVideoSaveStatus(),
        )
    }

    @Test
    fun `deteccion previa marca app guardada e impide duplicarla`() {
        val coordinator = VideoSaveCoordinator()

        coordinator.markSaved(VideoSaveTarget.APP)

        assertEquals(VideoSaveStatus.Saved, coordinator.appStatus.value)
        assertFalse(coordinator.begin(VideoSaveTarget.APP))
    }

    @Test
    fun `cambiar de video reinicia ambos destinos`() {
        val coordinator = VideoSaveCoordinator()
        coordinator.markSaved(VideoSaveTarget.APP)
        assertTrue(coordinator.begin(VideoSaveTarget.GALLERY))

        coordinator.reset()

        assertEquals(VideoSaveStatus.Idle, coordinator.appStatus.value)
        assertEquals(VideoSaveStatus.Idle, coordinator.galleryStatus.value)
        assertFalse(coordinator.isRunning())
    }
}
