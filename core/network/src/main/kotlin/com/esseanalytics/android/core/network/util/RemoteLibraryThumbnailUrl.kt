package com.esseanalytics.android.core.network.util

import okhttp3.HttpUrl

// Mirror de RemoteLibraryAPI.thumbnailURL en iOS -- el JWT viaja como ?token=
// (no como header Authorization) porque esto se usa directo como src de una
// imagen (AsyncImage/Coil), que no puede setear headers custom en la carga.
// Mismo criterio que streamURL (ver verifyTokenFromHeaderOrQuery en el
// backend). Null si el video no tiene miniatura generada o si no hay sesión.
fun remoteLibraryThumbnailUrl(
    baseUrl: HttpUrl,
    videoId: String,
    thumbnailStoredFileName: String?,
    token: String?,
): String? {
    if (thumbnailStoredFileName == null || token == null) return null
    return baseUrl.newBuilder()
        .addPathSegments("api/remote-library/videos/$videoId/thumbnail")
        .addQueryParameter("token", token)
        .build()
        .toString()
}
