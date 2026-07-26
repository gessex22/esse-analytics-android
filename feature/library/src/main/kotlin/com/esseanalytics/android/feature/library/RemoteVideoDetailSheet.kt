package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto

// Mirror de RemoteVideoDetailView (iOS) para un video que todavía vive SOLO
// en Nube -- ver RemoteVideoEditViewModel para por qué esto no puede
// reusar VideoDetailSheet (que opera sobre Room).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteVideoDetailSheet(
    video: RemoteLibraryVideoDto,
    onDismiss: () -> Unit,
    streamUrl: String? = null,
    onPublish: () -> Unit = {},
    viewModel: RemoteVideoEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(video._id) { viewModel.setInitial(video) }

    val current by viewModel.video.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val player = remember(streamUrl) {
        streamUrl?.let { ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(it))
            playWhenReady = true
            prepare()
        } }
    }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player?.release() } }

    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    val shownVideo = current ?: video

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { PlayerView(context).apply { this.player = player } },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
            Text(shownVideo.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                listOfNotNull(shownVideo.resolution, shownVideo.formato).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )

                    }
                    item { Text("Estado por plataforma", style = MaterialTheme.typography.titleSmall) }
                    item {
            Platform.publishable.forEach { platform ->
                VideoDetailPlatformRow(
                    platform = platform,
                    status = when {
                        platform.apiValue in shownVideo.platforms -> PlatformBadgeState.PUBLISHED
                        platform.apiValue in shownVideo.platformsDiscarded -> PlatformBadgeState.DISCARDED
                        else -> PlatformBadgeState.PENDING
                    },
                    hasLink = shownVideo.platformLinks.any { it.platform == platform.apiValue && it.platformUrl != null },
                    onToggleStatus = { viewModel.togglePlatform(platform) },
                    onEditLink = {
                        linkEditorText = viewModel.existingLink(platform) ?: ""
                        linkEditorPlatform = platform
                    },
                )
            }

            errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
                    }
                    item {
                        androidx.compose.material3.TextButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                            Text("Publicar")
                        }
                        androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Cerrar", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

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
