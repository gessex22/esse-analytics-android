package com.esseanalytics.android.feature.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.TiktokCreatorInfoDto

@Composable
private fun RefinedPublicationPreview(dark: Boolean = false, cancelled: Boolean = false) {
    val options = PlatformPublicationOptions(tiktokPrivacy = "MUTUAL_FOLLOW_FRIENDS")
    CreatorHomeTheme(darkTheme = dark) {
        Box {
            CreatorStudioBackdrop()
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    UploadStudioHeader("Esse", {}, subtitle = "Prepara tu contenido y elige dónde publicarlo.")
                    UploadStudioSelectedVideo("Nuevo video 1.mp4", 48, {}) {
                        Box(Modifier.size(72.dp, 48.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.VideoLibrary, contentDescription = null)
                        }
                    }
                    if (cancelled) {
                        UploadStudioCard {
                            BatchResultSummary(
                                PublishBatchState("preview-cancelled", -1, "Nuevo video 1.mp4", BatchStage.CANCELLED,
                                    listOf(PlatformPublishState(Platform.INSTAGRAM, PlatformPublishStage.CANCELLED))),
                                emptySet(), null, {}, {}, {},
                            )
                        }
                    }
                    UploadStudioSection("Contenido") { UploadStudioDetailsFields("Mi próxima publicación", {}, "Una idea para compartir con mi comunidad.", {}, true) }
                    UploadStudioSection("Publicar en", "Elige tus plataformas y revisa sus ajustes.") {
                        Platform.publishable.forEach { platform ->
                            UploadStudioCard(contentPadding = 12.dp, borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
                                UploadStudioPlatformRow(
                                    platform.displayName, platformIcon(platform), platformColor(platform), true, true, false,
                                    UploadStudioPlatformPresentation(UploadStudioPlatformState.AVAILABLE), onCheckedChange = {},
                                    extraContent = { PlatformPublicationConfiguration(platform, options, {}, true) },
                                )
                            }
                        }
                    }
                }
                UploadStudioPublishFooter(Platform.publishable.toSet(), false, true, null, { true })
            }
        }
    }
}

@Preview(name = "Publicar · refinado claro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun RefinedLightPreview() = RefinedPublicationPreview()

@Preview(name = "Publicar · refinado oscuro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun RefinedDarkPreview() = RefinedPublicationPreview(dark = true)

@Preview(name = "Publicar · cancelación cerrable", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun RefinedCancelledPreview() = RefinedPublicationPreview(cancelled = true)

@Preview(name = "Publicar · ventana pequeña", showBackground = true, widthDp = 320, heightDp = 480)
@Composable private fun RefinedSmallPreview() = RefinedPublicationPreview()

@Preview(name = "Publicar · horizontal", showBackground = true, widthDp = 800, heightDp = 400)
@Composable private fun RefinedLandscapePreview() = RefinedPublicationPreview()

@Preview(name = "Deslizar · cuatro destinos", showBackground = true, widthDp = 320, heightDp = 200)
@Composable private fun SlideFourDestinationsPreview() {
    CreatorHomeTheme {
        UploadStudioPublishFooter((Platform.publishable + Platform.FACEBOOK).toSet(), false, true, null, { true }, navigationClearance = 16.dp)
    }
}

@Preview(name = "Deslizar · publicando", showBackground = true, widthDp = 360, heightDp = 200)
@Composable private fun SlidePublishingPreview() {
    CreatorHomeTheme {
        UploadStudioPublishFooter(setOf(Platform.INSTAGRAM), true, false, null, { true }, navigationClearance = 16.dp)
    }
}

@Preview(name = "Deslizar · validación", showBackground = true, widthDp = 320, heightDp = 220, fontScale = 1.5f)
@Composable private fun SlideValidationPreview() {
    CreatorHomeTheme {
        UploadStudioPublishFooter(setOf(Platform.TIKTOK), false, false, "Elige quién puede ver la publicación de TikTok.", { true }, navigationClearance = 16.dp)
    }
}

@Preview(name = "Ajustes · TikTok", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun RefinedTikTokSettingsPreview() {
    CreatorHomeTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("TikTok", style = MaterialTheme.typography.headlineSmall)
                PublicationSettingsContent(
                    Platform.TIKTOK, PlatformPublicationOptions(tiktokPrivacy = "MUTUAL_FOLLOW_FRIENDS"), {}, true,
                    TiktokCreatorInfoDto(nickname = "Creadora", privacyOptions = listOf("PUBLIC_TO_EVERYONE", "MUTUAL_FOLLOW_FRIENDS", "SELF_ONLY"), duetDisabled = true, maxVideoDurationSec = 60),
                )
                UploadStudioPrimaryButton("Listo", {}, Modifier.fillMaxWidth())
            }
        }
    }
}
