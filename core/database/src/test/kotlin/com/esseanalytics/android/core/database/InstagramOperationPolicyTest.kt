package com.esseanalytics.android.core.database

import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Política pura de Instagram (sin Room ni red): misma postura conservadora que
// YouTube — el único desenlace que crea un contenedor nuevo es StartFresh,
// solo desde fila inexistente o pending sin restos; ResumeContainer no toca la
// red por sí solo (le pasa al uploader un contenedor existente para
// CONSULTARLO); publishRequested es ambiguo para siempre (Meta no reconcilia
// media_publish sin repetirlo) y siempre bloquea.
class InstagramOperationPolicyTest {

    private fun decide(
        phase: String? = null,
        igStage: String? = null,
        igContainerId: String? = null,
        igUploadURI: String? = null,
        igPublishRequested: Boolean = false,
        resultPlatformId: String? = null,
        resultURL: String? = null,
        lastError: String? = null,
    ) = InstagramOperationPolicy.decide(
        phase = phase,
        igStage = igStage,
        igContainerId = igContainerId,
        igUploadURI = igUploadURI,
        igPublishRequested = igPublishRequested,
        resultPlatformId = resultPlatformId,
        resultURL = resultURL,
        lastError = lastError,
    )

    @Test
    fun `sin fila o pending limpio arranca en original`() {
        assertEquals(
            InstagramPublishDecision.StartFresh(InstagramUploadStage.ORIGINAL),
            decide(phase = null),
        )
        // pending + igStage NULL es "todavía no se decidió nada": original.
        assertEquals(
            InstagramPublishDecision.StartFresh(InstagramUploadStage.ORIGINAL),
            decide(phase = PublishOperationEntity.PHASE_PENDING, igStage = null),
        )
    }

    @Test
    fun `pending con stage reconocida inicia esa etapa`() {
        for (stage in InstagramUploadStage.entries) {
            assertEquals(
                InstagramPublishDecision.StartFresh(stage),
                decide(phase = PublishOperationEntity.PHASE_PENDING, igStage = stage.rawValue),
            )
        }
    }

    @Test
    fun `pending con stage cruda desconocida se bloquea en vez de caer a original`() {
        val decision = decide(phase = PublishOperationEntity.PHASE_PENDING, igStage = "garbage")

        assertTrue(decision is InstagramPublishDecision.MarkBlocked)
    }

    @Test
    fun `pending con restos de contenedor publicacion o resultado se bloquea`() {
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_PENDING,
                igContainerId = "c-1",
            ) is InstagramPublishDecision.MarkBlocked,
        )
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_PENDING,
                igPublishRequested = true,
            ) is InstagramPublishDecision.MarkBlocked,
        )
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_PENDING,
                resultPlatformId = "m-1",
            ) is InstagramPublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `containerCreated completo y valido reanuda el contenedor`() {
        val decision = decide(
            phase = PublishOperationEntity.PHASE_CONTAINER_CREATED,
            igStage = "normalized",
            igContainerId = "container-1",
            igUploadURI = "https://rupload.facebook.com/ig-upload/v1/container-1",
        )

        assertEquals(
            InstagramPublishDecision.ResumeContainer(
                stage = InstagramUploadStage.NORMALIZED,
                containerId = "container-1",
                uploadUri = "https://rupload.facebook.com/ig-upload/v1/container-1",
            ),
            decision,
        )
    }

    @Test
    fun `containerCreated con URI invalida se bloquea`() {
        for (uri in listOf(null, "", "http://rupload.facebook.com/x", "https://", "/relativa", "file:///tmp")) {
            val decision = decide(
                phase = PublishOperationEntity.PHASE_CONTAINER_CREATED,
                igStage = "original",
                igContainerId = "c-1",
                igUploadURI = uri,
            )
            assertTrue("URI '$uri' inválida debe bloquear", decision is InstagramPublishDecision.MarkBlocked)
        }
    }

    @Test
    fun `containerCreated con containerId vacio o stage corrupta se bloquea`() {
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_CONTAINER_CREATED,
                igStage = "original",
                igContainerId = "",
                igUploadURI = "https://rupload.facebook.com/x",
            ) is InstagramPublishDecision.MarkBlocked,
        )
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_CONTAINER_CREATED,
                igStage = "garbage",
                igContainerId = "c-1",
                igUploadURI = "https://rupload.facebook.com/x",
            ) is InstagramPublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `containerCreated con evidencia de publicacion pedida se bloquea`() {
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_CONTAINER_CREATED,
                igStage = "original",
                igContainerId = "c-1",
                igUploadURI = "https://rupload.facebook.com/x",
                igPublishRequested = true,
            ) is InstagramPublishDecision.MarkBlocked,
        )
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_CONTAINER_CREATED,
                igStage = "original",
                igContainerId = "c-1",
                igUploadURI = "https://rupload.facebook.com/x",
                resultPlatformId = "m-1",
            ) is InstagramPublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `publishRequested siempre bloquea y nunca reintenta`() {
        assertTrue(
            decide(phase = PublishOperationEntity.PHASE_PUBLISH_REQUESTED) is InstagramPublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `confirmed solo exige platformId la URL del permalink puede ser null`() {
        // El mediaId se persiste ANTES de fetchar el permalink: confirmed con
        // platformId vale aunque todavía no haya URL.
        assertEquals(
            InstagramPublishDecision.AlreadyConfirmed(platformId = "m-1", url = null),
            decide(
                phase = PublishOperationEntity.PHASE_CONFIRMED,
                resultPlatformId = "m-1",
                resultURL = null,
            ),
        )
        assertEquals(
            InstagramPublishDecision.AlreadyConfirmed(platformId = "m-1", url = null),
            decide(
                phase = PublishOperationEntity.PHASE_CONFIRMED,
                resultPlatformId = "m-1",
                resultURL = "",
            ),
        )
        assertEquals(
            InstagramPublishDecision.AlreadyConfirmed(platformId = "m-1", url = "https://instagram.com/p/m-1"),
            decide(
                phase = PublishOperationEntity.PHASE_CONFIRMED,
                resultPlatformId = "m-1",
                resultURL = "https://instagram.com/p/m-1",
            ),
        )
        // Sin platformId no hay confirmed que valga.
        assertTrue(
            decide(phase = PublishOperationEntity.PHASE_CONFIRMED) is InstagramPublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `blocked no habilita red y fase desconocida se bloquea`() {
        assertEquals(
            InstagramPublishDecision.AlreadyBlocked("ya estaba"),
            decide(phase = PublishOperationEntity.PHASE_BLOCKED, lastError = "ya estaba"),
        )
        // Una fase de YouTube en una fila de Instagram no se asume nada.
        assertTrue(
            decide(phase = PublishOperationEntity.PHASE_SESSION_CREATED) is InstagramPublishDecision.MarkBlocked,
        )
        assertTrue(decide(phase = "garbage") is InstagramPublishDecision.MarkBlocked)
    }
}
