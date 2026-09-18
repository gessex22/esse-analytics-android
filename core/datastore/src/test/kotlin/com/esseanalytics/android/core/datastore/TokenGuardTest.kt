package com.esseanalytics.android.core.datastore

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenGuardTest {

    @Test
    fun `get returns the initial value`() {
        val guard = TokenGuard("tokenA")

        assertEquals("tokenA", guard.get())
    }

    @Test
    fun `set overwrites the current value`() {
        val guard = TokenGuard("tokenA")

        guard.set("tokenB")

        assertEquals("tokenB", guard.get())
    }

    @Test
    fun `clearIfCurrent clears and returns true when the expected token matches`() {
        val guard = TokenGuard("tokenA")

        val cleared = guard.clearIfCurrent("tokenA")

        assertTrue(cleared)
        assertNull(guard.get())
    }

    @Test
    fun `clearIfCurrent leaves the value intact and returns false on a stale token`() {
        // Simula la carrera del enunciado: se inicio sesion B (o se detecto
        // una expiracion) despues de que una request con el Bearer A ya habia
        // salido; cuando esa respuesta 401 tardia llega, la sesion vigente ya
        // no es A.
        val guard = TokenGuard("tokenA")
        guard.set("tokenB")

        val cleared = guard.clearIfCurrent("tokenA")

        assertFalse(cleared)
        assertEquals("tokenB", guard.get())
    }

    @Test
    fun `clearIfCurrent on a null guard never matches a real token`() {
        val guard = TokenGuard(null)

        val cleared = guard.clearIfCurrent("tokenA")

        assertFalse(cleared)
        assertNull(guard.get())
    }

    @Test
    fun `concurrent clearIfCurrent calls with the same expected token succeed exactly once`() {
        val guard = TokenGuard("tokenA")
        val threadCount = 32
        val readyLatch = CountDownLatch(threadCount)
        val goLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successes = AtomicInteger(0)

        val threads = List(threadCount) {
            Thread {
                readyLatch.countDown()
                goLatch.await()
                if (guard.clearIfCurrent("tokenA")) successes.incrementAndGet()
                doneLatch.countDown()
            }
        }
        threads.forEach { it.start() }
        readyLatch.await()
        goLatch.countDown()
        doneLatch.await()

        assertEquals(1, successes.get())
        assertNull(guard.get())
    }

    @Test
    fun `a set during a race prevents a stale clearIfCurrent from winning`() {
        val guard = TokenGuard("tokenA")
        val readyLatch = CountDownLatch(1)
        val relogin = Thread {
            readyLatch.await()
            guard.set("tokenB")
        }

        relogin.start()
        readyLatch.countDown()
        relogin.join()
        val cleared = guard.clearIfCurrent("tokenA")

        assertFalse(cleared)
        assertEquals("tokenB", guard.get())
    }
}
