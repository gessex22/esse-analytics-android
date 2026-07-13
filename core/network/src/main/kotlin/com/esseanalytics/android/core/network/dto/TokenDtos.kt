package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

// Lo que devuelven GET /api/{platform}/token — el cliente usa esto DIRECTO
// contra graph.facebook.com / open.tiktokapis.com / googleapis.com, la
// central nunca ve los bytes del video.
@Serializable
data class InstagramTokenResponse(val access_token: String, val instagram_user_id: String, val page_id: String? = null)

@Serializable
data class TiktokTokenResponse(val access_token: String, val open_id: String)

@Serializable
data class YoutubeTokenResponse(val access_token: String)

@Serializable
data class AuthUrlResponse(val url: String)

@Serializable
data class SetYoutubeThumbnailRequest(val imageBase64: String)
