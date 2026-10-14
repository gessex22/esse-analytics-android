package com.esseanalytics.android.feature.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform

@Composable
internal fun VideoDetailDialog(
    title: String,
    metadata: String,
    playbackUri: Uri?,
    playbackErrorText: String,
    onDismiss: () -> Unit,
    stateFor: (Platform) -> PlatformBadgeState,
    hasLink: (Platform) -> Boolean,
    onToggle: (Platform) -> Unit,
    onEditLink: (Platform) -> Unit,
    errorMessage: String?,
    actions: @Composable () -> Unit,
) {
    var fullscreen by remember { mutableStateOf(false) }
    val dismiss = { if (fullscreen) fullscreen = false else onDismiss() }
    BackHandler(onBack = dismiss)
    val listState = rememberLazyListState()
    val playback = rememberVideoDetailPlayback(playbackUri, playbackErrorText)

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CreatorHomeTheme {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CreatorStudioBackdrop(Modifier.fillMaxSize())
                VideoDetailStudioContent(
                    title = title,
                    metadata = metadata,
                    fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onDismiss = dismiss,
                    playback = playback,
                    listState = listState,
                    stateFor = stateFor,
                    hasLink = hasLink,
                    onToggle = onToggle,
                    onEditLink = onEditLink,
                    errorMessage = errorMessage,
                    actions = actions,
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                )
            }
        }
    }
}

/** Pure layout used by previews too; playback is supplied as a slot and contains no app services. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun VideoDetailStudioContent(
    title: String,
    metadata: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
    playback: VideoDetailPlaybackState,
    listState: LazyListState,
    stateFor: (Platform) -> PlatformBadgeState,
    hasLink: (Platform) -> Boolean,
    onToggle: (Platform) -> Unit,
    onEditLink: (Platform) -> Unit,
    errorMessage: String?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Keep the single player host as the first child in both modes.
        BoxWithConstraints(
            Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier),
        ) {
            val maxPlayerHeight = (maxHeight * 0.40f).coerceAtMost(320.dp)
            val detailPlayerHeight = (maxWidth / playback.aspectRatio)
                .coerceAtMost(maxPlayerHeight)
                .coerceAtLeast(120.dp.coerceAtMost(maxPlayerHeight))
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.heightIn(min = detailPlayerHeight, max = detailPlayerHeight))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                playback.player(Modifier.fillMaxSize())
                if (playback.message != null) {
                    Text(playback.message, Modifier.align(Alignment.Center).padding(24.dp), color = Color.White, textAlign = TextAlign.Center)
                } else if (playback.isBuffering) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }
        }

        if (fullscreen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onToggleFullscreen,
                    enabled = true,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Black, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.FullscreenExit, contentDescription = null)
                    Text("Volver al detalle", modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp + 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "detail_header") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = CreatorSerif, fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (metadata.isNotBlank()) {
                            Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onToggleFullscreen,
                                enabled = playback.hasUri,
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = null)
                                Text("Pantalla completa", modifier = Modifier.padding(start = 8.dp))
                            }
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = null)
                                Text("Cerrar", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                item(key = "platform_status") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Estado por plataforma", style = MaterialTheme.typography.titleMedium.copy(fontFamily = CreatorSerif, fontWeight = FontWeight.Bold))
                            itemsPlatformRows(stateFor, hasLink, onToggle, onEditLink)
                        }
                    }
                }
                errorMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
                item(key = "detail_actions_and_close") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun itemsPlatformRows(
    stateFor: (Platform) -> PlatformBadgeState,
    hasLink: (Platform) -> Boolean,
    onToggle: (Platform) -> Unit,
    onEditLink: (Platform) -> Unit,
) {
    Platform.publishable.forEach { platform ->
        VideoDetailPlatformRow(platform, stateFor(platform), hasLink(platform), { onToggle(platform) }, { onEditLink(platform) })
    }
}
