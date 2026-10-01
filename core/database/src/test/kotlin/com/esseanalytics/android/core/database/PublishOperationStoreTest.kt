package com.esseanalytics.android.core.database

import com.esseanalytics.android.core.database.dao.PublishOperationDao
import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Store del journal sobre un DAO en memoria que replica la semántica que
// importa: find por (userKey, sourceId, platform), insert ABORT si la clave
// única ya existe (como el índice real de Room), update por copia.
class PublishOperationStoreTest {

    private class FakePublishOperationDao : PublishOperationDao {
        val rows = mutableListOf<PublishOperationEntity>()
        var insertCalls = 0
        var updateCalls = 0

        private fun keyOf(e: PublishOperationEntity) = Triple(e.userKey, e.sourceId, e.platform)

        override suspend fun find(userKey: String, sourceId: String, platform: String): PublishOperationEntity? =
            rows.firstOrNull { it.userKey == userKey && it.sourceId == sourceId && it.platform == platform }

        override suspend fun insert(entity: PublishOperationEntity): Long {
            insertCalls++
            // Réplica del índice único + ABORT: la clave durable nunca se pisa.
            if (rows.any { keyOf(it) == keyOf(entity) }) {
                throw IllegalStateException("UNIQUE constraint failed: publish_operations.userKey,sourceId,platform")
            }
            val stored = entity.copy(id = rows.size + 1L)
            rows += stored
            return stored.id
        }

        override suspend fun update(entity: PublishOperationEntity) {
            updateCalls++
            val index = rows.indexOfFirst { it.id == entity.id }
            if (index < 0) throw IllegalStateException("fila ${entity.id} no existe")
            rows[index] = entity
        }

        override fun observeBlocked(userKey: String): Flow<List<PublishOperationEntity>> =
            flowOf(rows.filter { it.userKey == userKey && it.phase == PublishOperationEntity.PHASE_BLOCKED })
    }

    private val userKey = "u1"
    private val youtube = "youtube"
    private val instagram = "instagram"

    private fun store(dao: FakePublishOperationDao = FakePublishOperationDao()) = PublishOperationStore(dao)

    // --- Identidad de la clave: por clientFileId, nunca por fileName --------

    @Test
    fun `dos archivos homonimos no colisionan porque la clave es clientFileId`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)

        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.getOrCreate(userKey, "cfid-B", youtube, "op-2")
        // El mismo archivo en otra plataforma tampoco colisiona: es otro intento.
        store.getOrCreate(userKey, "cfid-A", instagram, "op-3")

        assertEquals(3, dao.rows.size)
        assertEquals(3, dao.insertCalls)
    }

    // --- getOrCreate durable -------------------------------------------------

    @Test
    fun `getOrCreate es idempotente y jamas refresca el operationId durable`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)

        val primera = store.getOrCreate(userKey, "cfid-A", youtube, "op-primero")
        val segunda = store.getOrCreate(userKey, "cfid-A", youtube, "op-del-batch-nuevo")

        // El id lo asigna la base al insertar: una fila recién creada con
        // id=0 indicaría que el store devolvió la copia sin persistir.
        assertTrue(primera.id > 0)
        assertEquals(primera.id, segunda.id)
        assertEquals("op-primero", segunda.operationId)
        // La fila existente se devolvió intacta: ni un update ni un insert extra.
        assertEquals(1, dao.insertCalls)
        assertEquals(0, dao.updateCalls)
    }

    @Test
    fun `getOrCreate y startNewAttempt rechazan claves u operationId vacios`() = runTest {
        val store = store()
        val casos = listOf(
            arrayOf("", "cfid-A", youtube, "op-1"),
            arrayOf("u1", "", youtube, "op-1"),
            arrayOf("u1", "cfid-A", " ", "op-1"),
            arrayOf("u1", "cfid-A", youtube, ""),
        )
        for ((u, s, p, op) in casos) {
            try {
                store.getOrCreate(u, s, p, op)
                fail("getOrCreate con partes vacías no debe aplicar: '$u','$s','$p','$op'")
            } catch (e: IllegalArgumentException) {
                // esperado
            }
            try {
                store.startNewAttempt(u, s, p, "op-nuevo")
                fail("startNewAttempt con partes vacías no debe aplicar")
            } catch (e: IllegalArgumentException) {
                // esperado
            }
        }
        // newOperationId vacío también se rechaza.
        try {
            store.startNewAttempt("u1", "cfid-A", youtube, "  ")
            fail("newOperationId vacío no debe aplicar")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }

    // --- startNewAttempt ------------------------------------------------------

    private suspend fun seedInvalidated(store: PublishOperationStore, dao: FakePublishOperationDao): PublishOperationEntity {
        val row = store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        dao.rows[0] = row.copy(
            phase = PublishOperationEntity.PHASE_SESSION_INVALIDATED,
            ytSessionURL = "https://upload.googleapis.com/vieja",
            ytBytesConfirmed = 10,
            ytTotalBytes = 100,
            lastError = "410 Gone",
        )
        return dao.rows[0]
    }

    @Test
    fun `startNewAttempt solo desde sessionInvalidated sin evidencia fuerte limpia solo lo permitido`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        seedInvalidated(store, dao)

        val renacida = store.startNewAttempt(userKey, "cfid-A", youtube, "op-nuevo")

        assertEquals("op-nuevo", renacida.operationId)
        assertEquals(PublishOperationEntity.PHASE_PENDING, renacida.phase)
        assertNull(renacida.ytSessionURL)
        assertEquals(0, renacida.ytBytesConfirmed)
        assertNull(renacida.ytTotalBytes)
        assertFalse(renacida.ytFinalChunkSent)
        assertNull(renacida.resultPlatformId)
        assertNull(renacida.resultURL)
        assertNull(renacida.lastError)
        // Persistida de verdad, no solo devuelta.
        assertEquals(renacida, dao.rows.single())
        assertEquals(1, dao.updateCalls)
    }

    @Test
    fun `startNewAttempt rechaza cualquier fase que no sea sessionInvalidated`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)

        for (phase in listOf(
            PublishOperationEntity.PHASE_PENDING,
            PublishOperationEntity.PHASE_SESSION_CREATED,
            PublishOperationEntity.PHASE_FINAL_CHUNK_SENT,
            PublishOperationEntity.PHASE_CONFIRMED,
            PublishOperationEntity.PHASE_BLOCKED,
            PublishOperationEntity.PHASE_CONTAINER_CREATED,
            PublishOperationEntity.PHASE_PUBLISH_REQUESTED,
        )) {
            dao.rows.clear()
            store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
            dao.rows[0] = dao.rows[0].copy(phase = phase)
            try {
                store.startNewAttempt(userKey, "cfid-A", youtube, "op-nuevo")
                fail("startNewAttempt no debe aplicar desde $phase")
            } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
                // esperado
            }
        }
        // Ninguna de las rechazadas escribió ni cambió el operationId.
        assertEquals(0, dao.updateCalls)
    }

    @Test
    fun `startNewAttempt rechaza sessionInvalidated con evidencia fuerte`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        seedInvalidated(store, dao)
        // Evidencia fuerte: el flag del chunk final sobrevivió (fila corrupta).
        dao.rows[0] = dao.rows[0].copy(ytFinalChunkSent = true)

        try {
            store.startNewAttempt(userKey, "cfid-A", youtube, "op-nuevo")
            fail("evidencia fuerte debe impedir startNewAttempt")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        assertEquals(0, dao.updateCalls)
    }

    @Test
    fun `startNewAttempt sin fila lanza RowMissing`() = runTest {
        try {
            store().startNewAttempt(userKey, "cfid-A", youtube, "op-nuevo")
            fail("fila inexistente debe lanzar RowMissing")
        } catch (e: PublishOperationStoreException.RowMissing) {
            // esperado
        }
    }

    // --- Checkpoints YouTube --------------------------------------------------

    @Test
    fun `cadena youtube completa persiste cada paso y confirmar exige el final`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")

        val s1 = store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        assertEquals(PublishOperationEntity.PHASE_SESSION_CREATED, s1.phase)
        assertEquals(100, s1.ytTotalBytes)

        val s2 = store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(40), userKey, "cfid-A", youtube)
        assertEquals(40, s2.ytBytesConfirmed)

        val s3 = store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)
        assertEquals(PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, s3.phase)
        assertTrue(s3.ytFinalChunkSent)

        val s4 = store.checkpointYouTube(
            YouTubeUploadCheckpoint.Confirmed("vid-1", "https://youtu.be/vid-1"),
            userKey, "cfid-A", youtube,
        )
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, s4.phase)
        assertEquals("vid-1", s4.resultPlatformId)
        assertEquals(4, dao.updateCalls)
        // Cada paso quedó persistido en el DAO (no solo devuelto).
        assertEquals(s4, dao.rows.single())
    }

    @Test
    fun `un segundo checkpoint de final no habilita nada y se rechaza`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)

        // Reintentar el final: la fase ambigua no se "re-marca".
        try {
            store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)
            fail("finalChunkSent no debe aplicar dos veces")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        // El intento fallido no escribió.
        assertEquals(2, dao.updateCalls)
    }

    @Test
    fun `confirmed desde sessionCreated se rechaza sin evidencia del final`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )

        try {
            store.checkpointYouTube(
                YouTubeUploadCheckpoint.Confirmed("vid-1", "https://youtu.be/vid-1"),
                userKey, "cfid-A", youtube,
            )
            fail("confirmed sin finalChunkSent no debe aplicar")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        assertEquals(1, dao.updateCalls)
    }

    @Test
    fun `blocked se rechaza solo desde desenlaces asentados confirmed y blocked`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)

        for (phase in listOf(
            PublishOperationEntity.PHASE_CONFIRMED,
            PublishOperationEntity.PHASE_BLOCKED,
        )) {
            dao.rows.clear()
            store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
            dao.rows[0] = dao.rows[0].copy(phase = phase)
            try {
                store.checkpointYouTube(
                    YouTubeUploadCheckpoint.Blocked("x"),
                    userKey, "cfid-A", youtube,
                )
                fail("blocked no debe aplicar desde $phase")
            } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
                // esperado
            }
        }
    }

    @Test
    fun `blocked persiste desde las fases ambiguas finalChunkSent y publishRequested`() = runTest {
        // La política devuelve MarkBlocked exactamente desde estados corruptos
        // de esas fases — el store tiene que poder persistirlo.
        val daoYt = FakePublishOperationDao()
        val storeYt = store(daoYt)
        storeYt.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        storeYt.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        storeYt.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)

        val bloqueadaYt = storeYt.checkpointYouTube(
            YouTubeUploadCheckpoint.Blocked("estado ambiguo corrupto"),
            userKey, "cfid-A", youtube,
        )
        assertEquals(PublishOperationEntity.PHASE_BLOCKED, bloqueadaYt.phase)
        assertEquals("estado ambiguo corrupto", bloqueadaYt.lastError)
        assertEquals(bloqueadaYt, daoYt.rows.single())

        val daoIg = FakePublishOperationDao()
        val storeIg = store(daoIg)
        storeIg.getOrCreate(userKey, "cfid-A", instagram, "op-1")
        storeIg.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )
        storeIg.checkpointInstagram(
            InstagramUploadCheckpoint.ContainerCreated("c-1", "https://rupload.facebook.com/c-1"),
            userKey, "cfid-A", instagram,
        )
        storeIg.checkpointInstagram(InstagramUploadCheckpoint.PublishRequested, userKey, "cfid-A", instagram)

        val bloqueadaIg = storeIg.checkpointInstagram(
            InstagramUploadCheckpoint.Blocked("media_publish sin confirmar"),
            userKey, "cfid-A", instagram,
        )
        assertEquals(PublishOperationEntity.PHASE_BLOCKED, bloqueadaIg.phase)
        assertEquals(bloqueadaIg, daoIg.rows.single())

        // Y desde blocked, la policy ya no habilita red ni reset.
        assertTrue(
            PublishOperationPolicy.decide(
                phase = daoYt.rows.single().phase,
                ytSessionURL = daoYt.rows.single().ytSessionURL,
                ytBytesConfirmed = daoYt.rows.single().ytBytesConfirmed,
                ytTotalBytes = daoYt.rows.single().ytTotalBytes,
                ytFinalChunkSent = daoYt.rows.single().ytFinalChunkSent,
                resultPlatformId = daoYt.rows.single().resultPlatformId,
                resultURL = daoYt.rows.single().resultURL,
                lastError = daoYt.rows.single().lastError,
            ) is YouTubePublishDecision.AlreadyBlocked,
        )
    }

    @Test
    fun `progress es monotonico y rechaza bytes imposibles`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )

        store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(40), userKey, "cfid-A", youtube)
        // Repetir el mismo valor es idempotente.
        store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(40), userKey, "cfid-A", youtube)

        for (bytes in listOf(-1L, 0L, 39L, 101L)) {
            try {
                store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(bytes), userKey, "cfid-A", youtube)
                fail("progress $bytes no debe aplicar (monotónico, total 100, ya 40)")
            } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
                // esperado
            }
        }
        // Ninguno de los inválidos escribió.
        assertEquals(40, dao.rows.single().ytBytesConfirmed)
    }

    @Test
    fun `confirmed youtube exige url no vacia instagram la permite null`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)

        try {
            store.checkpointYouTube(
                YouTubeUploadCheckpoint.Confirmed("vid-1", ""),
                userKey, "cfid-A", youtube,
            )
            fail("confirmed de YouTube exige url no vacía")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }

        // Instagram: el mediaId se persiste ANTES del permalink — url null aplica.
        store.getOrCreate(userKey, "cfid-A", instagram, "op-1")
        store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )
        store.checkpointInstagram(
            InstagramUploadCheckpoint.ContainerCreated("c-1", "https://rupload.facebook.com/c-1"),
            userKey, "cfid-A", instagram,
        )
        store.checkpointInstagram(InstagramUploadCheckpoint.PublishRequested, userKey, "cfid-A", instagram)
        val confirmada = store.checkpointInstagram(
            InstagramUploadCheckpoint.Confirmed("m-1", null),
            userKey, "cfid-A", instagram,
        )
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, confirmada.phase)
        assertEquals("m-1", confirmada.resultPlatformId)
        assertNull(confirmada.resultURL)
    }

    @Test
    fun `un checkpoint cruzado de plataforma falla sin escribir`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.getOrCreate(userKey, "cfid-B", instagram, "op-2")

        try {
            store.checkpointInstagram(
                InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
                userKey, "cfid-A", youtube,
            )
            fail("checkpoint de Instagram sobre fila de YouTube no debe aplicar")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        try {
            store.checkpointYouTube(
                YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
                userKey, "cfid-B", instagram,
            )
            fail("checkpoint de YouTube sobre fila de Instagram no debe aplicar")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        try {
            store.startNewAttempt(userKey, "cfid-B", instagram, "op-nuevo")
            fail("startNewAttempt es una transición de YouTube")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        // Nada se escribió en ninguna de las dos filas.
        assertEquals(0, dao.updateCalls)
        assertEquals(PublishOperationEntity.PHASE_PENDING, dao.rows[0].phase)
        assertEquals(PublishOperationEntity.PHASE_PENDING, dao.rows[1].phase)
    }

    @Test
    fun `sessionInvalidated con evidencia fuerte se rechaza corresponde blocked`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        // Fila corrupta: el final marcado a mano junto a una sesión viva.
        dao.rows[0] = dao.rows[0].copy(ytFinalChunkSent = true, phase = PublishOperationEntity.PHASE_SESSION_CREATED)

        try {
            store.checkpointYouTube(
                YouTubeUploadCheckpoint.SessionInvalidated("410"),
                userKey, "cfid-A", youtube,
            )
            fail("evidencia fuerte debe impedir sessionInvalidated")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
    }

    @Test
    fun `sessionInvalidated desde pending aplica sin evidencia y falla con evidencia fuerte`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")

        // Pending con restos débiles de una sesión anterior: falla segura.
        dao.rows[0] = dao.rows[0].copy(
            ytSessionURL = "https://upload.googleapis.com/vieja",
            ytBytesConfirmed = 10,
            ytTotalBytes = 100,
        )
        val invalidada = store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionInvalidated("410 Gone"),
            userKey, "cfid-A", youtube,
        )
        assertEquals(PublishOperationEntity.PHASE_SESSION_INVALIDATED, invalidada.phase)
        assertEquals("410 Gone", invalidada.lastError)
        assertEquals(invalidada, dao.rows.single())

        // Fila corrupta: un resultado asentado sobre un pending — evidencia
        // fuerte de posible publicación, corresponde blocked, no esto.
        dao.rows.clear()
        store.getOrCreate(userKey, "cfid-A", youtube, "op-2")
        dao.rows[0] = dao.rows[0].copy(
            phase = PublishOperationEntity.PHASE_PENDING,
            resultPlatformId = "vid-x",
            resultURL = "https://youtu.be/vid-x",
        )
        try {
            store.checkpointYouTube(
                YouTubeUploadCheckpoint.SessionInvalidated("410 Gone"),
                userKey, "cfid-A", youtube,
            )
            fail("evidencia fuerte debe impedir sessionInvalidated aunque la fase sea pending")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        assertEquals(1, dao.updateCalls)
    }

    @Test
    fun `finalChunkNotCompleted desde finalChunkSent vuelve a sessionCreated de forma monotona`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(60), userKey, "cfid-A", youtube)
        store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)

        // El 308 del último chunk confirmó 60 bytes: el final no completó la
        // subida y la sesión sigue viva — se reanuda, no se reinicia.
        val reanudada = store.checkpointYouTube(
            YouTubeUploadCheckpoint.FinalChunkNotCompleted(60),
            userKey, "cfid-A", youtube,
        )
        assertEquals(PublishOperationEntity.PHASE_SESSION_CREATED, reanudada.phase)
        assertEquals(60, reanudada.ytBytesConfirmed)
        assertFalse(reanudada.ytFinalChunkSent)
        // Sesión, total y operationId se conservan intactos.
        assertEquals("https://upload.googleapis.com/s1", reanudada.ytSessionURL)
        assertEquals(100, reanudada.ytTotalBytes)
        assertEquals("op-1", reanudada.operationId)
        assertEquals(reanudada, dao.rows.single())

        // Y desde ahí el progreso puede seguir y el final reintentarse.
        val avance = store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(80), userKey, "cfid-A", youtube)
        assertEquals(80, avance.ytBytesConfirmed)
        val reintentada = store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)
        assertEquals(PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, reintentada.phase)
        assertTrue(reintentada.ytFinalChunkSent)
    }

    @Test
    fun `finalChunkNotCompleted se rechaza si no viene de finalChunkSent`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")

        // Desde pending.
        try {
            store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkNotCompleted(0), userKey, "cfid-A", youtube)
            fail("finalChunkNotCompleted desde pending no debe aplicar")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        // Desde sessionCreated sin un final previo.
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        try {
            store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkNotCompleted(40), userKey, "cfid-A", youtube)
            fail("finalChunkNotCompleted desde sessionCreated no debe aplicar")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        assertEquals(1, dao.updateCalls)
    }

    @Test
    fun `finalChunkNotCompleted rechaza bytes que retroceden o cubren el total`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )
        store.checkpointYouTube(YouTubeUploadCheckpoint.Progress(60), userKey, "cfid-A", youtube)
        store.checkpointYouTube(YouTubeUploadCheckpoint.FinalChunkSent, userKey, "cfid-A", youtube)

        // Negativos, menores que los ya confirmados, o >= total (llegar al
        // total sin completar es imposible — eso sería el final completo).
        for (bytes in listOf(-1L, 59L, 100L)) {
            try {
                store.checkpointYouTube(
                    YouTubeUploadCheckpoint.FinalChunkNotCompleted(bytes),
                    userKey, "cfid-A", youtube,
                )
                fail("finalChunkNotCompleted con $bytes no debe aplicar (60 confirmados / 100 total)")
            } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
                // esperado
            }
        }
        // Ninguno de los inválidos escribió: sigue en finalChunkSent intacto.
        assertEquals(PublishOperationEntity.PHASE_FINAL_CHUNK_SENT, dao.rows.single().phase)
        assertTrue(dao.rows.single().ytFinalChunkSent)
        assertEquals(60, dao.rows.single().ytBytesConfirmed)
    }

    // --- Checkpoints Instagram -------------------------------------------------

    @Test
    fun `cadena instagram completa con avance de etapa entre variantes`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", instagram, "op-1")

        val c1 = store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )
        assertEquals(PublishOperationEntity.PHASE_PENDING, c1.phase)
        assertEquals("original", c1.igStage)

        val c2 = store.checkpointInstagram(
            InstagramUploadCheckpoint.ContainerCreated("c-1", "https://rupload.facebook.com/c-1"),
            userKey, "cfid-A", instagram,
        )
        assertEquals(PublishOperationEntity.PHASE_CONTAINER_CREATED, c2.phase)

        val c3 = store.checkpointInstagram(InstagramUploadCheckpoint.PublishRequested, userKey, "cfid-A", instagram)
        assertEquals(PublishOperationEntity.PHASE_PUBLISH_REQUESTED, c3.phase)
        assertTrue(c3.igPublishRequested)

        val c4 = store.checkpointInstagram(
            InstagramUploadCheckpoint.Confirmed("m-1", "https://instagram.com/p/m-1"),
            userKey, "cfid-A", instagram,
        )
        assertEquals(PublishOperationEntity.PHASE_CONFIRMED, c4.phase)
        assertEquals(c4, dao.rows.single())
    }

    @Test
    fun `stageStarting limpia el contenedor anterior y vuelve a pending`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", instagram, "op-1")
        store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )
        store.checkpointInstagram(
            InstagramUploadCheckpoint.ContainerCreated("c-1", "https://rupload.facebook.com/c-1"),
            userKey, "cfid-A", instagram,
        )

        val avanzada = store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.TRIMMED_60S),
            userKey, "cfid-A", instagram,
        )

        assertEquals(PublishOperationEntity.PHASE_PENDING, avanzada.phase)
        assertEquals("trimmed60s", avanzada.igStage)
        assertNull(avanzada.igContainerId)
        assertNull(avanzada.igUploadURI)
        assertFalse(avanzada.igPublishRequested)
    }

    @Test
    fun `un segundo publishRequested se rechaza y no reescribe el punto de no retorno`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", instagram, "op-1")
        store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )
        store.checkpointInstagram(
            InstagramUploadCheckpoint.ContainerCreated("c-1", "https://rupload.facebook.com/c-1"),
            userKey, "cfid-A", instagram,
        )
        store.checkpointInstagram(InstagramUploadCheckpoint.PublishRequested, userKey, "cfid-A", instagram)

        try {
            store.checkpointInstagram(InstagramUploadCheckpoint.PublishRequested, userKey, "cfid-A", instagram)
            fail("publishRequested no debe aplicar dos veces")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
        assertEquals(3, dao.updateCalls)
    }

    @Test
    fun `confirmed instagram solo desde publishRequested`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", instagram, "op-1")
        store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )
        store.checkpointInstagram(
            InstagramUploadCheckpoint.ContainerCreated("c-1", "https://rupload.facebook.com/c-1"),
            userKey, "cfid-A", instagram,
        )

        try {
            store.checkpointInstagram(
                InstagramUploadCheckpoint.Confirmed("m-1", "https://instagram.com/p/m-1"),
                userKey, "cfid-A", instagram,
            )
            fail("confirmed sin publishRequested no debe aplicar")
        } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
            // esperado
        }
    }

    @Test
    fun `containerCreated exige URI https valida y containerId no vacio`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", instagram, "op-1")
        store.checkpointInstagram(
            InstagramUploadCheckpoint.StageStarting(InstagramUploadStage.ORIGINAL),
            userKey, "cfid-A", instagram,
        )

        for (checkpoint in listOf(
            InstagramUploadCheckpoint.ContainerCreated("", "https://rupload.facebook.com/c-1"),
            InstagramUploadCheckpoint.ContainerCreated("c-1", "http://rupload.facebook.com/c-1"),
            InstagramUploadCheckpoint.ContainerCreated("c-1", "relativa"),
        )) {
            try {
                store.checkpointInstagram(checkpoint, userKey, "cfid-A", instagram)
                fail("$checkpoint no debe aplicar")
            } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
                // esperado
            }
        }
        // Ninguno de los inválidos escribió.
        assertEquals(1, dao.updateCalls)
    }

    // --- Errores transversales --------------------------------------------------

    @Test
    fun `cualquier checkpoint sin fila lanza RowMissing ruidosamente`() = runTest {
        val store = store()
        try {
            store.checkpointYouTube(
                YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
                userKey, "cfid-A", youtube,
            )
            fail("fila inexistente debe lanzar RowMissing")
        } catch (e: PublishOperationStoreException.RowMissing) {
            // esperado
        }
    }

    @Test
    fun `las escrituras devuelven la copia persistida no una mutacion en memoria suelta`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        store.getOrCreate(userKey, "cfid-A", youtube, "op-1")

        val devuelta = store.checkpointYouTube(
            YouTubeUploadCheckpoint.SessionCreated("https://upload.googleapis.com/s1", 100),
            userKey, "cfid-A", youtube,
        )

        assertEquals(devuelta, dao.rows.single())
        assertNotEquals(PublishOperationEntity.PHASE_PENDING, dao.rows.single().phase)
    }

    // --- confirmNotPublished (recuperación de publicación ambigua, diseño
    // docs/ambiguous-publish-recovery-design-2026-09-22.md) ------------------

    private suspend fun seedBlocked(
        store: PublishOperationStore,
        dao: FakePublishOperationDao,
        platform: String,
    ): PublishOperationEntity {
        store.getOrCreate(userKey, "cfid-A", platform, "op-1")
        val ambigua = if (platform == youtube) {
            dao.rows[0].copy(
                phase = PublishOperationEntity.PHASE_BLOCKED,
                ytSessionURL = "https://upload.googleapis.com/ambiguo",
                ytBytesConfirmed = 100,
                ytTotalBytes = 100,
                ytFinalChunkSent = true,
                lastError = "No se pudo confirmar en la plataforma",
            )
        } else {
            dao.rows[0].copy(
                phase = PublishOperationEntity.PHASE_BLOCKED,
                igStage = "original",
                igContainerId = "contenedor-ambiguo",
                igUploadURI = "https://upload.example.com/uri",
                igPublishRequested = true,
                lastError = "No se pudo confirmar en la plataforma",
            )
        }
        dao.rows[0] = ambigua
        return ambigua
    }

    @Test
    fun `confirmNotPublished desde blocked deja un intento limpio con evidencia`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        seedBlocked(store, dao, youtube)

        val renacida = store.confirmNotPublished(userKey, "cfid-A", youtube, "op-nuevo")

        // Intento NUEVO: identidad nueva y cero handles reutilizables -- el
        // próximo decide() tiene que reclasificar la fila como StartFresh.
        assertEquals("op-nuevo", renacida.operationId)
        assertEquals(PublishOperationEntity.PHASE_PENDING, renacida.phase)
        assertNull(renacida.ytSessionURL)
        assertEquals(0, renacida.ytBytesConfirmed)
        assertNull(renacida.ytTotalBytes)
        assertFalse(renacida.ytFinalChunkSent)
        assertNull(renacida.resultPlatformId)
        assertNull(renacida.resultURL)
        // Evidencia: el mensaje original se preserva y la confirmación queda
        // registrada con la acción fija confirmed_not_published.
        assertEquals("No se pudo confirmar en la plataforma", renacida.lastError)
        assertEquals(PublishOperationEntity.CONFIRMED_NOT_PUBLISHED, renacida.lastConfirmationAction)
        assertNotNull(renacida.lastConfirmationAtEpochMs)
        // Persistida de verdad.
        assertEquals(renacida, dao.rows.single())
        assertEquals(1, dao.updateCalls)
    }

    @Test
    fun `confirmNotPublished tambien limpia los handles ambiguos de Instagram`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        seedBlocked(store, dao, instagram)

        val renacida = store.confirmNotPublished(userKey, "cfid-A", instagram, "op-nuevo")

        assertEquals("op-nuevo", renacida.operationId)
        assertEquals(PublishOperationEntity.PHASE_PENDING, renacida.phase)
        assertNull(renacida.igStage)
        assertNull(renacida.igContainerId)
        assertNull(renacida.igUploadURI)
        assertFalse(renacida.igPublishRequested)
        assertEquals("No se pudo confirmar en la plataforma", renacida.lastError)
        assertEquals(PublishOperationEntity.CONFIRMED_NOT_PUBLISHED, renacida.lastConfirmationAction)
    }

    @Test
    fun `confirmNotPublished rechaza cualquier fase que no sea blocked sin mutar`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)

        for (phase in listOf(
            PublishOperationEntity.PHASE_PENDING,
            PublishOperationEntity.PHASE_SESSION_CREATED,
            PublishOperationEntity.PHASE_FINAL_CHUNK_SENT,
            PublishOperationEntity.PHASE_CONFIRMED,
            PublishOperationEntity.PHASE_SESSION_INVALIDATED,
        )) {
            dao.rows.clear()
            store.getOrCreate(userKey, "cfid-A", youtube, "op-1")
            dao.rows[0] = dao.rows[0].copy(phase = phase)
            try {
                store.confirmNotPublished(userKey, "cfid-A", youtube, "op-nuevo")
                fail("confirmNotPublished no debe aplicar desde $phase")
            } catch (e: PublishOperationStoreException.TransitionNotApplicable) {
                // esperado
            }
        }
        // Una fila confirmed jamás pasa por este flujo, y ninguna de las
        // rechazadas escribió ni cambió el operationId.
        assertEquals(0, dao.updateCalls)
        assertEquals("op-1", dao.rows.single().operationId)
    }

    @Test
    fun `confirmNotPublished sin fila lanza RowMissing`() = runTest {
        try {
            store().confirmNotPublished(userKey, "cfid-A", youtube, "op-nuevo")
            fail("fila inexistente debe lanzar RowMissing")
        } catch (e: PublishOperationStoreException.RowMissing) {
            // esperado
        }
    }

    @Test
    fun `confirmNotPublished rechaza newOperationId vacio`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        seedBlocked(store, dao, youtube)
        try {
            store.confirmNotPublished(userKey, "cfid-A", youtube, " ")
            fail("newOperationId vacío no debe aplicar")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
        assertEquals(0, dao.updateCalls)
    }

    @Test
    fun `un bloqueo nuevo supersede la confirmacion del intento anterior`() = runTest {
        val dao = FakePublishOperationDao()
        val store = store(dao)
        seedBlocked(store, dao, youtube)
        store.confirmNotPublished(userKey, "cfid-A", youtube, "op-nuevo")

        // El intento nuevo vuelve a quedar ambiguo: la confirmación vieja
        // hablaba del intento anterior y el checkpoint .blocked la borra.
        store.checkpointYouTube(
            YouTubeUploadCheckpoint.Blocked("otra vez sin confirmar"),
            userKey, "cfid-A", youtube,
        )

        val fila = dao.rows.single()
        assertEquals(PublishOperationEntity.PHASE_BLOCKED, fila.phase)
        assertEquals("otra vez sin confirmar", fila.lastError)
        assertNull(fila.lastConfirmationAction)
        assertNull(fila.lastConfirmationAtEpochMs)
    }
}
