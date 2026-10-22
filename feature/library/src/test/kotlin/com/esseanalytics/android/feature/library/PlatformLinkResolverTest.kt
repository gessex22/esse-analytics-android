package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.Platform
import org.junit.Assert.*
import org.junit.Test

class PlatformLinkResolverTest {
    @Test fun acceptsPublicationAndShareLinks() {
        val cases = listOf(
            Platform.YOUTUBE to "https://youtube.com/watch?si=share&v=gfwF_sZOhE0",
            Platform.YOUTUBE to "https://youtu.be/gfwF_sZOhE0?t=4",
            Platform.YOUTUBE to "https://m.youtube.com/shorts/gfwF_sZOhE0",
            Platform.YOUTUBE to "https://youtube.com/live/gfwF_sZOhE0",
            Platform.INSTAGRAM to "https://instagram.com/reel/ABC123/?igsh=share",
            Platform.INSTAGRAM to "https://instagram.com/p/ABC123/",
            Platform.INSTAGRAM to "https://instagram.com/tv/ABC123/",
            Platform.TIKTOK to "https://tiktok.com/@fixture/video/1234567890",
            Platform.TIKTOK to "https://vm.tiktok.com/ZMfixture/",
            Platform.TIKTOK to "https://vt.tiktok.com/ZMfixture/",
            Platform.TIKTOK to "https://tiktok.com/t/ZMfixture/",
        )
        cases.forEach { (platform, url) -> assertTrue(url, PlatformLinkResolver.isValid(platform, url)) }
        assertEquals("gfwF_sZOhE0", PlatformLinkResolver.extractPlatformId(Platform.YOUTUBE, cases.first().second))
    }

    @Test fun rejectsProfilesOtherDomainsAndPlainText() {
        Platform.publishable.forEach { platform ->
            listOf("texto", "", "javascript:alert(1)", "https://example.com/video/123",
                "https://youtube.com.evil.test/watch?v=gfwF_sZOhE0", "https://youtube.com@evil.test/watch?v=gfwF_sZOhE0",
                "https://youtube.com:8080/watch?v=gfwF_sZOhE0").forEach { url ->
                assertFalse(url, PlatformLinkResolver.isValid(platform, url))
            }
        }
        assertFalse(PlatformLinkResolver.isValid(Platform.YOUTUBE, "https://youtube.com/@fixture"))
        assertFalse(PlatformLinkResolver.isValid(Platform.INSTAGRAM, "https://instagram.com/fixture/"))
        assertFalse(PlatformLinkResolver.isValid(Platform.TIKTOK, "https://tiktok.com/@fixture"))
        assertFalse(PlatformLinkResolver.isValid(Platform.INSTAGRAM, "https://youtu.be/gfwF_sZOhE0"))
    }
}
