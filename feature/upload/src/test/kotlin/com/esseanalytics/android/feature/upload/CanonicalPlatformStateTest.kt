package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.BackupFileDto
import com.esseanalytics.android.core.network.util.CanonicalPlatformState
import com.esseanalytics.android.core.network.util.matchCanonicalPlatformState
import com.esseanalytics.android.core.network.util.withCanonicalPlatformState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPlatformStateTest {
    private val local = VideoFile(
        fileName = "final - oneplus.mp4",
        filePath = "/videos/final - oneplus.mp4",
        status = FileStatus.PENDIENTE,
        contentStatus = ContentStatus.BORRADOR,
        duracionSegundos = 42,
    )

    @Test fun uniqueSameNameAndDurationUsesPcPublicationState() {
        val matched = matchCanonicalPlatformState(
            local,
            listOf(BackupFileDto("final - oneplus.mp4", platforms = listOf("instagram"), duracion_segundos = 42.4)),
        )

        assertEquals(setOf(Platform.INSTAGRAM), matched?.published)
        assertTrue(matched?.discarded.orEmpty().isEmpty())
        assertTrue(Platform.INSTAGRAM in local.withCanonicalPlatformState(matched).platforms)
    }

    @Test fun ambiguousNamesAreNotMatched() {
        val matched = matchCanonicalPlatformState(
            local,
            listOf(
                BackupFileDto("final - oneplus.mp4", platforms = listOf("instagram"), duracion_segundos = 42.0),
                BackupFileDto("FINAL - ONEPLUS.MP4", platforms = listOf("youtube"), duracion_segundos = 42.0),
            ),
        )

        assertNull(matched)
    }

    @Test fun duplicateLocalNamesAreNotMatched() {
        val duplicate = local.copy(id = 2, filePath = "/other/final - oneplus.mp4")
        val matched = matchCanonicalPlatformState(
            local,
            listOf(BackupFileDto("final - oneplus.mp4", platforms = listOf("instagram"), duracion_segundos = 42.0)),
            localFiles = listOf(local, duplicate),
        )

        assertNull(matched)
    }

    @Test fun differentDurationIsNotMatchedAndPublishedWinsOverDiscarded() {
        assertNull(
            matchCanonicalPlatformState(
                local,
                listOf(BackupFileDto("final - oneplus.mp4", platforms = listOf("instagram"), duracion_segundos = 90.0)),
            ),
        )

        val merged = local.copy(platformsDiscarded = listOf(Platform.INSTAGRAM))
            .withCanonicalPlatformState(CanonicalPlatformState(setOf(Platform.INSTAGRAM), emptySet()))
        assertTrue(Platform.INSTAGRAM in merged.platforms)
        assertTrue(Platform.INSTAGRAM !in merged.platformsDiscarded)
    }
}
