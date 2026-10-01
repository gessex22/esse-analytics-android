package com.esseanalytics.android.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Bootstrap de identidad del flusher: qué filas del outbox llegan a pedir
// resolve-identity y cuáles se quedan quietas. El criterio es "falla cerrado":
// sin handle local no se pide nada y la fila se conserva.
class CausalFlusherBootstrapTest {

    private val userKey = "user-1"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Resolver espía: registra con qué se lo llamó y no toca la red.
    private class RecordingResolver(private val contentIdByClientFileId: Map<String, String> = emptyMap()) :
        CausalIdentityResolver {
        val calls = mutableListOf<Triple<String?, String?, String?>>()

        override suspend fun resolveContentId(
            userKey: String,
            remoteLibraryVideoId: String?,
            clientFileId: String?,
            fileName: String?,
        ): String? {
            calls += Triple(remoteLibraryVideoId, clientFileId, fileName)
            return clientFileId?.let { contentIdByClientFileId[it] }
        }
    }

    private fun flusher(dao: FakeCausalPlatformDao, resolver: CausalIdentityResolver, api: FakeSyncApi = FakeSyncApi()) =
        CausalFlusher(dao, api, resolver, json)

    @Test
    fun `una fila legacy sin clientFileId no sale a la red y se conserva`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, clientFileId = null, fileName = "reel.mp4")
        val resolver = RecordingResolver()
        val api = FakeSyncApi()

        flusher(dao, resolver, api).flush(userKey)

        assertTrue("no se puede intentar resolver sin handle local", resolver.calls.isEmpty())
        assertTrue(api.resolveIdentityRequests.isEmpty())
        assertTrue(api.transitionRequests.isEmpty())
        // La intención NO se descarta: queda encolada esperando.
        assertEquals(1, dao.outbox.size)
        assertNull(dao.outbox.single().contentId)
        assertEquals(1, dao.countBlockedLegacy(userKey))
    }

    @Test
    fun `una fila con clientFileId resuelve identidad e hidrata solo la suya`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, clientFileId = "cfid-A", fileName = "reel.mp4")
        // Homónimo de OTRO archivo: no puede quedar hidratado de rebote.
        dao.outbox += outboxRow(id = 2, clientFileId = "cfid-B", fileName = "reel.mp4")
        val resolver = RecordingResolver(mapOf("cfid-A" to "content-A"))

        flusher(dao, resolver).flush(userKey)

        assertEquals("content-A", dao.outbox.single { it.id == 1L }.contentId)
        assertNull(dao.outbox.single { it.id == 2L }.contentId)
    }

    @Test
    fun `el bootstrap identifica por clientFileId, no por nombre`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, clientFileId = "cfid-A", fileName = "reel.mp4")
        dao.outbox += outboxRow(id = 2, clientFileId = "cfid-B", fileName = "reel.mp4")
        val resolver = RecordingResolver()

        flusher(dao, resolver).flush(userKey)

        // Dos archivos distintos = dos intentos, aunque compartan el nombre.
        assertEquals(2, resolver.calls.size)
        assertEquals(listOf("cfid-A", "cfid-B"), resolver.calls.map { it.second })
    }

    @Test
    fun `el mismo archivo no se pide dos veces en el mismo flush`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, clientFileId = "cfid-A", fileName = "reel.mp4", platform = "youtube")
        dao.outbox += outboxRow(id = 2, clientFileId = "cfid-A", fileName = "reel.mp4", platform = "instagram")
        val resolver = RecordingResolver()

        flusher(dao, resolver).flush(userKey)

        assertEquals(1, resolver.calls.size)
    }

    @Test
    fun `una fila sin baseVersion conocida frena la cadena sin inventar una base`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, contentId = "content-A", clientFileId = "cfid-A", baseVersion = null)
        val api = FakeSyncApi()

        flusher(dao, RecordingResolver(), api).flush(userKey)

        assertTrue(api.transitionRequests.isEmpty())
        assertEquals(1, dao.outbox.size)
    }

    @Test
    fun `con identidad y base la transicion sale y la fila se saca`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, contentId = "content-A", clientFileId = "cfid-A", baseVersion = 3)
        val api = FakeSyncApi()

        flusher(dao, RecordingResolver(), api).flush(userKey)

        assertEquals(1, api.transitionRequests.size)
        assertEquals("content-A", api.transitionRequests.single().contentId)
        assertEquals(3L, api.transitionRequests.single().baseVersion)
        assertTrue(dao.outbox.isEmpty())
    }
}
