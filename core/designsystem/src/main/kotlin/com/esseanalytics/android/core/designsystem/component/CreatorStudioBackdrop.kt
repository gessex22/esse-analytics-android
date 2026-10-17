package com.esseanalytics.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun CreatorStudioBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().graphicsLayer().background(colors.background).drawWithCache {
        val step = 24.dp.toPx()
        val canvasSize = size
        val dots = buildList {
            var y = step / 2
            while(y < canvasSize.height) {
                var x = step / 2
                while(x < canvasSize.width) { add(Offset(x,y)); x += step }
                y += step
            }
        }
        onDrawBehind { drawPoints(dots, PointMode.Points, colors.onBackground.copy(alpha = .025f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round) }
    })
}
