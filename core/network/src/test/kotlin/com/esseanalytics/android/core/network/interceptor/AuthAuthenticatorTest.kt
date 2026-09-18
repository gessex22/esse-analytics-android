package com.esseanalytics.android.core.network.interceptor

import com.esseanalytics.android.core.datastore.TokenSessionGuard
import com.esseanalytics.android.core.network.AuthEvent
import com.esseanalytics.android.core.network.AuthEventBus
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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

    // El intento anterior (launch con start DEFAULT + advanceUntilIdle()
    // antes de `body`) seguia fallando en CI (run 101, commit 074fa82):
    // DEFAULT solo *encola* el arranque del collector en el scheduler:
    // sigue siendo advanceUntilIdle() quien decide cuando corre esa cola, y
    // nada en la API documenta que un unico advanceUntilIdle() alcance para
    // llevar a collect() hasta su punto de suspension (registrado como
    // subscriptor del MutableSharedFlow) antes de que sigamos con `body`.
    // CoroutineStart.UNDISPATCHED elimina esa dependencia por completo: el
    // cuerpo de la corrutina corre inline, en el hilo actual, apenas se
    // llama a launch(...) -- collect() ya alcanzo allocateSlot() (quedo
    // suscripto) y esta bloqueado en su primer punto de suspension real
    // (awaitValue) para cuando esta linea termina, sin pasar por el
    // scheduler. Recien de ahi en adelante entra el tiempo virtual: `body`
    // solo encola el scope.launch { eventBus.emit(...) } de
    // handleUnauthorized (via el dispatcher DEFAULT del propio TestScope);
    // advanceUntilIdle() lo corre, el emit() encuentra el slot ya
    // suscripto y reanuda su continuation (otra tarea encolada que el mismo
    // advanceUntilIdle() drena antes de devolver el control); recien ahi se
    // cancela. Sin sleeps, delays ni timeouts reales.
    private fun collectEvents(eventBus: AuthEventBus, body: (CoroutineScope) -> Unit): List<AuthEvent> {
        val events = mutableListOf<AuthEvent>()
        runTest {
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                eventBus.events.collect { events.add(it) }
            }

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
