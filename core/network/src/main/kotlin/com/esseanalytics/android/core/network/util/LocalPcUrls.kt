package com.esseanalytics.android.core.network.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// Mismo criterio que remoteLibraryStreamUrl/remoteLibraryThumbnailUrl: el JWT
// viaja como ?token=, no como header Authorization, porque esto se usa
// directo como fuente de ExoPlayer (MediaItem.fromUri)/Coil (AsyncImage),
// que no pueden setear headers custom sin un HttpDataSource.Factory/
// ImageLoader propio. Devuelve null solo si no hay sesión o la baseUrl de la
// PC descubierta no es una URL válida.
fun localPcStreamUrl(baseUrl: String, videoId: String, token: String?): String? {
    if (token == null) return null
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    return base.newBuilder()
        .addPathSegments("api/videos/stream/$videoId")
        .addQueryParameter("token", token)
        .build()
        .toString()
}

fun localPcThumbnailUrl(baseUrl: String, videoId: String, token: String?): String? {
    if (token == null) return null
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    return base.newBuilder()
        .addPathSegments("api/videos/$videoId/thumbnail")
        .addQueryParameter("token", token)
        .build()
        .toString()
}
