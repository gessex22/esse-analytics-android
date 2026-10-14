package com.esseanalytics.android.feature.upload

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role

@Composable
internal fun UploadStudioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    contentSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        contentColor = if (containerColor == MaterialTheme.colorScheme.primaryContainer) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = { content() },
        )
    }
}

@Composable
internal fun UploadStudioSection(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = CreatorSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
internal fun UploadStudioSectionTitle(index: String, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(index, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = CreatorSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun UploadStudioPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun UploadStudioSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun UploadStudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    if (primary) UploadStudioPrimaryButton(text, onClick, modifier, enabled)
    else UploadStudioSecondaryButton(text, onClick, modifier, enabled)
}

@Composable
internal fun UploadStudioPublishButton(
    isPublishing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UploadStudioPrimaryButton(
        text = if (isPublishing) "Publicando…" else "Publicar",
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
internal fun UploadGalleryAction(
    isLoading: Boolean,
    error: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    UploadStudioPrimaryButton(
        text = if (isLoading) "Importando video…" else "Elegir desde la galería",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !isLoading,
    )
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
            strokeWidth = 2.dp,
        )
    }
    error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
internal fun UploadCloudVideoGridRow(
    videos: List<RemoteLibraryVideoDto>,
    columns: Int,
    importingId: String?,
    importInProgress: Boolean,
    nextUploads: Map<Platform, String>,
    thumbnailUrl: (RemoteLibraryVideoDto) -> String?,
    onSelect: (RemoteLibraryVideoDto) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        videos.forEach { video ->
            key(video._id) {
                UploadCloudVideoCard(
                    video = video,
                    importing = importingId == video._id,
                    enabled = !importInProgress,
                    nextUploads = nextUploads,
                    thumbnailUrl = thumbnailUrl(video),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(video) },
                )
            }
        }
        repeat((columns - videos.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UploadCloudVideoCard(
    video: RemoteLibraryVideoDto,
    importing: Boolean,
    enabled: Boolean,
    nextUploads: Map<Platform, String>,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.clip(shape).clickable(
            enabled = enabled,
            onClick = onClick,
            role = Role.Button,
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Miniatura de ${video.fileName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = "Sin miniatura para ${video.fileName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                }
                if (importing) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .45f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    video.fileName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )
                val published = video.platforms.mapNotNull(Platform::fromApiValue).toSet()
                val visiblePlatforms = Platform.publishable + if (Platform.FACEBOOK in published) listOf(Platform.FACEBOOK) else emptyList()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visiblePlatforms.forEach { platform ->
                        val state = when {
                            platform in published -> UploadCloudPlatformBadgeState.PUBLISHED
                            platform.apiValue in video.platformsDiscarded -> UploadCloudPlatformBadgeState.DISCARDED
                            else -> UploadCloudPlatformBadgeState.PENDING
                        }
                        UploadCloudPlatformBadge(platform, state)
                    }
                    Platform.publishable.filter { nextUploads[it] == video.fileName }.forEach { platform ->
                        Text(
                            "Próximo · ${uploadCloudPlatformShortLabel(platform)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = uploadCloudPlatformColor(platform),
                        )
                    }
                }
            }
        }
    }
}

internal enum class UploadCloudPlatformBadgeState { PUBLISHED, PENDING, DISCARDED }

@Composable
internal fun UploadCloudPlatformBadge(platform: Platform, state: UploadCloudPlatformBadgeState) {
    val label = when (state) {
        UploadCloudPlatformBadgeState.PUBLISHED -> "publicado"
        UploadCloudPlatformBadgeState.PENDING -> "pendiente"
        UploadCloudPlatformBadgeState.DISCARDED -> "descartado"
    }
    val tint = if (state == UploadCloudPlatformBadgeState.PUBLISHED) {
        uploadCloudPlatformColor(platform)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = when (platform) {
                Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
                Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
                Platform.TIKTOK -> PlatformIcons.TiktokLogo
                Platform.FACEBOOK -> PlatformIcons.FacebookLogo
            },
            contentDescription = "${platform.displayName}: $label",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        if (state == UploadCloudPlatformBadgeState.DISCARDED) {
            Text("╱", color = tint, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

private fun uploadCloudPlatformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> FacebookBlue
}

private fun uploadCloudPlatformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}

internal enum class UploadStudioPlatformState { AVAILABLE, PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }

internal data class UploadStudioPlatformPresentation(
    val state: UploadStudioPlatformState,
    val statusText: String? = null,
    val progress: Float? = null,
    val error: Boolean = false,
)

/** Pure mapping shared by live platform rows and previews. */
internal fun uploadStudioPlatformPresentation(
    alreadyDone: Boolean,
    queued: Boolean,
    runningProgress: Float?,
    succeeded: Boolean,
    failureMessage: String?,
): UploadStudioPlatformPresentation = when {
    alreadyDone -> UploadStudioPlatformPresentation(UploadStudioPlatformState.SUCCEEDED, "Ya publicado")
    runningProgress != null -> UploadStudioPlatformPresentation(
        UploadStudioPlatformState.RUNNING,
        "${(runningProgress * 100).toInt()}%",
        progress = runningProgress,
    )
    queued -> UploadStudioPlatformPresentation(UploadStudioPlatformState.PENDING, "En cola…")
    succeeded -> UploadStudioPlatformPresentation(UploadStudioPlatformState.SUCCEEDED, "Subido ✓")
    failureMessage != null -> UploadStudioPlatformPresentation(
        UploadStudioPlatformState.FAILED,
        failureMessage,
        error = true,
    )
    else -> UploadStudioPlatformPresentation(UploadStudioPlatformState.AVAILABLE)
}

@Composable
internal fun UploadStudioPlatformStatus(presentation: UploadStudioPlatformPresentation) {
    val scheme = MaterialTheme.colorScheme
    when (presentation.state) {
        UploadStudioPlatformState.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { presentation.progress ?: 0f },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(presentation.statusText.orEmpty(), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
        }
        UploadStudioPlatformState.FAILED -> Text(
            presentation.statusText.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.error,
        )
        UploadStudioPlatformState.PENDING,
        UploadStudioPlatformState.SUCCEEDED -> presentation.statusText?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = if (presentation.state == UploadStudioPlatformState.SUCCEEDED) scheme.primary else scheme.onSurfaceVariant)
        }
        UploadStudioPlatformState.CANCELLED -> Text(
            "Cancelado",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        UploadStudioPlatformState.AVAILABLE -> Unit
    }
}

@Composable
internal fun UploadStudioHeader(
    username: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "Elegí un video y prepará su publicación.",
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Subir", fontFamily = CreatorSerif, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(10.dp))
        Surface(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onProfileClick)
                .semantics { contentDescription = "Abrir perfil de $username" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(username.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun UploadStudioEmptyState(username: String, onProfileClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp)) {
        UploadStudioHeader(username, onProfileClick)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PlaceholderScreen(
                title = "Nada para subir todavía",
                note = "Importá un video primero desde la pestaña Videos.",
                icon = Icons.Outlined.CloudUpload,
            )
        }
    }
}

@Composable
internal fun UploadStudioDetailsFields(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    enabled: Boolean,
) {
    UploadStudioCard(contentPadding = 16.dp, contentSpacing = 12.dp) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Título de la publicación") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Descripción") },
            placeholder = { Text("¿Qué quieres contar sobre este video?") },
            minLines = 2,
            maxLines = 5,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
internal fun UploadStudioPlatformRow(
    label: String,
    icon: ImageVector?,
    brandColor: Color,
    checked: Boolean,
    enabled: Boolean,
    alreadyDone: Boolean,
    presentation: UploadStudioPlatformPresentation,
    discarded: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .toggleable(
                    value = checked || alreadyDone,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .semantics {
                    contentDescription = buildString {
                        append(label)
                        append(if (checked || alreadyDone) ", seleccionado" else ", no seleccionado")
                        presentation.statusText?.let { append(", $it") }
                        if (discarded && !alreadyDone && presentation.state != UploadStudioPlatformState.SUCCEEDED) append(", descartado")
                        if (!enabled) append(", deshabilitado")
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    androidx.compose.material3.Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked || alreadyDone || presentation.state == UploadStudioPlatformState.SUCCEEDED) {
                            brandColor
                        } else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    if (discarded && !alreadyDone && presentation.state != UploadStudioPlatformState.SUCCEEDED) {
                        Text(
                            "╱",
                            color = scheme.onSurfaceVariant,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                UploadStudioPlatformStatus(presentation)
                if (presentation.state == UploadStudioPlatformState.AVAILABLE && !checked) {
                    Text("No incluida", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Checkbox(
                checked = checked || alreadyDone,
                enabled = enabled,
                onCheckedChange = null,
                modifier = Modifier.size(48.dp).clearAndSetSemantics {},
            )
        }
        extraContent()
    }
}

@Composable
internal fun UploadStudioCoverPicker(
    offsetSec: Float,
    durationSec: Int,
    onOffsetChange: (Float) -> Unit,
    onOffsetChangeFinished: () -> Unit,
    enabled: Boolean = true,
    frameContent: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val time = "${offsetSec.toInt() / 60}:${(offsetSec.toInt() % 60).toString().padStart(2, '0')}"
    UploadStudioCard(contentPadding = 12.dp, contentSpacing = 10.dp) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text("Portada del video", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Fotograma $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "Cerrar" else "Editar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) { frameContent() }
            Slider(
                value = offsetSec,
                onValueChange = onOffsetChange,
                onValueChangeFinished = onOffsetChangeFinished,
                enabled = enabled,
                valueRange = 0f..durationSec.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    time,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
internal fun UploadStudioSelectedVideo(
    fileName: String,
    durationSeconds: Int?,
    onChangeVideo: () -> Unit,
    thumbnail: @Composable () -> Unit,
) {
    UploadStudioCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            thumbnail()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Video seleccionado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(fileName.replace(Regex("\\.(mp4|mov|m4v|mkv|webm|avi)$", RegexOption.IGNORE_CASE), ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                durationSeconds?.takeIf { it > 0 }?.let {
                    Text("${it / 60}:${(it % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onChangeVideo) { Text("Cambiar") }
        }
    }
}

@Composable
internal fun UploadStudioPublishFooter(
    selectedPlatforms: Set<Platform>,
    isPublishing: Boolean,
    enabled: Boolean,
    message: String?,
    onPublish: () -> Boolean,
    navigationClearance: androidx.compose.ui.unit.Dp = 96.dp,
    confirmationKey: Any? = null,
    completedAttemptKey: Any? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navigationClearance), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            SlideToPublishBar(selectedPlatforms, enabled, isPublishing, onPublish, confirmationKey, completedAttemptKey)
        }
    }
}
