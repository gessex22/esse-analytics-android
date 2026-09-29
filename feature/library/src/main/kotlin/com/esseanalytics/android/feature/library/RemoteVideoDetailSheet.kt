package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Save
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
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto

@Composable
fun RemoteVideoDetailSheet(
    video: RemoteLibraryVideoDto,
    onDismiss: () -> Unit,
    streamUrl: String? = null,
    onPublish: () -> Unit = {},
    viewModel: RemoteVideoEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(video._id) {
        viewModel.setInitial(video)
        viewModel.refreshSavedState(video._id)
    }
    val current by viewModel.video.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val appSaveStatus by viewModel.appSaveStatus.collectAsState()
    val gallerySaveStatus by viewModel.gallerySaveStatus.collectAsState()
    val saveErrorMessage by viewModel.saveErrorMessage.collectAsState()
    val saveRunning = appSaveStatus is VideoSaveStatus.Running ||
        gallerySaveStatus is VideoSaveStatus.Running
    val shown = current ?: video
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
        metadata = listOfNotNull(shown.resolution, shown.formato).joinToString(" · "),
        onDismiss = onDismiss,
        stateFor = { platform ->
            when {
                platform.apiValue in shown.platforms -> PlatformBadgeState.PUBLISHED
                platform.apiValue in shown.platformsDiscarded -> PlatformBadgeState.DISCARDED
                else -> PlatformBadgeState.PENDING
            }
        },
        hasLink = { platform ->
            shown.platformLinks.any { it.platform == platform.apiValue && it.platformUrl != null }
        },
        onToggle = viewModel::togglePlatform,
        onEditLink = { platform ->
            linkEditorText = viewModel.existingLink(platform) ?: ""
            linkEditorPlatform = platform
        },
        errorMessage = errorMessage ?: saveErrorMessage,
        player = {
            AndroidView(
                factory = { PlayerView(context).apply { this.player = player } },
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        },
        actions = {
            DetailPrimaryAction(
                text = "Publicar",
                icon = Icons.Outlined.CloudUpload,
                enabled = !saveRunning,
                onClick = onPublish,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            DetailSaveAction(
                idleText = "Guardar en app",
                subtitle = "Disponible sin conexión dentro de EsseAnalytics",
                savedText = "Guardado en app",
                icon = Icons.Outlined.Save,
                status = appSaveStatus,
                enabled = !saveRunning,
                onClick = { viewModel.saveToApp(shown) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            DetailSaveAction(
                idleText = "Guardar en galería",
                subtitle = "Crea una copia en la galería del teléfono",
                savedText = "Guardado en galería",
                icon = Icons.Outlined.Download,
                status = gallerySaveStatus,
                enabled = !saveRunning,
                onClick = { viewModel.saveToGallery(shown) },
                modifier = Modifier.fillMaxWidth(),
            )
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
