package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.TiktokCreatorInfoDto
import org.junit.Assert.*
import org.junit.Test

class PlatformPublicationOptionsTest {
    private val creator = TiktokCreatorInfoDto(
        privacyOptions = listOf("PUBLIC_TO_EVERYONE", "MUTUAL_FOLLOW_FRIENDS", "SELF_ONLY"),
        duetDisabled = true, maxVideoDurationSec = 60,
    )

    @Test fun youtubeValuesAreIsolatedFromOtherPlatforms() {
        val options = PlatformPublicationOptions(youtubePrivacy = "unlisted", youtubeCategory = "27", youtubeMadeForKids = true, youtubeSyntheticMedia = true, youtubeTags = "uno, dos, uno")
        val metadata = options.metadataFor(Platform.YOUTUBE, "Título", "Descripción")
        assertEquals("unlisted", metadata.privacyStatus)
        assertEquals("27", metadata.categoryId)
        assertTrue(metadata.madeForKids)
        assertTrue(metadata.containsSyntheticMedia)
        assertEquals(listOf("uno", "dos"), metadata.tags)
        assertNull(options.metadataFor(Platform.INSTAGRAM, "Título", "Descripción").tiktokPrivacyLevel)
    }

    @Test fun instagramHashtagsAndFeedChoiceReachTheUploader() {
        val options = PlatformPublicationOptions(instagramHashtags = "#receta", instagramShareToFeed = false)
        val metadata = options.metadataFor(Platform.INSTAGRAM, "Título", "Descripción", crossPostFacebook = true)
        assertEquals("Título", metadata.title)
        assertEquals("#receta", metadata.description)
        assertFalse(metadata.shareToFeed)
        assertTrue(metadata.crossPostFacebook)
        assertTrue(options.metadataFor(Platform.YOUTUBE, "Título", "Descripción").shareToFeed)
    }

    @Test fun tiktokHonorsPrivacyInteractionsCommercialAndAiSelections() {
        val options = PlatformPublicationOptions(tiktokPrivacy = "MUTUAL_FOLLOW_FRIENDS", tiktokAllowComment = false, tiktokOwnBrand = true, tiktokPaidBrand = true, tiktokAiGenerated = true)
        assertNull(options.validationError(setOf(Platform.TIKTOK), creator, 30))
        val metadata = options.metadataFor(Platform.TIKTOK, "Título", "Descripción", creator = creator)
        assertEquals("MUTUAL_FOLLOW_FRIENDS", metadata.tiktokPrivacyLevel)
        assertEquals("Título", metadata.title)
        assertEquals("", metadata.description)
        assertTrue(metadata.disableComment)
        assertTrue(metadata.disableDuet) // restriction from the creator account
        assertFalse(metadata.disableStitch)
        assertTrue(metadata.brandOrganic)
        assertTrue(metadata.brandedContent)
        assertTrue(metadata.tiktokAiGenerated)
    }

    @Test fun tiktokRequiresAnExplicitAllowedPrivacyChoiceAndValidDuration() {
        val selected = setOf(Platform.TIKTOK)
        assertNotNull(PlatformPublicationOptions().validationError(selected, creator, 30))
        assertNotNull(PlatformPublicationOptions(tiktokPrivacy = "FOLLOWER_OF_CREATOR").validationError(selected, creator, 30))
        assertNotNull(PlatformPublicationOptions(tiktokPrivacy = "PUBLIC_TO_EVERYONE").validationError(selected, creator, 61))
        assertNotNull(PlatformPublicationOptions(tiktokPrivacy = "SELF_ONLY", tiktokPaidBrand = true).validationError(selected, creator, 30))
        assertNull(PlatformPublicationOptions().validationError(setOf(Platform.YOUTUBE), null, 30))
    }
}
