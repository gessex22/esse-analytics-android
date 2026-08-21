package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal object PlatformLinkResolver {
    fun nextCycle(
        platform: Platform,
        platforms: List<String>,
        discarded: List<String>,
    ): Pair<List<String>, List<String>> {
        val published = platforms.toMutableList()
        val discardedMutable = discarded.toMutableList()
        val key = platform.apiValue
        when {
            key in published -> {
                published.remove(key)
                if (key !in discardedMutable) discardedMutable.add(key)
            }
            key in discardedMutable -> discardedMutable.remove(key)
            else -> published.add(key)
        }
        return published to discardedMutable
    }

    suspend fun resolvedPlatformId(
        platform: Platform,
        url: String,
        client: OkHttpClient,
    ): String {
        val resolved = resolveShortLinkIfNeeded(platform, url, client) ?: url
        return extractPlatformId(platform, resolved)
    }

    suspend fun resolveShortLinkIfNeeded(
        platform: Platform,
        url: String,
        client: OkHttpClient,
    ): String? {
        if (platform != Platform.TIKTOK) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        val isShortLink = parsed.host.contains("vm.tiktok.com") ||
            parsed.host.contains("vt.tiktok.com") ||
            parsed.encodedPath.startsWith("/t/")
        if (!isShortLink) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(parsed).get().build()).execute().use {
                    it.request.url.toString()
                }
            }.getOrNull()
        }
    }

    fun extractPlatformId(platform: Platform, url: String): String {
        val pattern = when (platform) {
            Platform.YOUTUBE -> Regex("""(?:v=|youtu\.be/|shorts/)([\w-]{6,})""")
            Platform.INSTAGRAM -> Regex("""(?:reel|p)/([\w-]+)""")
            Platform.TIKTOK -> Regex("""video/(\d+)""")
            Platform.FACEBOOK -> return url
        }
        return pattern.find(url)?.groupValues?.getOrNull(1) ?: url
    }
}
