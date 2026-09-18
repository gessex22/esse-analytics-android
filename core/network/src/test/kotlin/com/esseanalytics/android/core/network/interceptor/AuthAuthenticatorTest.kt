package com.esseanalytics.android.core.network.interceptor

import com.esseanalytics.android.core.datastore.TokenSessionGuard
import com.esseanalytics.android.core.network.AuthEvent
import com.esseanalytics.android.core.network.AuthEventBus
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAuthenticatorTest {

    // 1. 401 central con Bearer viejo (A) que llega despues de un login B:
    // no debe limpiar la sesion vigente ni emitir nada.
    @Test
    fun `stale central 401 after a newer login neither clears nor emits`() {
        val guard = FakeTokenSessionGuard("tokenB")
        val eventBus = AuthEventBus()
        val response = unauthorizedResponse(url = "https://api.esse-analytics.com/api/videos", bearer = "tokenA")

        val events = collectEvents(eventBus) {
            handleUnauthorized(response, guard, eventBus, it)
        }

        assertTrue(events.isEmpty())
        assertEquals("tokenB", guard.token)
    }

    // 2. 401 central con el Bearer vigente: limpia y emite SessionExpired.
    @Test
    fun `current central 401 clears the session and emits SessionExpired`() {
        val guard = FakeTokenSessionGuard("tokenA")
        val eventBus = AuthEventBus()
        val response = unauthorizedResponse(url = "https://api.esse-analytics.com/api/videos", bearer = "tokenA")

        val events = collectEvents(eventBus) {
            handleUnauthorized(response, guard, eventBus, it)
        }

        assertEquals(listOf(AuthEvent.SessionExpired), events)
        assertNull(guard.token)
    }

    // 3. 401 de plataforma (youtube/instagram/tiktok) con Bearer viejo:
    // no emite (y nunca toca la central de por si).
    @Test
    fun `stale platform 401 does not emit`() {
        val guard = FakeTokenSessionGuard("tokenB")
        val eventBus = AuthEventBus()
        val response = unauthorizedResponse(url = "https://api.esse-analytics.com/api/youtube/upload", bearer = "tokenA")

        val events = collectEvents(eventBus) {
            handleUnauthorized(response, guard, eventBus, it)
        }

        assertTrue(events.isEmpty())
        assertEquals("tokenB", guard.token)
    }

    // 4. 401 de plataforma con Bearer vigente: emite PlatformSessionExpired
    // y jamas limpia la sesion central.
    @Test
    fun `current platform 401 emits PlatformSessionExpired and never clears the central session`() {
        val guard = FakeTokenSessionGuard("tokenA")
        val eventBus = AuthEventBus()
        val response = unauthorizedResponse(url = "https://api.esse-analytics.com/api/instagram/upload", bearer = "tokenA")

        val events = collectEvents(eventBus) {
            handleUnauthorized(response, guard, eventBus, it)
        }

        assertEquals(listOf(AuthEvent.PlatformSessionExpired("instagram")), events)
        assertEquals("tokenA", guard.token)
    }

    // 5. 401 sin header Authorization: no hay Bearer del que decidir nada,
    // no limpia ni emite.
    @Test
    fun `401 without an Authorization header neither clears nor emits`() {
        val guard = FakeTokenSessionGuard("tokenA")
        val eventBus = AuthEventBus()
        val response = unauthorizedResponse(url = "https://api.esse-analytics.com/api/videos", bearer = null)

        val events = collectEvents(eventBus) {
            handleUnauthorized(response, guard, eventBus, it)
        }

        assertTrue(events.isEmpty())
        assertEquals("tokenA", guard.token)
    }

    private fun unauthorizedResponse(url: String, bearer: String?): Response {
        val requestBuilder = Request.Builder().url(url)
        if (bearer != null) requestBuilder.header("Authorization", "Bearer $bearer")
        return Response.Builder()
            .request(requestBuilder.build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody(null))
            .build()
    }

    // runTest corre sobre un StandardTestDispatcher respaldado por un
    // TestCoroutineScheduler: nada arranca solo, cada coroutine queda
    // encolada hasta que se pide explicitamente avanzar el scheduler. Eso
    // reemplaza el intento anterior con Dispatchers.Unconfined, que dependia
    // de que el collector alcanzara su punto de suspension (ya suscripto)
    // antes de que corriera el scope.launch interno de handleUnauthorized, y
    // de que ese launch entregara el evento antes de cancelar -- orden que
    // Unconfined no garantiza entre corrutinas hermanas. Aca el orden es
    // explicito: 1) se arranca el collector y se avanza el scheduler hasta
    // que quede suscripto y en espera; 2) se corre `body`, que solo encola
    // el scope.launch { eventBus.emit(...) } de handleUnauthorized sin
    // ejecutarlo todavia; 3) se vuelve a avanzar el scheduler para que ese
    // launch corra hasta el final y el collector reciba el evento; recien
    // ahi se cancela. Todo en tiempo virtual, sin sleeps ni timeouts reales.
    private fun collectEvents(eventBus: AuthEventBus, body: (CoroutineScope) -> Unit): List<AuthEvent> {
        val events = mutableListOf<AuthEvent>()
        runTest {
            val collectJob = launch {
                eventBus.events.collect { events.add(it) }
            }
            testScheduler.advanceUntilIdle()

            body(this)
            testScheduler.advanceUntilIdle()

            collectJob.cancelAndJoin()
        }
        return events
    }
}

private class FakeTokenSessionGuard(initial: String?) : TokenSessionGuard {
    private val current = AtomicReference(initial)
    override val token: String? get() = current.get()
    override fun clearIfCurrent(expectedToken: String): Boolean = current.compareAndSet(expectedToken, null)
}
