package com.esseanalytics.android.core.database

import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Política pura de YouTube (sin Room ni red): qué decisión sale de cada
// combinación de fase + campos del journal ANTES de tocar la red. Regla
// general: el único desenlace que habilita POST nuevo es StartFresh, solo
// desde fila inexistente o pending genuinamente limpio; todo lo demás es
// resume, confirmar lo ya confirmado, o bloquear/invalidar — nunca arrancar
// una sesión nueva a partir de un estado ambiguo.
class PublishOperationPolicyTest {

    private fun decide(
        phase: String? = null,
        ytSessionURL: String? = null,
        ytBytesConfirmed: Long = 0,
        ytTotalBytes: Long? = null,
        ytFinalChunkSent: Boolean = false,
        resultPlatformId: String? = null,
        resultURL: String? = null,
        lastError: String? = null,
    ) = PublishOperationPolicy.decide(
        phase = phase,
        ytSessionURL = ytSessionURL,
        ytBytesConfirmed = ytBytesConfirmed,
        ytTotalBytes = ytTotalBytes,
        ytFinalChunkSent = ytFinalChunkSent,
        resultPlatformId = resultPlatformId,
        resultURL = resultURL,
        lastError = lastError,
    )

    @Test
    fun `sin fila o pending limpio arranca de cero`() {
        assertEquals(YouTubePublishDecision.StartFresh, decide(phase = null))
        assertEquals(
            YouTubePublishDecision.StartFresh,
            decide(phase = PublishOperationEntity.PHASE_PENDING),
        )
    }

    @Test
    fun `pending con restos debiles se invalida y no arranca`() {
        val decision = decide(
            phase = PublishOperationEntity.PHASE_PENDING,
            ytSessionURL = "https://upload.googleapis.com/session/1",
            ytTotalBytes = 100,
        )

        assertTrue(decision is YouTubePublishDecision.MarkInvalidated)
    }

    @Test
    fun `pending con evidencia fuerte se bloquea`() {
        // El flag del chunk final pesa aunque la fase diga pending.
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_PENDING,
                ytFinalChunkSent = true,
            ) is YouTubePublishDecision.MarkBlocked,
        )
        // Un resultado previo también.
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_PENDING,
                resultPlatformId = "vid-123",
            ) is YouTubePublishDecision.MarkBlocked,
        )
        // Bytes que ya cubren el total también (el confirmed pudo fallar tras un 2xx real).
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_PENDING,
                ytBytesConfirmed = 100,
                ytTotalBytes = 100,
            ) is YouTubePublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `sessionCreated con URL valida reanuda`() {
        val decision = decide(
            phase = PublishOperationEntity.PHASE_SESSION_CREATED,
            ytSessionURL = "https://upload.googleapis.com/resumable/xyz",
            ytBytesConfirmed = 40,
            ytTotalBytes = 100,
        )

        assertEquals(
            YouTubePublishDecision.Resume(
                sessionUrl = "https://upload.googleapis.com/resumable/xyz",
                bytesConfirmed = 40,
                totalBytes = 100,
                finalChunkSent = false,
            ),
            decision,
        )
    }

    @Test
    fun `sessionCreated con URL invalida se invalida sin evidencia fuerte`() {
        for (url in listOf(null, "", "http://upload.googleapis.com/x", "https://", "https:///path", "/relativa", "file:///tmp/x")) {
            val decision = decide(
                phase = PublishOperationEntity.PHASE_SESSION_CREATED,
                ytSessionURL = url,
            )
            assertTrue("URL '$url' inválida debe invalidar, no reanudar", decision is YouTubePublishDecision.MarkInvalidated)
        }
    }

    @Test
    fun `URL invalida con chunk final mandado se bloquea porque es ambiguo`() {
        val decision = decide(
            phase = PublishOperationEntity.PHASE_FINAL_CHUNK_SENT,
            ytSessionURL = "no-es-url",
        )

        assertTrue(decision is YouTubePublishDecision.MarkBlocked)
    }

    @Test
    fun `finalChunkSent reanuda marcando el final aunque el flag crudo sea falso`() {
        // Evidencia fuerte por bytes completos dentro de una fase sessionCreated.
        val decision = decide(
            phase = PublishOperationEntity.PHASE_SESSION_CREATED,
            ytSessionURL = "https://upload.googleapis.com/resumable/xyz",
            ytBytesConfirmed = 100,
            ytTotalBytes = 100,
        )

        assertTrue(decision is YouTubePublishDecision.Resume)
        assertEquals(true, (decision as YouTubePublishDecision.Resume).finalChunkSent)
    }

    @Test
    fun `confirmed solo vale con resultado demostrable`() {
        val valido = decide(
            phase = PublishOperationEntity.PHASE_CONFIRMED,
            resultPlatformId = "vid-123",
            resultURL = "https://youtu.be/vid-123",
        )
        assertEquals(
            YouTubePublishDecision.AlreadyConfirmed(platformId = "vid-123", url = "https://youtu.be/vid-123"),
            valido,
        )

        assertTrue(
            decide(phase = PublishOperationEntity.PHASE_CONFIRMED) is YouTubePublishDecision.MarkBlocked,
        )
        assertTrue(
            decide(
                phase = PublishOperationEntity.PHASE_CONFIRMED,
                resultPlatformId = "",
                resultURL = "",
            ) is YouTubePublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `blocked y sessionInvalidated no habilitan red`() {
        val blocked = decide(
            phase = PublishOperationEntity.PHASE_BLOCKED,
            lastError = "quedó ambiguo",
        )
        assertEquals(YouTubePublishDecision.AlreadyBlocked("quedó ambiguo"), blocked)

        val invalidated = decide(phase = PublishOperationEntity.PHASE_SESSION_INVALIDATED)
        assertTrue(invalidated is YouTubePublishDecision.AlreadyInvalidated)
    }

    @Test
    fun `sessionInvalidated con evidencia fuerte esta corrupto y se bloquea`() {
        val decision = decide(
            phase = PublishOperationEntity.PHASE_SESSION_INVALIDATED,
            ytFinalChunkSent = true,
        )

        assertTrue(decision is YouTubePublishDecision.MarkBlocked)
    }

    @Test
    fun `fase desconocida nunca se asume arrancar de cero`() {
        assertTrue(decide(phase = "garbage") is YouTubePublishDecision.MarkBlocked)
        // Una fase de Instagram en una fila de YouTube tampoco.
        assertTrue(
            decide(phase = PublishOperationEntity.PHASE_PUBLISH_REQUESTED) is YouTubePublishDecision.MarkBlocked,
        )
    }

    @Test
    fun `validacion de URL https absoluta con host`() {
        assertTrue(PublishOperationPolicy.isValidHttpsUrl("https://upload.googleapis.com/a?b#c"))
        assertTrue(PublishOperationPolicy.isValidHttpsUrl("HTTPS://HOST.com/x"))
        assertTrue(PublishOperationPolicy.isValidHttpsUrl("https://host"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl(null))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl(""))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://?x=1"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("http://upload.googleapis.com/x"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("javascript:alert(1)"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://us er@host/x"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://otro.com/ https://host"))
        // Casos java.net.URI: puerto sin host, host sólo puntos, userinfo y
        // URL relativa no son sesiones resumibles reales.
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://:443"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://."))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("https://user@host/x"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("/relativa/x"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("relativa"))
        assertFalse(PublishOperationPolicy.isValidHttpsUrl("file:///tmp/x"))
    }

    @Test
    fun `bytes que cubren el total cuentan como evidencia fuerte`() {
        assertTrue(
            PublishOperationPolicy.hasStrongPublicationEvidence(
                ytFinalChunkSent = false,
                ytBytesConfirmed = 100,
                ytTotalBytes = 100,
                resultPlatformId = null,
                resultURL = null,
            ),
        )
        assertFalse(
            PublishOperationPolicy.hasStrongPublicationEvidence(
                ytFinalChunkSent = false,
                ytBytesConfirmed = 99,
                ytTotalBytes = 100,
                resultPlatformId = null,
                resultURL = null,
            ),
        )
        // total 0/null no cuenta: sin total declarado no hay "completo" que valga.
        assertFalse(
            PublishOperationPolicy.hasStrongPublicationEvidence(
                ytFinalChunkSent = false,
                ytBytesConfirmed = 0,
                ytTotalBytes = null,
                resultPlatformId = null,
                resultURL = null,
            ),
        )
    }
}
