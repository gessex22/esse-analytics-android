package com.esseanalytics.android.feature.upload

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.model.Platform
import kotlin.math.roundToInt

@Composable
internal fun SlideToPublishBar(
    platforms: Set<Platform>,
    enabled: Boolean,
    isPublishing: Boolean,
    onPublish: () -> Boolean,
    confirmationKey: Any? = null,
    completedAttemptKey: Any? = null,
) {
    var state by remember(confirmationKey) { mutableStateOf(SlideToPublishState()) }
    var dragging by remember(confirmationKey) { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val canPublish = enabled && !isPublishing && platforms.any { it in Platform.publishable }
    val latestCanPublish by rememberUpdatedState(canPublish)
    val latestOnPublish by rememberUpdatedState(onPublish)
    val latestPlatforms by rememberUpdatedState(platforms)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scheme = MaterialTheme.colorScheme
    val pending = state.confirmed || isPublishing
    val interactive = canPublish && !state.confirmed
    val trackColor = if (canPublish || pending) scheme.primaryContainer else scheme.surfaceVariant
    val trackContentColor = if (canPublish || pending) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val handleSize = 60.dp
    val insetPx = with(density) { 6.dp.toPx() }
    val travel = (widthPx - with(density) { handleSize.toPx() } - insetPx * 2).coerceAtLeast(0f)
    val trackHeight = (72f * maxOf(1f, density.fontScale / 1.3f)).dp
    val visualFraction by animateFloatAsState(
        targetValue = if (isPublishing) 1f else state.fraction,
        animationSpec = if (dragging) snap() else tween(220),
        label = "slide-publication-confirmation",
    )
    val destinations = listOf(Platform.YOUTUBE, Platform.INSTAGRAM, Platform.FACEBOOK, Platform.TIKTOK).filter { it in platforms }
    val destinationNames = destinations.joinToString { it.displayName }

    LaunchedEffect(isPublishing) { state = state.publishingChanged(isPublishing) }
    LaunchedEffect(platforms, enabled, widthPx) {
        state = state.cancel()
        dragging = false
    }
    // A job can finish before Compose observes a busy frame; terminal identity
    // still unlocks the control for an intentional new attempt.
    LaunchedEffect(completedAttemptKey) {
        if (completedAttemptKey != null) {
            state = SlideToPublishState()
            dragging = false
        }
    }

    fun confirm(accessibility: Boolean = false, sameSelection: Boolean = true): Boolean {
        val allowed = latestCanPublish && sameSelection
        val result = if (accessibility) state.confirm(allowed) else state.release(allowed)
        state = result.state
        dragging = false
        if (!result.publish) return false
        if (!latestOnPublish()) {
            state = SlideToPublishState()
            return false
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    val label = when {
        isPublishing -> "Publicando…"
        state.confirmed -> "Preparando…"
        platforms.none { it in Platform.publishable } -> "Elige una plataforma"
        !enabled -> "Revisa los detalles"
        else -> "Desliza para publicar"
    }
    Box(
        Modifier.fillMaxWidth().height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (destinationNames.isEmpty()) "Publicar: selecciona una plataforma" else "Deslizar para publicar en $destinationNames"
                stateDescription = label
                if (interactive) onClick(label = "Confirmar publicación") { confirm(accessibility = true) }
                else disabled()
            }
            .onKeyEvent { event ->
                if (interactive && event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)) {
                    confirm(accessibility = true)
                } else false
            }
            .focusable(interactive),
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 72.dp)
                .graphicsLayer { alpha = if (pending) 1f else (1f - visualFraction * 1.5f).coerceIn(0f, 1f) }
                .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = trackContentColor,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                destinations.forEach { platform ->
                    platformIcon(platform)?.let { Icon(it, contentDescription = null, modifier = Modifier.size(17.dp),
                        tint = trackContentColor) }
                }
            }
        }
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((insetPx + visualFraction * travel).roundToInt(), 0) }
                .size(handleSize).clip(CircleShape).background(scheme.surface)
                .clearAndSetSemantics {}
                .pointerInput(confirmationKey, interactive, platforms, travel, rtl) {
                    if (!interactive || travel <= 0f) return@pointerInput
                    val dragPlatforms = platforms.toSet()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            state = state.copy(fraction = visualFraction)
                            dragging = true
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            state = state.slideBy((if (rtl) -delta else delta) / travel, latestCanPublish)
                        },
                        onDragCancel = { dragging = false; state = state.cancel() },
                        onDragEnd = { confirm(sameSelection = latestPlatforms == dragPlatforms) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                isPublishing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), color = trackContentColor, strokeWidth = 2.dp)
                state.confirmed -> Icon(Icons.Outlined.Check, contentDescription = null, tint = trackContentColor)
                else -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null,
                    tint = if (interactive) trackContentColor else scheme.onSurfaceVariant)
            }
        }
    }
}
