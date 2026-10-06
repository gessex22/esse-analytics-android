package com.esseanalytics.android.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

internal fun providerForHost(host: String): String? = when {
    host == "googleapis.com" || host.endsWith(".googleapis.com") -> "youtube"
    host == "graph.facebook.com" || host == "graph.instagram.com" || host == "rupload.facebook.com" -> "instagram"
    host == "tiktokapis.com" || host.endsWith(".tiktokapis.com") -> "tiktok"
    else -> null
}

class PlatformUsageInterceptor(private val begin: (String) -> ((Boolean) -> Unit)) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val provider = providerForHost(chain.request().url.host)
        val complete = provider?.let { runCatching { begin(it) }.getOrNull() }
        try {
            val response = chain.proceed(chain.request())
            complete?.let { runCatching { it(response.code >= 400) } }
            return response
        } catch (error: Exception) {
            complete?.let { runCatching { it(true) } }
            throw error
        }
    }
}
