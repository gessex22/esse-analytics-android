package com.esseanalytics.android.feature.library

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.model.Platform

@Composable
fun LanVideoDetailSheet(
    item: LibraryListItem.LanVideo,
    streamUrl: String?,
    onDismiss: () -> Unit,
    onPublish: () -> Unit,
    viewModel: LanVideoDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(item.video._id, item.baseUrl) { viewModel.setInitial(item) }
    val current by viewModel.video.collectAsState()
    val links by viewModel.links.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val shown = current ?: item.video
    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    VideoDetailDialog(
        title = shown.fileName,
        metadata = listOfNotNull(
            shown.resolucion?.let { "Resolución: $it" },
            shown.duracion_segundos?.let { "Duración: ${it}s" },
            shown.formato?.let { "Formato: $it" },
        ).joinToString(" · "),
        playbackUri = streamUrl?.takeIf(String::isNotBlank)?.let(Uri::parse),
        playbackErrorText = "No hay una dirección de reproducción disponible para este video.",
        onDismiss = onDismiss,
        stateFor = { platform ->
            when {
                platform.apiValue in shown.platforms -> PlatformBadgeState.PUBLISHED
                platform.apiValue in shown.platforms_discarded -> PlatformBadgeState.DISCARDED
                else -> PlatformBadgeState.PENDING
            }
        },
        hasLink = { links.urlFor(it.apiValue) != null },
        onToggle = viewModel::togglePlatform,
        onEditLink = { platform ->
            linkEditorText = viewModel.existingLink(platform) ?: ""
            linkEditorPlatform = platform
        },
        errorMessage = errorMessage,
        actions = {
            DetailPrimaryAction("Publicar", Icons.Outlined.CloudUpload, true, onPublish, Modifier.fillMaxWidth())
        },
    )

    linkEditorPlatform?.let { platform ->
        com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme {
            LinkEditorDialog(
                platform = platform,
                text = linkEditorText,
                onTextChange = { linkEditorText = it },
                isSaving = isSaving,
                onDismiss = { linkEditorPlatform = null },
                onSave = {
                    viewModel.saveLink(platform, linkEditorText)
                    linkEditorPlatform = null
                },
            )
        }
    }
}
