package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.database.CausalKeys
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.dto.ResolveIdentityResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Contrato real de POST /api/sync/resolve-identity: la central identifica por
// (deviceId, clientFileId) y jamás por fileName. Estos tests fijan eso y el
// hecho de que no quede ningún camino que cachee/hidrate identidad por nombre.
class CausalIdentityResolutionTest {

    private val userKey = "user-1"

    private fun resolution(
        api: FakeSyncApi = FakeSyncApi(),
        dao: FakeCausalPlatformDao = FakeCausalPlatformDao(),
        fileDao: FakeFileDao = FakeFileDao(),
        deviceIds: FakeDeviceIdProvider = FakeDeviceIdProvider(),
    ) = CausalIdentityResolution(api, dao, fileDao, deviceIds)

    @Test
    fun `el request lleva deviceId y clientFileId no vacios`() = runTest {
        val api = FakeSyncApi().apply { resolveIdentityResponse = ResolveIdentityResponse(contentId = "c-1") }
        val resolved = resolution(api = api)
            .resolve(userKey, remoteLibraryVideoId = null, clientFileId = "cfid-A", fileName = "reel.mp4")

        assertEquals("c-1", resolved)
        assertEquals(1, api.resolveIdentityRequests.size)
        val request = api.resolveIdentityRequests.single()
        assertEquals("cfid-A", request.clientFileId)
        assertEquals("device-fijo-1234", request.deviceId)
        assertTrue(request.deviceId.isNotBlank())
        assertTrue(request.clientFileId.isNotBlank())
        // El nombre viaja, pero como metadato.
        assertEquals("reel.mp4", request.fileName)
    }

    @Test
    fun `el deviceId es el mismo entre resoluciones`() = runTest {
        val api = FakeSyncApi().apply { resolveIdentityResponse = ResolveIdentityResponse(contentId = "c-1") }
        val store = resolution(api = api)

        store.resolve(userKey, null, "cfid-A", "a.mp4")
        store.resolve(userKey, null, "cfid-B", "b.mp4")

        assertEquals(2, api.resolveIdentityRequests.size)
        assertEquals(
            api.resolveIdentityRequests[0].deviceId,
            api.resolveIdentityRequests[1].deviceId,
        )
    }

    @Test
    fun `dos archivos con el mismo nombre resuelven identidades distintas`() = runTest {
        val dao = FakeCausalPlatformDao()
        val api = FakeSyncApi()
        val store = resolution(api = api, dao = dao)

        api.resolveIdentityResponse = ResolveIdentityResponse(contentId = "content-A")
        val a = store.resolve(userKey, null, "cfid-A", "reel.mp4")
        api.resolveIdentityResponse = ResolveIdentityResponse(contentId = "content-B")
        val b = store.resolve(userKey, null, "cfid-B", "reel.mp4")

        assertEquals("content-A", a)
        assertEquals("content-B", b)
        assertNotEquals(a, b)
        // Dos pedidos de verdad: el segundo NO se comió el cache del primero
        // por tener el mismo fileName.
        assertEquals(2, api.resolveIdentityRequests.size)
        assertEquals("content-A", dao.identities[userKey to CausalKeys.forClientFile("cfid-A")])
        assertEquals("content-B", dao.identities[userKey to CausalKeys.forClientFile("cfid-B")])
    }

    @Test
    fun `la identidad no se cachea ni se lee por fileName`() = runTest {
        val dao = FakeCausalPlatformDao()
        // Se siembra a mano una clave legacy "por nombre" con su valor: si
        // quedara algún lookup por fileName, la central no se llamaría y se
        // devolvería "content-viejo"; si la resolución escribiera por nombre,
        // la clave sembrada quedaría pisada. La prueba exige que la clave
        // legacy quede intacta y sin usar.
        dao.identities[userKey to "file:reel.mp4"] = "content-viejo"
        val api = FakeSyncApi().apply { resolveIdentityResponse = ResolveIdentityResponse(contentId = "content-nuevo") }

        val resolved = resolution(api = api, dao = dao).resolve(userKey, null, "cfid-A", "reel.mp4")

        // La respuesta viene de la central (valor nuevo), no del cache por nombre.
        assertEquals("content-nuevo", resolved)
        // Exactamente un pedido: el cache por nombre no satisfizo la consulta.
        assertEquals(1, api.resolveIdentityRequests.size)
        // El cache nuevo vive bajo la clave por clientFileId.
        assertEquals("content-nuevo", dao.identities[userKey to CausalKeys.forClientFile("cfid-A")])
        // La clave legacy quedó intacta: no se leyó para resolver ni se
        // sobrescribió.
        assertEquals("content-viejo", dao.identities[userKey to "file:reel.mp4"])
    }

    @Test
    fun `el cache por clientFileId evita un segundo pedido`() = runTest {
        val dao = FakeCausalPlatformDao()
        val api = FakeSyncApi().apply { resolveIdentityResponse = ResolveIdentityResponse(contentId = "c-1") }
        val store = resolution(api = api, dao = dao)

        store.resolve(userKey, null, "cfid-A", "reel.mp4")
        val second = store.resolve(userKey, null, "cfid-A", "reel-renombrado.mp4")

        assertEquals("c-1", second)
        assertEquals("un renombre no puede disparar una re-resolución", 1, api.resolveIdentityRequests.size)
    }

    @Test
    fun `sin clientFileId no se toca la red`() = runTest {
        val api = FakeSyncApi()
        val deviceIds = FakeDeviceIdProvider()

        val resolved = resolution(api = api, deviceIds = deviceIds)
            .resolve(userKey, remoteLibraryVideoId = null, clientFileId = null, fileName = "reel.mp4")

        assertNull(resolved)
        assertTrue(api.resolveIdentityRequests.isEmpty())
        assertEquals("ni siquiera se pide el deviceId", 0, deviceIds.calls)
    }

    @Test
    fun `un clientFileId en blanco tambien falla cerrado`() = runTest {
        val api = FakeSyncApi()

        val resolved = resolution(api = api).resolve(userKey, null, "   ", "reel.mp4")

        assertNull(resolved)
        assertTrue(api.resolveIdentityRequests.isEmpty())
    }

    @Test
    fun `un error de red deja la identidad sin resolver y no cachea nada`() = runTest {
        val dao = FakeCausalPlatformDao()
        val api = FakeSyncApi().apply { resolveIdentityError = java.io.IOException("sin red") }

        val resolved = resolution(api = api, dao = dao).resolve(userKey, null, "cfid-A", "reel.mp4")

        assertNull(resolved)
        assertTrue(dao.identities.isEmpty())
    }

    @Test
    fun `hydrateFromRemote guarda la clave del id remoto y no una por nombre`() = runTest {
        val dao = FakeCausalPlatformDao()
        val store = resolution(dao = dao)

        store.hydrateFromRemote(userKey, listOf(remoteVideo(id = "rlv-1", fileName = "reel.mp4", contentId = "c-1")))

        assertEquals("c-1", dao.identities[userKey to CausalKeys.forRemote("rlv-1")])
        assertFalse(dao.identities.keys.any { (_, localKey) -> localKey.contains("reel.mp4") })
    }

    @Test
    fun `hydrateFromRemote solo asocia el archivo local por el link explicito de nube`() = runTest {
        val dao = FakeCausalPlatformDao()
        val fileDao = FakeFileDao(
            listOf(
                // Bajado de Nube: tiene el link explícito.
                fileEntity(id = 1, clientFileId = "cfid-bajado", fileName = "reel.mp4", remoteLibraryVideoId = "rlv-1"),
                // Homónimo puramente local: NO se puede asociar.
                fileEntity(id = 2, clientFileId = "cfid-local", fileName = "reel.mp4"),
            ),
        )
        val store = resolution(dao = dao, fileDao = fileDao)

        store.hydrateFromRemote(userKey, listOf(remoteVideo(id = "rlv-1", fileName = "reel.mp4", contentId = "c-1")))

        assertEquals("c-1", dao.identities[userKey to CausalKeys.forClientFile("cfid-bajado")])
        assertNull(dao.identities[userKey to CausalKeys.forClientFile("cfid-local")])
    }

    @Test
    fun `hydrateFromRemote completa las filas del outbox del archivo correcto`() = runTest {
        val dao = FakeCausalPlatformDao()
        dao.outbox += outboxRow(id = 1, clientFileId = "cfid-bajado", fileName = "reel.mp4")
        dao.outbox += outboxRow(id = 2, clientFileId = "cfid-local", fileName = "reel.mp4")
        val fileDao = FakeFileDao(
            listOf(
                fileEntity(id = 1, clientFileId = "cfid-bajado", fileName = "reel.mp4", remoteLibraryVideoId = "rlv-1"),
                fileEntity(id = 2, clientFileId = "cfid-local", fileName = "reel.mp4"),
            ),
        )

        resolution(dao = dao, fileDao = fileDao)
            .hydrateFromRemote(userKey, listOf(remoteVideo(id = "rlv-1", fileName = "reel.mp4", contentId = "c-1")))

        assertEquals("c-1", dao.outbox.single { it.id == 1L }.contentId)
        assertNull("el homónimo no puede heredar la identidad", dao.outbox.single { it.id == 2L }.contentId)
    }

    private fun remoteVideo(id: String, fileName: String, contentId: String?) = RemoteLibraryVideoDto(
        _id = id,
        fileName = fileName,
        sizeBytes = 1_024,
        contentId = contentId,
    )
}
