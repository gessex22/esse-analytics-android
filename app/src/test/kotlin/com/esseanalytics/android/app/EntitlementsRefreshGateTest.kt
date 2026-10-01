package com.esseanalytics.android.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsRefreshGateTest {
    @Test
    fun `refresca al volver a primer plano solo una vez por intervalo`() {
        var now = 1_000L
        val gate = EntitlementsRefreshGate(clockMs = { now })

        assertTrue(gate.shouldRefresh("usuario-a"))
        now += EntitlementsRefreshGate.DEFAULT_MIN_INTERVAL_MS - 1
        assertFalse(gate.shouldRefresh("usuario-a"))
        now += 1
        assertTrue(gate.shouldRefresh("usuario-a"))
    }

    @Test
    fun `un cambio de cuenta no hereda el enfriamiento anterior`() {
        var now = 1_000L
        val gate = EntitlementsRefreshGate(clockMs = { now })

        assertTrue(gate.shouldRefresh("usuario-a"))
        now += 1
        assertTrue(gate.shouldRefresh("usuario-b"))
    }

    @Test
    fun `no consulta si no hay una identidad de usuario`() {
        val gate = EntitlementsRefreshGate(clockMs = { 1_000L })

        assertFalse(gate.shouldRefresh(null))
        assertFalse(gate.shouldRefresh("  "))
    }
}
