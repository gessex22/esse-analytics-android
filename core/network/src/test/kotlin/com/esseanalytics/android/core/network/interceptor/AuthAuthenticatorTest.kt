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

    // OJO: los intentos previos (074fa82 / adf15de) reescribieron ESTA función
    // creyendo que el caso 2 fallaba por una carrera de suscripción del
    // collector. No era eso: el emit nunca ocurría porque
    // FakeTokenSessionGuard.clearIfCurrent comparaba por identidad
    // (AtomicReference.compareAndSet) y el Bearer parseado del header es otra
    // instancia de String — ver el fake más abajo y TokenGuard.clearIfCurrent.
    // Ya arreglado eso, este armado sí captura el evento de forma determinista:
    // UNDISPATCHED corre el collector inline hasta allocateSlot()+awaitValue,
    // así queda suscripto antes de disparar `body`; `body` encola el
    // scope.launch { emit } de handleUnauthorized en el StandardTestDispatcher
    // y advanceUntilIdle() lo corre y drena la continuation que el emit reanuda
    // en el collector. Todo en tiempo virtual, sin sleeps ni timeouts.
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

    // Espeja la semántica real de TokenGuard.clearIfCurrent: compara por VALOR
    // (el Bearer parseado del header es un String distinto al guardado, aunque
    // sea igual por contenido) y recién entonces hace el compareAndSet por
    // identidad sobre la instancia leída. Un compareAndSet(expectedToken, null)
    // directo compararía por referencia y devolvería siempre false acá.
    override fun clearIfCurrent(expectedToken: String): Boolean {
        while (true) {
            val cur = current.get() ?: return false
            if (cur != expectedToken) return false
            if (current.compareAndSet(cur, null)) return true
        }
    }
}
