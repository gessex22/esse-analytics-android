package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    val context = LocalContext.current
    val player = remember(streamUrl) {
        streamUrl?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(it))
                playWhenReady = true
                prepare()
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    VideoDetailDialog(
        title = shown.fileName,
        metadata = listOfNotNull(
            shown.resolucion?.let { "Resolución: $it" },
            shown.duracion_segundos?.let { "Duración: ${it}s" },
            shown.formato?.let { "Formato: $it" },
        ).joinToString(" · "),
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
        player = {
            AndroidView(
                factory = { PlayerView(context).apply { this.player = player } },
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        },
        actions = {
            TextButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                Text("Publicar")
            }
        },
    )

    linkEditorPlatform?.let { platform ->
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
