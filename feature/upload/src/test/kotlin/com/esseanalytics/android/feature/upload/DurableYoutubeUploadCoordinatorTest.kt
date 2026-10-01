package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.database.PublishOperationStore
import com.esseanalytics.android.core.database.dao.PublishOperationDao
import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Coordinador durable sobre DAO y transporte fake, sin socket ni Android: el
// DAO en memoria replica find/insert(ABORT por clave única)/update del store y
// el transporte encola desenlaces y graba cada llamada en un evento
// compartido con el DAO ("db:<fase>" por update, "create"/"query"/
// "upload:<offset>" por llamada). Así se verifica el orden exacto
// journal-antes-que-red de cada camino y que los desenlaces Already*/Mark*
// resuelven con cero token y cero transporte.
class DurableYoutubeUploadCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakePublishOperationDao(
        private val events: MutableList<String>,
    ) : PublishOperationDao {
        val rows = mutableListOf<PublishOperationEntity>()
        var insertCalls = 0
        var updateCalls = 0

        // Excepciones crudas que el store no envuelve (p. ej. una
        // SQLiteException/IllegalStateException de Room): el coordinador las
        // tiene que clasificar como Failure no reintentable igual que las
        // PublishOperationStoreException del dominio.
        var crashOnFind: Exception? = null
        var crashOnUpdate: Exception? = null

        override suspend fun find(userKey: String, sourceId: String, platform: String): PublishOperationEntity? {
            crashOnFind?.let { throw it }
            return rows.firstOrNull { it.userKey == userKey && it.sourceId == sourceId && it.platform == platform }
        }

        override suspend fun insert(entity: PublishOperationEntity): Long {
            insertCalls++
            // Réplica del índice único + ABORT: la clave durable nunca se pisa.
            if (rows.any { it.userKey == entity.userKey && it.sourceId == entity.sourceId && it.platform == entity.platform }) {
                throw IllegalStateException("UNIQUE constraint failed: publish_operations.userKey,sourceId,platform")
            }
            val stored = entity.copy(id = rows.size + 1L)
            rows += stored
            return stored.id
        }

        override suspend fun update(entity: PublishOperationEntity) {
            crashOnUpdate?.let { throw it }
            updateCalls++
            events += "db:${entity.phase}"
            val index = rows.indexOfFirst { it.id == entity.id }
            if (index < 0) throw IllegalStateException("fila ${entity.id} no existe")
            rows[index] = entity
        }
    }

    private class FakeTransport(
        private val events: MutableList<String>,
    ) : YoutubeResumableTransport {
        val calls = mutableListOf<String>()
        val createResults = ArrayDeque<YoutubeResumableResult>()
        val queryResults = ArrayDeque<YoutubeResumableResult>()
        val uploadResults = ArrayDeque<YoutubeResumableResult>()

        override fun createSession(token: String, metadata: UploadMetadata, totalBytes: Long): YoutubeResumableResult {
            calls += "create"
            events += "create"
            return createResults.removeFirst()
        }

        override fun querySession(sessionUrl: String, totalBytes: Long): YoutubeResumableResult {
            calls += "query"
            events += "query"
            return queryResults.removeFirst()
        }

        override fun uploadFromOffset(
            sessionUrl: String,
            file: File,
            offsetBytes: Long,
            totalBytes: Long,
            onProgress: (Float) -> Unit,
        ): YoutubeResumableResult {
            calls += "upload:$offsetBytes"
            events += "upload:$offsetBytes"
            return uploadResults.removeFirst()
        }
    }

    private class Fixture {
        val events = mutableListOf<String>()
        val dao = FakePublishOperationDao(events)
        val transport = FakeTransport(events)
        val store = PublishOperationStore(dao)
        val coordinator = DurableYoutubeUploadCoordinator(store, transport)
        var tokens = 0

        val tokenProvider: suspend () -> String = {
            tokens++
            "tok-1"
        }
    }

    private val metadata = UploadMetadata(title = "Video de prueba")

    private var fileCounter = 0

    private fun file(bytes: Int): File =
        tempFolder.newFile("video-${fileCounter++}.mp4").apply { writeBytes(ByteArray(bytes)) }

    private suspend fun seedSession(
        f: Fixture,
        phase: String,
        bytesConfirmed: Long,
        totalBytes: Long,
        finalChunkSent: Boolean,
    ) {
        f.store.getOrCreate("u1", "cfid-A", "youtube", "op-1")
        f.dao.rows[0] = f.dao.rows[0].copy(
            phase = phase,
            ytSessionURL = SESSION_URL,
            ytBytesConfirmed = bytesConfirmed,
            ytTotalBytes = totalBytes,
            ytFinalChunkSent = finalChunkSent,
        )
    }

    // --- 1: inicio limpio ------------------------------------------------------

    @Test
    fun `inicio limpio crea sesion y persiste cada checkpoint durable antes que la red`() = runTest {
        val f = Fixture()
        f.transport.createResults += YoutubeResumableResult.Created(SESSION_URL)
        f.transport.uploadResults += YoutubeResumableResult.Completed("vid-1")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)

        assertEquals(
            UploadResult.Success("vid-1", "https://youtube.com/shorts/vid-1", durableOperationId = "op-1"),
            r,
        )
        assertEquals(1, f.tokens)
        // Orden exacto: create, SessionCreated durable, FinalChunkSent
        // durable, upload desde 0, Confirmed durable.
        assertEquals(
            listOf("create", "db:sessionCreated", "db:finalChunkSent", "upload:0", "db:confirmed"),
            f.events,
        )
        assertEquals(listOf("create", "upload:0"), f.transport.calls)
        val row = f.dao.rows.single()
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, row.phase)
        assertEquals("vid-1", row.resultPlatformId)
        assertEquals("https://youtube.com/shorts/vid-1", row.resultURL)
        assertEquals(SESSION_URL, row.ytSessionURL)
        assertEquals(100L, row.ytTotalBytes)
    }

    // --- 2: desenlaces asentados e identidad vacía: cero token/transporte -------

    @Test
    fun `confirmed blocked invalidated e identidad vacia resuelven con cero token y cero transporte`() = runTest {
        // AlreadyConfirmed: devuelve el éxito guardado.
        val confirmada = Fixture()
        confirmada.store.getOrCreate("u1", "cfid-A", "youtube", "op-1")
        confirmada.dao.rows[0] = confirmada.dao.rows[0].copy(
            phase = PublishOperationEntity.PHASE_CONFIRMED,
            resultPlatformId = "vid-previo",
            resultURL = "https://youtube.com/shorts/vid-previo",
        )
        val r1 = confirmada.coordinator.upload(file(10), metadata, "u1", "cfid-A", "op-1", confirmada.tokenProvider)
        assertEquals(
            UploadResult.Success("vid-previo", "https://youtube.com/shorts/vid-previo", durableOperationId = "op-1"),
            r1,
        )
        assertEquals(0, confirmada.tokens)
        assertTrue(confirmada.transport.calls.isEmpty())
        assertEquals(0, confirmada.dao.updateCalls)

        // AlreadyBlocked: falla no reintentable con el error guardado.
        val bloqueada = Fixture()
        bloqueada.store.getOrCreate("u1", "cfid-A", "youtube", "op-1")
        bloqueada.dao.rows[0] = bloqueada.dao.rows[0].copy(
            phase = PublishOperationEntity.PHASE_BLOCKED,
            lastError = "desenlace remoto sin confirmar",
        )
        val r2 = bloqueada.coordinator.upload(file(10), metadata, "u1", "cfid-A", "op-1", bloqueada.tokenProvider)
        assertEquals(UploadResult.Failure("desenlace remoto sin confirmar", retryable = false), r2)
        assertEquals(0, bloqueada.tokens)
        assertTrue(bloqueada.transport.calls.isEmpty())

        // AlreadyInvalidated: falla no reintentable, sin red.
        val invalidada = Fixture()
        invalidada.store.getOrCreate("u1", "cfid-A", "youtube", "op-1")
        invalidada.dao.rows[0] = invalidada.dao.rows[0].copy(
            phase = PublishOperationEntity.PHASE_SESSION_INVALIDATED,
            lastError = "410 Gone de una sesión vieja",
        )
        val r3 = invalidada.coordinator.upload(file(10), metadata, "u1", "cfid-A", "op-1", invalidada.tokenProvider)
        assertEquals(UploadResult.Failure("410 Gone de una sesión vieja", retryable = false), r3)
        assertEquals(0, invalidada.tokens)
        assertTrue(invalidada.transport.calls.isEmpty())

        // Identidad vacía: se rechaza antes de journal y red.
        val vacia = Fixture()
        try {
            vacia.coordinator.upload(file(10), metadata, "", "cfid-A", "op-1", vacia.tokenProvider)
            fail("userKey vacío no debe aplicar")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
        try {
            vacia.coordinator.upload(file(10), metadata, "u1", "cfid-A", "  ", vacia.tokenProvider)
            fail("operationId vacío no debe aplicar")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
        try {
            vacia.coordinator.upload(file(0), metadata, "u1", "cfid-A", "op-1", vacia.tokenProvider)
            fail("archivo vacío no debe aplicar")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
        assertEquals(0, vacia.tokens)
        assertTrue(vacia.transport.calls.isEmpty())
        assertEquals(0, vacia.dao.rows.size)
    }

    // --- 3: resume con 308 ------------------------------------------------------

    @Test
    fun `resume con 308 consulta primero y el upload recibe exactamente ese offset`() = runTest {
        val f = Fixture()
        seedSession(f, PublishOperationEntity.PHASE_SESSION_CREATED, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = false)

        f.transport.queryResults += YoutubeResumableResult.Incomplete(40)
        f.transport.uploadResults += YoutubeResumableResult.Completed("vid-3")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)

        assertEquals(
            UploadResult.Success("vid-3", "https://youtube.com/shorts/vid-3", durableOperationId = "op-1"),
            r,
        )
        // Siempre query primero y el upload arranca EXACTAMENTE en el offset
        // confirmado por el 308 — sin createSession ni token.
        assertEquals(listOf("query", "upload:40"), f.transport.calls)
        assertEquals(0, f.tokens)
        // 40 == journal: Progress solo se escribe si avanza — acá no.
        assertEquals(listOf("query", "db:finalChunkSent", "upload:40", "db:confirmed"), f.events)
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, f.dao.rows.single().phase)
    }

    // --- 4: respuesta perdida tras el final --------------------------------------

    @Test
    fun `respuesta perdida tras el final vuelve a consultar y confirma sin create ni upload`() = runTest {
        val f = Fixture()
        seedSession(f, PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = true)

        f.transport.queryResults += YoutubeResumableResult.RetryableFailure("red caída al consultar")
        val r1 = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)
        assertEquals(UploadResult.Failure("red caída al consultar", retryable = true), r1)
        assertEquals(listOf("query"), f.transport.calls)
        // El journal quedó en finalChunkSent: el próximo intento solo consulta.
        assertEquals(PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, f.dao.rows.single().phase)
        assertTrue(f.dao.rows.single().ytFinalChunkSent)

        f.transport.queryResults += YoutubeResumableResult.Completed("vid-4")
        val r2 = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)
        assertEquals(
            UploadResult.Success("vid-4", "https://youtube.com/shorts/vid-4", durableOperationId = "op-1"),
            r2,
        )
        assertEquals(listOf("query", "query"), f.transport.calls)
        // Segundo intento: SOLO query + confirmar; la fila ya estaba en
        // finalChunkSent, así que no hace falta re-marcar el final.
        assertEquals(listOf("query", "query", "db:confirmed"), f.events)
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, f.dao.rows.single().phase)
        assertEquals(0, f.tokens)
    }

    // --- 5: 404/410 pre-final y post-final ---------------------------------------

    @Test
    fun `gone pre-final invalida la sesion y post-final la bloquea`() = runTest {
        // Pre-final (sin evidencia fuerte): falla segura → sessionInvalidated.
        val pre = Fixture()
        seedSession(pre, PublishOperationEntity.PHASE_SESSION_CREATED, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = false)
        pre.transport.queryResults += YoutubeResumableResult.Gone("La sesión ya no existe (HTTP 404).")

        val r1 = pre.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", pre.tokenProvider)

        assertEquals(UploadResult.Failure("La sesión ya no existe (HTTP 404).", retryable = false), r1)
        assertEquals(listOf("query"), pre.transport.calls)
        assertEquals(PublishOperationEntity.PHASE_SESSION_INVALIDATED, pre.dao.rows.single().phase)
        assertEquals(0, pre.tokens)

        // Post-final (punto de no retorno cruzado): desenlace ambiguo → blocked.
        val post = Fixture()
        seedSession(post, PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = true)
        post.transport.queryResults += YoutubeResumableResult.Gone("La sesión ya no existe (HTTP 410).")

        val r2 = post.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", post.tokenProvider)

        assertEquals(UploadResult.Failure("La sesión ya no existe (HTTP 410).", retryable = false), r2)
        assertEquals(listOf("query"), post.transport.calls)
        assertEquals(PublishOperationEntity.PHASE_BLOCKED, post.dao.rows.single().phase)
        assertEquals(0, post.tokens)
    }

    // --- 6: RetryableFailure en query ---------------------------------------------

    @Test
    fun `retryable en query no crea sesion ni envia bytes y post-final sigue finalChunkSent`() = runTest {
        // Pre-final: el query falla de forma reintentable y el coordinador NO
        // cae a createSession (duplicaría la sesión): devuelve retryable y
        // deja el journal intacto para reconsultar.
        val pre = Fixture()
        seedSession(pre, PublishOperationEntity.PHASE_SESSION_CREATED, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = false)
        pre.transport.queryResults += YoutubeResumableResult.RetryableFailure("timeout consultando")

        val r1 = pre.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", pre.tokenProvider)

        assertEquals(UploadResult.Failure("timeout consultando", retryable = true), r1)
        assertEquals(listOf("query"), pre.transport.calls)
        assertEquals(0, pre.dao.updateCalls)
        assertEquals(PublishOperationEntity.PHASE_SESSION_CREATED, pre.dao.rows.single().phase)
        assertEquals(0, pre.tokens)

        // Post-final: igual, y el flag del punto de no retorno sobrevive.
        val post = Fixture()
        seedSession(post, PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = true)
        post.transport.queryResults += YoutubeResumableResult.RetryableFailure("5xx consultando")

        val r2 = post.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", post.tokenProvider)

        assertEquals(UploadResult.Failure("5xx consultando", retryable = true), r2)
        assertEquals(listOf("query"), post.transport.calls)
        assertEquals(0, post.dao.updateCalls)
        assertEquals(PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, post.dao.rows.single().phase)
        assertTrue(post.dao.rows.single().ytFinalChunkSent)
        assertEquals(0, post.tokens)
    }

    // --- 7: 308 post-final ---------------------------------------------------------

    @Test
    fun `308 post-final aplica FinalChunkNotCompleted antes de reintentar el envio`() = runTest {
        val f = Fixture()
        seedSession(f, PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, bytesConfirmed = 60, totalBytes = 100, finalChunkSent = true)

        f.transport.queryResults += YoutubeResumableResult.Incomplete(60)
        f.transport.uploadResults += YoutubeResumableResult.Completed("vid-7")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)

        assertEquals(
            UploadResult.Success("vid-7", "https://youtube.com/shorts/vid-7", durableOperationId = "op-1"),
            r,
        )
        assertEquals(listOf("query", "upload:60"), f.transport.calls)
        // El 308 revierte el journal a sessionCreated (evento db:sessionCreated),
        // se re-marca el final y se reenvía desde el byte confirmado.
        assertEquals(
            listOf("query", "db:sessionCreated", "db:finalChunkSent", "upload:60", "db:confirmed"),
            f.events,
        )
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, f.dao.rows.single().phase)
        assertEquals(0, f.tokens)
    }

    // --- 8: nunca startNewAttempt ni operationId nuevo ------------------------------

    @Test
    fun `nunca llama a startNewAttempt ni cambia el operationId existente`() = runTest {
        val f = Fixture()
        f.store.getOrCreate("u1", "cfid-A", "youtube", "op-original")
        f.dao.rows[0] = f.dao.rows[0].copy(
            phase = PublishOperationEntity.PHASE_SESSION_INVALIDATED,
            lastError = "410 Gone de una sesión vieja",
        )

        val r1 = f.coordinator.upload(file(10), metadata, "u1", "cfid-A", "op-original", f.tokenProvider)
        assertEquals(UploadResult.Failure("410 Gone de una sesión vieja", retryable = false), r1)

        // Segundo intento, aunque el batch de UI llegue con OTRO operationId:
        // el journal sigue intacto — getOrCreate nunca lo refresca y el
        // coordinador jamás pasa por startNewAttempt (que habría limpiado la
        // fila a pending con operationId nuevo).
        val r2 = f.coordinator.upload(file(10), metadata, "u1", "cfid-A", "op-del-batch-nuevo", f.tokenProvider)
        assertEquals(UploadResult.Failure("410 Gone de una sesión vieja", retryable = false), r2)

        assertEquals("op-original", f.dao.rows.single().operationId)
        assertEquals(PublishOperationEntity.PHASE_SESSION_INVALIDATED, f.dao.rows.single().phase)
        assertEquals(1, f.dao.insertCalls)
        assertEquals(0, f.dao.updateCalls)
        assertEquals(0, f.tokens)
        assertTrue(f.transport.calls.isEmpty())
    }

    // --- 9: operationId durable acompaña todo Success ------------------------------

    @Test
    fun `start fresh usa el operationId persistido por getOrCreate`() = runTest {
        // Fila existente (pending limpio): getOrCreate la devuelve intacta —
        // el operationId nuevo del batch de UI nunca reemplaza al durable.
        val f = Fixture()
        f.store.getOrCreate("u1", "cfid-A", "youtube", "op-original")
        f.transport.createResults += YoutubeResumableResult.Created(SESSION_URL)
        f.transport.uploadResults += YoutubeResumableResult.Completed("vid-9")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-del-batch-nuevo", f.tokenProvider)

        assertEquals(
            UploadResult.Success("vid-9", "https://youtube.com/shorts/vid-9", durableOperationId = "op-original"),
            r,
        )
        assertEquals("op-original", f.dao.rows.single().operationId)
    }

    @Test
    fun `sin fila previa el operationId del caller se persiste y viaja en el success`() = runTest {
        val f = Fixture()
        f.transport.createResults += YoutubeResumableResult.Created(SESSION_URL)
        f.transport.uploadResults += YoutubeResumableResult.Completed("vid-9b")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-nuevo", f.tokenProvider)

        assertEquals(
            UploadResult.Success("vid-9b", "https://youtube.com/shorts/vid-9b", durableOperationId = "op-nuevo"),
            r,
        )
    }

    @Test
    fun `resume y already confirmed usan el operationId de la fila aunque el caller pase otro`() = runTest {
        // Resume: la fila en sessionCreated fue creada con op-1 (ver seedSession).
        val f = Fixture()
        seedSession(f, PublishOperationEntity.PHASE_SESSION_CREATED, bytesConfirmed = 40, totalBytes = 100, finalChunkSent = false)
        f.transport.queryResults += YoutubeResumableResult.Incomplete(40)
        f.transport.uploadResults += YoutubeResumableResult.Completed("vid-r")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-del-batch-nuevo", f.tokenProvider)

        assertEquals(
            UploadResult.Success("vid-r", "https://youtube.com/shorts/vid-r", durableOperationId = "op-1"),
            r,
        )

        // AlreadyConfirmed: cero red y aun así lleva la identidad durable.
        val c = Fixture()
        c.store.getOrCreate("u1", "cfid-A", "youtube", "op-original")
        c.dao.rows[0] = c.dao.rows[0].copy(
            phase = PublishOperationEntity.PHASE_CONFIRMED,
            resultPlatformId = "vid-previo",
            resultURL = "https://youtube.com/shorts/vid-previo",
        )
        val r2 = c.coordinator.upload(file(10), metadata, "u1", "cfid-A", "op-del-batch-nuevo", c.tokenProvider)
        assertEquals(
            UploadResult.Success("vid-previo", "https://youtube.com/shorts/vid-previo", durableOperationId = "op-original"),
            r2,
        )
        assertEquals(0, c.tokens)
        assertTrue(c.transport.calls.isEmpty())
    }

    // --- 10: excepción cruda de DAO ---------------------------------------------------

    @Test
    fun `excepcion generica de dao antes de red devuelve failure no reintentable sin token ni transporte`() = runTest {
        val f = Fixture()
        f.dao.crashOnFind = IllegalStateException("disk I/O error")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)

        assertEquals(UploadResult.Failure("disk I/O error", retryable = false), r)
        assertEquals(0, f.tokens)
        assertTrue(f.transport.calls.isEmpty())
        assertEquals(0, f.dao.insertCalls)
        assertEquals(0, f.dao.updateCalls)
    }

    @Test
    fun `excepcion generica de dao en un checkpoint devuelve failure no reintentable`() = runTest {
        val f = Fixture()
        f.transport.createResults += YoutubeResumableResult.Created(SESSION_URL)
        f.dao.crashOnUpdate = IllegalStateException("database is locked")

        val r = f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)

        assertEquals(UploadResult.Failure("database is locked", retryable = false), r)
    }

    @Test
    fun `cancellation exception del journal sigue propagandose`() = runTest {
        val f = Fixture()
        f.dao.crashOnFind = CancellationException("cancelado")

        try {
            f.coordinator.upload(file(100), metadata, "u1", "cfid-A", "op-1", f.tokenProvider)
            fail("CancellationException del journal debe propagarse")
        } catch (e: CancellationException) {
            // esperado: no se convierte en Failure
        }
        assertEquals(0, f.tokens)
        assertTrue(f.transport.calls.isEmpty())
    }

    private companion object {
        const val SESSION_URL = "https://upload.googleapis.com/session/op-1"
    }
}
