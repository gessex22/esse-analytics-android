package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.TiktokCreatorInfoDto

data class PlatformPublicationOptions(
    val youtubePrivacy: String = "public",
    val youtubeCategory: String = "22",
    val youtubeMadeForKids: Boolean = false,
    val youtubeSyntheticMedia: Boolean = false,
    val youtubeTags: String = "",
    val instagramHashtags: String = "",
    val instagramShareToFeed: Boolean = true,
    val tiktokPrivacy: String? = null,
    val tiktokAllowComment: Boolean = true,
    val tiktokAllowDuet: Boolean = true,
    val tiktokAllowStitch: Boolean = true,
    val tiktokOwnBrand: Boolean = false,
    val tiktokPaidBrand: Boolean = false,
    val tiktokAiGenerated: Boolean = false,
) {
    fun validationError(platforms: Set<Platform>, creator: TiktokCreatorInfoDto?, durationSeconds: Int? = null): String? {
        if (Platform.TIKTOK !in platforms) return null
        if (creator == null) return "Carga las opciones de la cuenta de TikTok antes de publicar."
        if (tiktokPrivacy == null || tiktokPrivacy !in creator.privacyOptions) return "Elige quién puede ver la publicación de TikTok."
        if (tiktokPaidBrand && tiktokPrivacy == "SELF_ONLY") return "La colaboración pagada de TikTok no puede ser privada."
        if (creator.maxVideoDurationSec > 0 && durationSeconds != null && durationSeconds > creator.maxVideoDurationSec) {
            return "Esta cuenta de TikTok admite videos de hasta ${creator.maxVideoDurationSec} segundos."
        }
        return null
    }

    fun metadataFor(
        platform: Platform,
        title: String,
        description: String,
        thumbnailOffsetMs: Long? = null,
        crossPostFacebook: Boolean = false,
        creator: TiktokCreatorInfoDto? = null,
    ): UploadMetadata = when (platform) {
        Platform.YOUTUBE -> UploadMetadata(
            title = title, description = description, thumbnailOffsetMs = thumbnailOffsetMs,
            privacyStatus = youtubePrivacy, categoryId = youtubeCategory, madeForKids = youtubeMadeForKids,
            containsSyntheticMedia = youtubeSyntheticMedia,
            tags = youtubeTags.split(',').map(String::trim).filter(String::isNotEmpty).distinct(),
        )
        Platform.INSTAGRAM -> UploadMetadata(
            title = title, description = listOf(description, instagramHashtags.trim()).filter(String::isNotBlank).joinToString("\n\n"),
            thumbnailOffsetMs = thumbnailOffsetMs, crossPostFacebook = crossPostFacebook,
            shareToFeed = instagramShareToFeed,
        )
        Platform.TIKTOK -> UploadMetadata(
            title = listOf(title, description).filter(String::isNotBlank).joinToString("\n\n"),
            thumbnailOffsetMs = thumbnailOffsetMs, tiktokPrivacyLevel = tiktokPrivacy,
            disableComment = !tiktokAllowComment || creator?.commentDisabled == true,
            disableDuet = !tiktokAllowDuet || creator?.duetDisabled == true,
            disableStitch = !tiktokAllowStitch || creator?.stitchDisabled == true,
            brandOrganic = tiktokOwnBrand, brandedContent = tiktokPaidBrand, tiktokAiGenerated = tiktokAiGenerated,
        )
        Platform.FACEBOOK -> error("Facebook se publica a través de Instagram.")
    }
}
