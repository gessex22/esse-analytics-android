package com.esseanalytics.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Invariantes de la identidad local estable (clientFileId). Son las que rompía
// el esquema viejo, que identificaba por fileName.
class ClientFileIdTest {

    private fun videoNamed(name: String) = VideoFile(
        fileName = name,
        filePath = "/videos/$name",
        status = FileStatus.PENDIENTE,
        contentStatus = ContentStatus.BORRADOR,
    )

    @Test
    fun `dos videos con el mismo nombre no comparten identidad`() {
        val a = videoNamed("reel.mp4")
        val b = videoNamed("reel.mp4")

        assertEquals(a.fileName, b.fileName)
        assertNotEquals(a.clientFileId, b.clientFileId)
    }

    @Test
    fun `renombrar un video no cambia su identidad`() {
        val original = videoNamed("reel.mp4")
        val renamed = original.copy(fileName = "reel-final-v2.mp4", updatedAt = Instant.now())

        assertEquals(original.clientFileId, renamed.clientFileId)
    }

    @Test
    fun `mover un video de ruta o linkearlo a la nube no cambia su identidad`() {
        val original = videoNamed("reel.mp4")
        val moved = original.copy(
            filePath = "content://otra/ubicacion",
            remoteLibraryVideoId = "65f0c0ffee",
            id = 42,
        )

        assertEquals(original.clientFileId, moved.clientFileId)
    }

    @Test
    fun `la identidad no se deriva del nombre ni de la ruta ni del id remoto`() {
        val video = videoNamed("reel.mp4").copy(id = 7, remoteLibraryVideoId = "65f0c0ffee")

        assertTrue(video.clientFileId.isNotBlank())
        for (origen in listOf(video.fileName, video.filePath, video.remoteLibraryVideoId!!, video.id.toString())) {
            assertNotEquals(origen, video.clientFileId)
        }
        // Estos tres son lo bastante largos como para que "contenerlos" solo
        // pueda significar que la identidad se armó a partir de ellos (a
        // diferencia del id numérico, que sí puede aparecer por azar dentro de
        // un UUID).
        for (origen in listOf(video.fileName, video.filePath, video.remoteLibraryVideoId!!)) {
            assertFalse("clientFileId no puede contener $origen", video.clientFileId.contains(origen))
        }
    }

    @Test
    fun `newClientFileId genera un UUID distinto por llamada`() {
        val generated = List(500) { newClientFileId() }

        assertEquals(500, generated.toSet().size)
        assertTrue(generated.all { Regex("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$").matches(it) })
    }
}
