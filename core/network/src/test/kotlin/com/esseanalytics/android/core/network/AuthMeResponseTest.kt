package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.dto.AuthMeResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthMeResponseTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `decodes central envelope and maps effective capabilities`() {
        val response = json.decodeFromString<AuthMeResponse>("""
            {
              "user": {
                "id": "user-1",
                "username": "esse",
                "role": "editor",
                "tier": "free",
                "isOwner": false,
                "hasCloudStorage": false,
                "theme": "dark"
              },
              "entitlements": {
                "userId": "user-1",
                "isOwner": false,
                "capabilities": {
                  "catalog.backup": {"enabled": true, "sources": ["subscription"]},
                  "library.cloud": {"enabled": false, "sources": []}
                }
              },
              "futureField": true
            }
        """.trimIndent())

        assertEquals("esse", response.user.username)
        assertEquals("user-1", response.entitlements?.userId)
        val domain = response.entitlements?.toDomain()
        assertTrue(domain?.capabilities?.get("catalog.backup")?.enabled == true)
        assertEquals(listOf("subscription"), domain?.capabilities?.get("catalog.backup")?.sources)
    }

    @Test
    fun `decodes envelope when entitlements are not yet included`() {
        val response = json.decodeFromString<AuthMeResponse>("""
            {
              "user": {
                "username": "legacy",
                "role": "editor",
                "tier": "free",
                "isOwner": false
              }
            }
        """.trimIndent())

        assertEquals("legacy", response.user.username)
        assertNull(response.entitlements)
    }

    @Test
    fun `decodes legacy flat user response without deriving entitlements`() {
        val response = json.decodeFromString<AuthMeResponse>("""
            {
              "id": "user-legacy",
              "username": "legacy-flat",
              "role": "editor",
              "tier": "premium",
              "isOwner": false,
              "hasCloudStorage": true
            }
        """.trimIndent())

        assertEquals("legacy-flat", response.user.username)
        assertNull(response.entitlements)
    }
}
