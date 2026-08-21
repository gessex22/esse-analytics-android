package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LocalPcUpdatePlatformsRequest(
    val platforms: List<String>,
    val platforms_discarded: List<String>,
)

@Serializable
data class LocalPcSetPlatformLinkRequest(val url: String?)

@Serializable
data class LocalPcPlatformLinksDto(
    val youtube: String? = null,
    val instagram: String? = null,
    val tiktok: String? = null,
) {
    fun urlFor(platform: String): String? = when (platform) {
        "youtube" -> youtube
        "instagram" -> instagram
        "tiktok" -> tiktok
        else -> null
    }
}
