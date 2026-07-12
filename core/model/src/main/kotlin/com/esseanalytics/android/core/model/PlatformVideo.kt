package com.esseanalytics.android.core.model

import java.time.Instant

// Mirror del "platform_videos" de local-backend — el vínculo entre un archivo
// local y su publicación real en una plataforma.
data class PlatformVideo(
    val id: Long = 0,
    val platform: Platform,
    val platformId: String,
    val platformUrl: String?,
    val publishedAt: Instant?,
    val linkedFileId: Long?,
    val matchStatus: String,
    val title: String? = null,
    val description: String? = null,
    val views: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0,
)
