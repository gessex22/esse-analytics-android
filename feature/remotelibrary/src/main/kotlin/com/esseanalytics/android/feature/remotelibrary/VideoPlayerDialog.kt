package com.esseanalytics.android.feature.remotelibrary

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

// No existía ningún reproductor en Android todavía. Mirror de
// RemoteVideoPlayerView.swift (iOS) -- streaming vía DefaultHttpDataSource
// (viene con media3-exoplayer, sin dependencias extra), la URL ya trae el JWT
// como ?token= (ver remoteLibraryStreamUrl), no hace falta un
// HttpDataSource.Factory custom. Dialog de pantalla completa: el video ya
// está en memoria acá (viene de RemoteLibraryUiState.Loaded), no hace falta
// una ruta de NavHost aparte.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteVideoPlayerDialog(title: String, streamUrl: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        val mediaUri = remember(streamUrl) { streamUrl?.let(Uri::parse) }
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
                    playbackError -> PlaybackErrorState(
                        // El comentario de RemoteVideoPlayerView.swift sobre
                        // "el problema de los dos conectores" aplica igual
                        // acá -- ver notas de la sesión: dos cloudflared
                        // activos sin storage compartido pueden dar 404
                        // intermitente para un archivo que en realidad existe.
                        "No se pudo reproducir este video. Puede ser un problema temporal de conexión -- probá de nuevo en un momento.",
                    )
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
