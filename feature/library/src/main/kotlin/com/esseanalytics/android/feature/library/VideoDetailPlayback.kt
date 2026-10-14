package com.esseanalytics.android.feature.library

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

internal data class VideoDetailPlaybackState(
    val hasUri: Boolean,
    val aspectRatio: Float,
    val isBuffering: Boolean,
    val message: String?,
    val player: @Composable (Modifier) -> Unit,
)

@Composable
internal fun rememberVideoDetailPlayback(uri: Uri?, errorText: String): VideoDetailPlaybackState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriKey = uri?.toString()
    var aspectRatio by remember(uriKey) { mutableFloatStateOf(16f / 9f) }
    var isBuffering by remember(uriKey) { mutableStateOf(uri != null) }
    var failed by remember(uriKey) { mutableStateOf(false) }
    var resumeAfterForeground by remember(uriKey) { mutableStateOf(false) }
    val player = remember(uriKey) {
        uri?.let { mediaUri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                playWhenReady = true
                prepare()
            }
        }
    }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio
                val height = videoSize.height.toFloat()
                if (width > 0f && height > 0f && width.isFinite() && height.isFinite()) {
                    aspectRatio = (width / height).coerceIn(0.25f, 4f)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                isBuffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        if (player == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeAfterForeground = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (resumeAfterForeground) player.play()
                    resumeAfterForeground = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            resumeAfterForeground = player.playWhenReady
            player.pause()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val message = when {
        uri == null -> errorText
        failed -> "No se pudo reproducir este video. Puedes seguir editando sus plataformas."
        else -> null
    }
    return VideoDetailPlaybackState(
        hasUri = uri != null,
        aspectRatio = aspectRatio,
        isBuffering = isBuffering && message == null,
        message = message,
        player = { modifier ->
            if (player != null) {
                AndroidView(
                    factory = { createPlayerView(context, player) },
                    update = { view -> if (view.player !== player) view.player = player },
                    modifier = modifier.fillMaxSize(),
                    onRelease = { view -> view.player = null },
                )
            }
        },
    )
}

private fun createPlayerView(context: Context, player: ExoPlayer): PlayerView =
    PlayerView(context).apply {
        this.player = player
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        useController = true
        setShutterBackgroundColor(android.graphics.Color.BLACK)
    }
