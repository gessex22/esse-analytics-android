package com.esseanalytics.android.feature.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.esseanalytics.android.core.model.VideoFile

// No existía ningún reproductor en Android todavía -- tocar un video solo
// navegaba a Estadísticas/Subir (ver EsseAnalyticsNavHost.onLocalClick).
// Mirror de LocalVideoPlayerView.swift/RemoteVideoPlayerView.swift (iOS), pero
// unificado en un solo composable: acá "Todos" ya mezcla local+remoto (ver
// LibraryViewModel.items), y ExoPlayer resuelve ambos casos con el mismo
// MediaItem.fromUri (file/content para local, https para remoto vía
// DefaultHttpDataSource, que viene con media3-exoplayer sin dependencias
// extra). Dialog de pantalla completa en vez de una ruta de NavHost: el dato
// (VideoFile o la URL de streaming) ya está en memoria acá, no hace falta
// ir y volver a buscarlo por id.
@Composable
fun LocalVideoPlayerDialog(file: VideoFile, onDismiss: () -> Unit) {
    VideoPlayerDialog(
        title = file.fileName,
        mediaUri = Uri.parse(file.filePath),
        errorMessage = "No se pudo reproducir este video. El archivo puede haberse movido o borrado.",
        onDismiss = onDismiss,
    )
}

// streamUrl null (sin sesión, caso raro) -> mensaje de error directo, sin
// intentar reproducir nada.
@Composable
fun RemoteVideoPlayerDialog(title: String, streamUrl: String?, onDismiss: () -> Unit) {
    VideoPlayerDialog(
        title = title,
        mediaUri = streamUrl?.let(Uri::parse),
        // El comentario de RemoteVideoPlayerView.swift sobre "el problema de
        // los dos conectores" aplica igual acá -- ver notas de la sesión: dos
        // cloudflared activos sin storage compartido pueden dar 404
        // intermitente para un archivo que en realidad sí existe.
        errorMessage = "No se pudo reproducir este video. Puede ser un problema temporal de conexión -- probá de nuevo en un momento.",
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerDialog(title: String, mediaUri: Uri?, errorMessage: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var playbackError by remember(mediaUri) { mutableStateOf(mediaUri == null) }
        var isReady by remember(mediaUri) { mutableStateOf(false) }

        val exoPlayer = remember(mediaUri) {
            mediaUri?.let {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(it))
                    playWhenReady = true
                    prepare()
                }
            }
        }

        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = true
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) isReady = true
                }
            }
            exoPlayer?.addListener(listener)
            onDispose {
                exoPlayer?.removeListener(listener)
                exoPlayer?.release()
            }
        }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(title, color = Color.White, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    playbackError -> PlaybackErrorState(errorMessage)
                    exoPlayer != null -> {
                        AndroidView(
                            factory = { PlayerView(context).apply { player = exoPlayer } },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!isReady) CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color.White)
        Text(message, color = Color.White)
    }
}
