package com.esseanalytics.android.feature.ingest

import org.junit.Assert.*
import org.junit.Test

class ImportVideoNamingTest {
    @Test fun opaqueNamesBecomeReadableButUserNamesArePreserved() {
        listOf(null, " ", "89A01C02-D4FD-8D1B-8C43-912224229220.mov", "89a01c02d4fd8d1b8c43912224229220.mp4", "video_1792023999123.mp4")
            .forEach { assertTrue("$it", ImportVideoNaming.needsName(it)) }
        listOf("Receta de pasta.mp4", "IMG_1024.mov", "Nuevo video 3.mp4", "20261014.mp4")
            .forEach { assertFalse(ImportVideoNaming.needsName(it)) }
    }

    @Test fun sequenceSkipsExistingLabelsAcrossExtensionsAndCase() {
        val next = ImportVideoNaming.nextName(0, listOf("nuevo VIDEO 1.mov", "Nuevo video 2.mp4"), "mp4")
        assertEquals("Nuevo video 3.mp4", next.fileName)
        assertEquals(3L, next.sequence)
    }

    @Test fun savedSequenceDoesNotReuseRenamedOrDeletedLabels() {
        val first = ImportVideoNaming.nextName(7, emptyList(), "mov")
        val second = ImportVideoNaming.nextName(first.sequence, emptyList(), "mp4")
        assertEquals("Nuevo video 8.mov", first.fileName)
        assertEquals("Nuevo video 9.mp4", second.fileName)
    }
}
