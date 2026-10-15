package com.esseanalytics.android.feature.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.Data
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto

private val previewUploadingBatch = PublishBatchState(
    operationId = "preview-uploading",
    fileId = -1L,
    fileDisplayName = "clip-lanzamiento.mp4",
    stage = BatchStage.UPLOADING,
    platforms = listOf(
        PlatformPublishState(Platform.YOUTUBE, PlatformPublishStage.PENDING),
        PlatformPublishState(Platform.INSTAGRAM, PlatformPublishStage.UPLOADING, progress = .62f),
        PlatformPublishState(Platform.TIKTOK, PlatformPublishStage.CANCELLED),
    ),
)

private val previewPartialBatch = PublishBatchState(
    operationId = "preview-partial",
    fileId = -1L,
    fileDisplayName = "clip-lanzamiento.mp4",
    stage = BatchStage.PARTIAL_FAILURE,
    platforms = listOf(
        PlatformPublishState(Platform.YOUTUBE, PlatformPublishStage.SUCCESS),
        PlatformPublishState(
            Platform.INSTAGRAM,
            PlatformPublishStage.FAILED,
            error = "No se pudo confirmar la respuesta del servidor; revisá el estado de publicación antes de volver a intentarlo.",
        ),
        PlatformPublishState(Platform.TIKTOK, PlatformPublishStage.CANCELLED),
    ),
)

private val previewCloudVideos = listOf(
    RemoteLibraryVideoDto(
        _id = "gallery-preview-1",
        fileName = "ensayo-general-con-un-nombre-largo-para-revisar-el-ajuste.mp4",
        sizeBytes = 38_000_000,
        platforms = listOf("youtube", "instagram"),
        platformsDiscarded = listOf("tiktok"),
    ),
    RemoteLibraryVideoDto(
        _id = "gallery-preview-2",
        fileName = "video-pendiente.mp4",
        sizeBytes = 12_000_000,
        platformsDiscarded = listOf("instagram"),
    ),
    RemoteLibraryVideoDto(
        _id = "gallery-preview-3",
        fileName = "publicacion-con-facebook.mp4",
        sizeBytes = 24_000_000,
        platforms = listOf("facebook", "tiktok"),
    ),
)

@Composable
private fun UploadStudioPreviewContent(dark: Boolean = false) {
    CreatorHomeTheme(darkTheme = dark) {
        Box {
            CreatorStudioBackdrop()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UploadStudioHeader("Luna", onProfileClick = {})
                UploadStudioCard {
                    Text("clip-lanzamiento.mp4", style = MaterialTheme.typography.titleMedium)
                    UploadStudioSecondaryButton("Elegir otro video", onClick = {})
                }
                UploadStudioDetailsFields(
                    title = "Un título largo que puede ocupar más de una línea en una pantalla angosta",
                    onTitleChange = {},
                    description = "Descripción de ejemplo para revisar el formulario con texto ampliado.",
                    onDescriptionChange = {},
                    enabled = true,
                )
                UploadStudioCoverPicker(18f, 93, {}, {}) {
                    Box(
                        Modifier.fillMaxWidth().height(170.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("Vista previa de portada", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                UploadStudioSection("Plataformas", "Estados de ejemplo; no son datos de una cuenta.") {
                    UploadStudioCard(contentPadding = 10.dp, contentSpacing = 4.dp) {
                        PreviewPlatformRow(Platform.YOUTUBE, true, UploadStudioPlatformPresentation(UploadStudioPlatformState.RUNNING, "62%", progress = .62f))
                        UploadStudioPlatformRow(
                            label = platformFullLabel(Platform.INSTAGRAM),
                            icon = platformIcon(Platform.INSTAGRAM),
                            brandColor = platformColor(Platform.INSTAGRAM),
                            checked = true,
                            enabled = true,
                            alreadyDone = false,
                            presentation = UploadStudioPlatformPresentation(UploadStudioPlatformState.SUCCEEDED, "Subido ✓"),
                            onCheckedChange = {},
                        )
                        PreviewPlatformRow(Platform.TIKTOK, false, UploadStudioPlatformPresentation(UploadStudioPlatformState.FAILED, "No se pudo completar la subida.", error = true))
                        PreviewPlatformRow(Platform.YOUTUBE, true, UploadStudioPlatformPresentation(UploadStudioPlatformState.SUCCEEDED, "Ya publicado"))
                        FacebookCrossPostRow(
                            checked = true,
                            enabled = true,
                            onCheckedChange = {},
                            presentation = facebookCrossPostPresentation(
                                Data.Builder().putString(UploadWorker.KEY_FACEBOOK_URL, "https://preview.invalid").build(),
                            ),
                        )
                    }
                }
                UploadStudioPublishButton(isPublishing = false, enabled = true, onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PreviewPlatformRow(platform: Platform, checked: Boolean, presentation: UploadStudioPlatformPresentation) {
    UploadStudioPlatformRow(
        label = platformFullLabel(platform),
        icon = platformIcon(platform),
        brandColor = platformColor(platform),
        checked = checked,
        enabled = true,
        alreadyDone = false,
        presentation = presentation,
        onCheckedChange = {},
    )
}

@Preview(name = "Subir · claro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun UploadStudioLightPreview() = UploadStudioPreviewContent()

@Preview(name = "Subir · oscuro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun UploadStudioDarkPreview() = UploadStudioPreviewContent(dark = true)

@Preview(name = "Subir · compacto", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun UploadStudioCompactPreview() = UploadStudioPreviewContent()

@Preview(name = "Subir · Facebook deshabilitado", showBackground = true, widthDp = 320, heightDp = 320, fontScale = 1.3f)
@Composable
private fun UploadStudioFacebookDisabledPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            UploadStudioCard(modifier = Modifier.padding(12.dp), contentPadding = 10.dp, contentSpacing = 4.dp) {
                FacebookCrossPostRow(checked = true, enabled = false, onCheckedChange = {})
            }
        }
    }
}

@Preview(name = "Subir · vacío", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun UploadStudioEmptyPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            UploadStudioEmptyState("Luna", onProfileClick = {})
        }
    }
}

@Preview(name = "Subir · paisaje 800×400", showBackground = true, widthDp = 800, heightDp = 400)
@Composable
private fun UploadStudioLandscapePreview() = UploadStudioPublishFooterPreview()

@Preview(name = "Subir · compacto 320×400", showBackground = true, widthDp = 320, heightDp = 400)
@Composable
private fun UploadStudioShortViewportPreview() = UploadStudioPublishFooterPreview()

@Composable
private fun UploadStudioPublishFooterPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UploadStudioHeader("Luna", onProfileClick = {})
                UploadStudioDetailsFields("Título del video", {}, "Descripción de ejemplo", {}, enabled = true)
                UploadStudioCoverPicker(
                    offsetSec = 0f,
                    durationSec = 10,
                    onOffsetChange = {},
                    onOffsetChangeFinished = {},
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("Vista previa de portada", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                UploadStudioPublishButton(isPublishing = false, enabled = true, onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(name = "Selector · importados", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun UploadStudioSourcesPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UploadStudioHeader("Luna", onProfileClick = {}, subtitle = "Elegí un video para continuar.")
                Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                    UploadGalleryAction(isLoading = false, error = null, enabled = true, onClick = {})
                }
                UploadPickerSources(UploadPickerSource.entries, UploadPickerSource.IMPORTED, enabled = true, onSelect = {})
                FileList(
                    files = listOf(
                        VideoFile(
                            fileName = "video-capturado.mp4",
                            filePath = "",
                            status = FileStatus.PENDIENTE,
                            contentStatus = ContentStatus.BORRADOR,
                            platforms = listOf(Platform.TIKTOK),
                        ),
                    ),
                    nextUploads = mapOf(Platform.YOUTUBE to "video-capturado.mp4"),
                    onSelect = {},
                )
            }
        }
    }
}

@Preview(name = "Subir · progreso y recuperación", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun UploadStudioBatchPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PublishingBatchBlock(previewUploadingBatch, onCancel = {})
                UploadStudioCard {
                    BatchResultSummary(
                        batch = previewPartialBatch,
                        blockedPlatforms = setOf(Platform.INSTAGRAM),
                        confirmationError = null,
                        onRetryFailed = {},
                        onConfirmNotPublished = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "Subir · error largo compacto", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun UploadStudioBatchCompactPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                PublishingBatchBlock(previewUploadingBatch, onCancel = {})
                UploadStudioCard {
                    BatchResultSummary(
                        batch = previewPartialBatch,
                        blockedPlatforms = setOf(Platform.INSTAGRAM),
                        confirmationError = null,
                        onRetryFailed = {},
                        onConfirmNotPublished = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "Galería · CTA, carga y error", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
private fun UploadGalleryActionStatesPreview() {
    CreatorHomeTheme {
        Box {
            CreatorStudioBackdrop()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UploadStudioHeader("Luna", onProfileClick = {})
                UploadStudioSection("En este teléfono", "La galería se puede abrir aunque todavía no haya videos importados.") {
                    UploadGalleryAction(isLoading = false, error = null, enabled = true, onClick = {})
                    Text("Todavía no hay videos importados.", style = MaterialTheme.typography.bodySmall)
                }
                UploadStudioSection("Importación en curso", "El estado pertenece al import actual.") {
                    UploadGalleryAction(isLoading = true, error = null, enabled = false, onClick = {})
                }
                UploadStudioSection("Error recuperable", "Podés elegir otro archivo.") {
                    UploadGalleryAction(
                        isLoading = false,
                        error = "No se pudo leer el video seleccionado. Volvé a elegirlo.",
                        enabled = true,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "Selector · compacto con texto grande", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun UploadPickerCompactPreview() = UploadPickerPreview()

@Preview(name = "Selector · oscuro", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun UploadPickerDarkPreview() = UploadPickerPreview(dark = true)

@Preview(name = "Selector · ancho", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
private fun UploadPickerWidePreview() = UploadPickerPreview()

@Preview(name = "Selector · nube", showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun UploadPickerLightPreview() = UploadPickerPreview()

@Composable
private fun UploadPickerPreview(dark: Boolean = false) {
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(UploadPickerSource.CLOUD) }
    CreatorHomeTheme(darkTheme = dark) {
        Box {
            CreatorStudioBackdrop()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") { UploadStudioHeader("Luna", onProfileClick = {}, subtitle = "Elegí un video para continuar.") }
                item(key = "gallery") {
                    Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                        UploadGalleryAction(isLoading = false, error = null, enabled = true, onClick = {})
                    }
                }
                item(key = "sources") {
                    UploadPickerSources(UploadPickerSource.entries, selected, enabled = true, onSelect = { selected = it })
                }
                if (selected == UploadPickerSource.CLOUD) {
                    items(previewCloudVideos, key = { it._id }) { video ->
                        UploadCloudVideoCard(
                            video = video,
                            importing = false,
                            enabled = true,
                            nextUploads = mapOf(Platform.YOUTUBE to "video-pendiente.mp4"),
                            thumbnailUrl = null,
                            onClick = {},
                        )
                    }
                } else {
                    item(key = "empty") { SourceStatusRow("No hay videos en esta fuente.") }
                }
            }
        }
    }
}
