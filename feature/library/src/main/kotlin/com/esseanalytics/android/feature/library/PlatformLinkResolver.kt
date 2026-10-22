package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal object PlatformLinkResolver {
    fun isValid(platform: Platform, value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() } || !text.startsWith("http://", true) && !text.startsWith("https://", true)) return false
        val url = text.toHttpUrlOrNull() ?: return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.port != if (url.isHttps) 443 else 80) return false
        val path = url.encodedPath
        return when (platform) {
            Platform.YOUTUBE -> when (url.host) {
                "youtu.be", "www.youtu.be" -> Regex("""/[a-zA-Z0-9_-]{6,}/?""").matches(path)
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                    path == "/watch" && Regex("""[a-zA-Z0-9_-]{6,}""").matches(url.queryParameter("v") ?: "") ||
                        Regex("""/(?:shorts|live|embed)/[a-zA-Z0-9_-]{6,}/?""").matches(path)
                else -> false
            }
            Platform.INSTAGRAM -> url.host in setOf("instagram.com", "www.instagram.com", "m.instagram.com") &&
                Regex("""/(?:reel|p|tv)/[a-zA-Z0-9_-]+/?""").matches(path)
            Platform.TIKTOK -> when (url.host) {
                "vm.tiktok.com", "vt.tiktok.com" -> Regex("""/[a-zA-Z0-9_-]+/?""").matches(path)
                "tiktok.com", "www.tiktok.com", "m.tiktok.com" ->
                    Regex("""/@[^/\s]+/video/\d+/?""").matches(path) || Regex("""/t/[a-zA-Z0-9_-]+/?""").matches(path)
                else -> false
            }
            Platform.FACEBOOK -> false // Facebook solo se publica como destino de crosspost.
        }
    }

    fun validationMessage(platform: Platform): String = "Pegá un enlace válido de una publicación de ${platform.displayName}."

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
        if (platform != Platform.TIKTOK || !isValid(platform, url)) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        val isShortLink = parsed.host == "vm.tiktok.com" ||
            parsed.host == "vt.tiktok.com" ||
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
        if (!isValid(platform, url)) return url
        val parsed = url.toHttpUrlOrNull()
        if (platform == Platform.YOUTUBE && parsed?.encodedPath == "/watch") return parsed.queryParameter("v") ?: url
        val segments = parsed?.encodedPath?.split('/')?.filter { it.isNotEmpty() } ?: return url
        if (platform == Platform.TIKTOK && (segments.size != 3 || segments[1] != "video")) return url
        return segments.lastOrNull() ?: url
    }
}
