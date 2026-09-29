package com.esseanalytics.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlRulesTest {

    @Test
    fun `valor vacio normaliza a central`() {
        assertValid("", stored = "", base = "https://api.esse-analytics.com")
    }

    @Test
    fun `valor en blanco normaliza a central`() {
        assertValid("   ", stored = "", base = "https://api.esse-analytics.com")
    }

    @Test
    fun `central escrita a mano es valida y normaliza sin barra final`() {
        assertValid(
            "https://api.esse-analytics.com/",
            stored = "https://api.esse-analytics.com",
            base = "https://api.esse-analytics.com",
        )
    }

    @Test
    fun `url lan con puerto es valida`() {
        assertValid("http://192.168.1.50:4000", stored = "http://192.168.1.50:4000", base = "http://192.168.1.50:4000")
    }

    @Test
    fun `recorta espacios alrededor y barras finales`() {
        assertValid(
            "  http://192.168.1.50:4000/  ",
            stored = "http://192.168.1.50:4000",
            base = "http://192.168.1.50:4000",
        )
    }

    @Test
    fun `recorta varias barras finales`() {
        assertValid("http://host:4000//", stored = "http://host:4000", base = "http://host:4000")
    }

    @Test
    fun `mantiene path del candidato`() {
        assertValid(
            "https://api.esse-analytics.com/base/",
            stored = "https://api.esse-analytics.com/base",
            base = "https://api.esse-analytics.com/base",
        )
    }

    @Test
    fun `acepta esquema en mayusculas`() {
        assertValid("HTTP://192.168.1.50:4000", stored = "HTTP://192.168.1.50:4000", base = "HTTP://192.168.1.50:4000")
    }

    @Test
    fun `acepta preset laboratorio del emulador`() {
        assertValid("http://10.0.2.2:5055", stored = "http://10.0.2.2:5055", base = "http://10.0.2.2:5055")
    }

    @Test
    fun `sin esquema es invalido`() {
        assertInvalid("192.168.1.50:4000")
    }

    @Test
    fun `esquema que no es http es invalido`() {
        assertInvalid("ftp://192.168.1.50:4000")
    }

    @Test
    fun `host vacio es invalido`() {
        assertInvalid("http://")
    }

    @Test
    fun `host vacio con puerto es invalido`() {
        assertInvalid("http://:4000")
    }

    @Test
    fun `texto libre sin formato de url es invalido`() {
        assertInvalid("mi servidor local")
    }

    private fun assertValid(raw: String, stored: String, base: String) {
        val result = ServerUrlRules.validate(raw)
        assertTrue("se esperaba Valid para '$raw' pero fue $result", result is ServerUrlValidation.Valid)
        result as ServerUrlValidation.Valid
        assertEquals(stored, result.storedValue)
        assertEquals(base, result.healthCheckBase)
    }

    private fun assertInvalid(raw: String) {
        assertTrue(
            "se esperaba Invalid para '$raw' pero fue ${ServerUrlRules.validate(raw)}",
            ServerUrlRules.validate(raw) is ServerUrlValidation.Invalid,
        )
    }
}
