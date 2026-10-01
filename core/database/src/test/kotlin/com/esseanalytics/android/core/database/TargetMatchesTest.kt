package com.esseanalytics.android.core.database

import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity.Companion.KIND_TRANSITION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// targetMatches decide si una fila del outbox pertenece a la misma cadena
// causal que el cambio que se está encolando -- o sea, decide qué se
// deduplica/encadena con qué. Cuando emparejaba por fileName, dos videos
// homónimos se pisaban entre sí y un cambio se perdía en el dedup.
class TargetMatchesTest {

    private fun row(
        contentId: String? = null,
        remoteLibraryVideoId: String? = null,
        clientFileId: String? = null,
        fileName: String? = null,
    ) = PlatformTransitionOutboxEntity(
        userKey = "u1",
        kind = KIND_TRANSITION,
        contentId = contentId,
        remoteLibraryVideoId = remoteLibraryVideoId,
        clientFileId = clientFileId,
        fileName = fileName,
        platform = "youtube",
        action = PlatformTransitionOutboxEntity.ACTION_MARK_PUBLISHED,
        operationId = "op-1",
        baseVersion = null,
        platformId = null,
        platformUrl = null,
        title = null,
        publishedAt = null,
    )

    @Test
    fun `dos videos con el mismo nombre no se emparejan entre si`() {
        val encolada = row(clientFileId = "cfid-A", fileName = "reel.mp4")

        assertFalse(
            targetMatches(encolada, contentId = null, remoteLibraryVideoId = null, clientFileId = "cfid-B"),
        )
    }

    @Test
    fun `el mismo archivo se empareja por clientFileId aunque lo hayan renombrado`() {
        val encolada = row(clientFileId = "cfid-A", fileName = "reel.mp4")

        assertTrue(
            targetMatches(encolada, contentId = null, remoteLibraryVideoId = null, clientFileId = "cfid-A"),
        )
    }

    @Test
    fun `empareja por contentId y por remoteLibraryVideoId`() {
        assertTrue(
            targetMatches(row(contentId = "c1"), contentId = "c1", remoteLibraryVideoId = null, clientFileId = null),
        )
        assertTrue(
            targetMatches(row(remoteLibraryVideoId = "r1"), contentId = null, remoteLibraryVideoId = "r1", clientFileId = null),
        )
    }

    @Test
    fun `sin ningun handle de identidad no empareja nada`() {
        val encolada = row(fileName = "reel.mp4")

        assertFalse(targetMatches(encolada, contentId = null, remoteLibraryVideoId = null, clientFileId = null))
        // Ni siquiera contra una fila con el mismo fileName: el nombre no es
        // un handle.
        assertFalse(targetMatches(encolada, contentId = null, remoteLibraryVideoId = null, clientFileId = "cfid-A"))
    }

    @Test
    fun `una fila legacy sin clientFileId no se adopta por nombre`() {
        val legacy = row(fileName = "reel.mp4", clientFileId = null)

        assertFalse(targetMatches(legacy, contentId = null, remoteLibraryVideoId = null, clientFileId = "cfid-A"))
    }
}
