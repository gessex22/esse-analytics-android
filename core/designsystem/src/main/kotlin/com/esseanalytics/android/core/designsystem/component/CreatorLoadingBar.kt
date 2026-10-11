package com.esseanalytics.android.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp

/** Indicador superpuesto: no reserva espacio ni inventa un porcentaje de descarga. */
@Composable
fun CreatorLoadingBar(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    pullProgress: Float = 0f,
) {
    val gestureProgress = pullProgress.coerceIn(0f, 1f)
    AnimatedVisibility(
        visible = isLoading || gestureProgress > 0f,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
    ) {
        val lineModifier = Modifier.fillMaxWidth().height(2.dp).clearAndSetSemantics {
            contentDescription = if (isLoading) "Cargando contenido" else "Desliza para actualizar"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
        if (isLoading) {
            LinearProgressIndicator(
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        } else {
            LinearProgressIndicator(
                progress = { gestureProgress },
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        }
    }
}
