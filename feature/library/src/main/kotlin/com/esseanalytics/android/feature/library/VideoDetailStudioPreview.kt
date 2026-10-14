package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform

@Preview(name = "Local · 320 dp · texto grande · final", widthDp = 320, heightDp = 760, fontScale = 1.3f)
@Composable
private fun VideoDetailLocalBottomPreview() = VideoDetailPreview("Local", ratio = 16f / 9f, bottomItem = true)

@Preview(name = "Nube · final con URL ausente", widthDp = 360, heightDp = 700)
@Composable
private fun VideoDetailCloudBottomPreview() = VideoDetailPreview("Nube", ratio = 9f / 16f, bottomItem = true, playbackMessage = "No hay una dirección de reproducción disponible para este video.")

@Preview(name = "Local · paisaje · final", widthDp = 800, heightDp = 400)
@Composable
private fun VideoDetailLocalLandscapeBottomPreview() = VideoDetailPreview("Local", ratio = 16f / 9f, bottomItem = true)

@Preview(name = "Nube · paisaje · final", widthDp = 800, heightDp = 400)
@Composable
private fun VideoDetailCloudLandscapeBottomPreview() = VideoDetailPreview("Nube", ratio = 16f / 9f, bottomItem = true)

@Preview(name = "PC · paisaje inline", widthDp = 800, heightDp = 400)
@Composable
private fun VideoDetailLandscapePreview() = VideoDetailPreview("PC", ratio = 16f / 9f)

@Preview(name = "Fullscreen · vertical", widthDp = 400, heightDp = 800)
@Composable
private fun VideoDetailVerticalFullscreenPreview() = VideoDetailPreview("PC", ratio = 9f / 16f, fullscreen = true)

@Preview(name = "Detalle · oscuro", widthDp = 420, heightDp = 780)
@Composable
private fun VideoDetailDarkPreview() = VideoDetailPreview("Nube", ratio = 16f / 9f, dark = true)

@Preview(name = "Detalle · error de reproducción", widthDp = 400, heightDp = 720)
@Composable
private fun VideoDetailPlaybackErrorPreview() = VideoDetailPreview("PC", ratio = 16f / 9f, bottomItem = true, playbackMessage = "No se pudo reproducir este video. Puedes seguir editando sus plataformas.")

@Preview(name = "Guardar · Idle", widthDp = 360, heightDp = 700)
@Composable
private fun VideoDetailSaveIdlePreview() = VideoDetailSavePreview(VideoSaveStatus.Idle)

@Preview(name = "Guardar · Running", widthDp = 360, heightDp = 700)
@Composable
private fun VideoDetailSaveRunningPreview() = VideoDetailSavePreview(VideoSaveStatus.Running)

@Preview(name = "Guardar · Saved", widthDp = 360, heightDp = 700)
@Composable
private fun VideoDetailSaveSavedPreview() = VideoDetailSavePreview(VideoSaveStatus.Saved)

@Preview(name = "Guardar · Failed", widthDp = 360, heightDp = 700)
@Composable
private fun VideoDetailSaveFailedPreview() = VideoDetailSavePreview(VideoSaveStatus.Failed("No se pudo guardar el video."))

@Composable
private fun VideoDetailSavePreview(status: VideoSaveStatus) =
    VideoDetailPreview("Local", ratio = 16f / 9f, bottomItem = true, saveStatus = status)

@Composable
private fun VideoDetailPreview(
    source: String,
    ratio: Float,
    fullscreen: Boolean = false,
    dark: Boolean = false,
    bottomItem: Boolean = false,
    playbackMessage: String? = null,
    saveStatus: VideoSaveStatus = VideoSaveStatus.Idle,
) {
    CreatorHomeTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CreatorStudioBackdrop(Modifier.fillMaxSize())
            VideoDetailStudioContent(
                title = "Una receta larga para el canal · versión final",
                metadata = "Resolución: 1080p · Duración: 00:42 · Formato: mp4 · $source",
                fullscreen = fullscreen,
                onToggleFullscreen = {},
                onDismiss = {},
                playback = VideoDetailPlaybackState(
                    hasUri = playbackMessage == null,
                    aspectRatio = ratio,
                    isBuffering = false,
                    message = playbackMessage,
                    player = { modifier ->
                        Box(modifier.background(Color(0xFF17191C)), contentAlignment = Alignment.Center) {
                            Text("Vista previa del reproductor", color = Color.White)
                        }
                    },
                ),
                listState = rememberLazyListState(initialFirstVisibleItemIndex = if (bottomItem) 2 else 0),
                stateFor = { if (it == Platform.INSTAGRAM) PlatformBadgeState.PUBLISHED else PlatformBadgeState.PENDING },
                hasLink = { false },
                onToggle = {},
                onEditLink = {},
                errorMessage = null,
                actions = {
                    when (source) {
                        "Local" -> {
                            DetailPrimaryAction("Publicar", Icons.Outlined.CloudUpload, true, {}, Modifier.fillMaxWidth())
                            DetailSaveAction("Guardar en galería", "Crea una copia en la galería", "Guardado en galería", Icons.Outlined.Download, saveStatus, true, {}, Modifier.fillMaxWidth())
                        }
                        "Nube" -> {
                            DetailSaveAction("Guardar en app", "Disponible sin conexión", "Guardado en app", Icons.Outlined.Save, VideoSaveStatus.Idle, true, {}, Modifier.fillMaxWidth())
                            DetailSaveAction("Guardar en galería", "Crea una copia en la galería", "Guardado en galería", Icons.Outlined.Download, VideoSaveStatus.Idle, true, {}, Modifier.fillMaxWidth())
                        }
                        else -> DetailPrimaryAction("Publicar", Icons.Outlined.CloudUpload, true, {}, Modifier.fillMaxWidth())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
