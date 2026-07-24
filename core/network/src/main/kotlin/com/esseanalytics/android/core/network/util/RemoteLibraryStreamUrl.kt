package com.esseanalytics.android.core.network.util

import okhttp3.HttpUrl

// Mismo criterio que remoteLibraryThumbnailUrl: el JWT viaja como ?token= en
// vez de header Authorization porque esto se usa directo como fuente de
// ExoPlayer (MediaItem.fromUri), que no puede setear headers custom acá sin
// un HttpDataSource.Factory propio. A diferencia de la miniatura, no hace
// falta un campo "existe" del lado del DTO -- el endpoint stream ya devuelve
// 404 si el archivo no está, y el reproductor lo muestra como error de
// reproducción (ver VideoPlayerDialog). Null solo si no hay sesión.
fun remoteLibraryStreamUrl(baseUrl: HttpUrl, videoId: String, token: String?): String? {
    if (token == null) return null
    return baseUrl.newBuilder()
        .addPathSegments("api/remote-library/videos/$videoId/stream")
        .addQueryParameter("token", token)
        .build()
        .toString()
}
