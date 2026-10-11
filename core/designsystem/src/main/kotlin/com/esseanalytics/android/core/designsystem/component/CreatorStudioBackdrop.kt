package com.esseanalytics.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun CreatorStudioBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val forest = if (colors.background.luminance() < 0.5f) Color(0xFF438872) else Color(0xFF28745C)
    Box(
        modifier.fillMaxSize().graphicsLayer().background(colors.background).drawWithCache {
            // Conserva la iluminación y la retícula, calculadas por tamaño/tema.
            // Un fondo quieto no necesita reconstruir tres shaders y redibujar
            // toda la pantalla en cada frame mientras se navega o se hace scroll.
            val step = 24.dp.toPx()
            val canvasSize = size
            val dots = buildList {
                var y = step / 2
                while (y < canvasSize.height) {
                    var x = step / 2
                    while (x < canvasSize.width) { add(Offset(x, y)); x += step }
                    y += step
                }
            }
            val upperGlow = Brush.radialGradient(
                listOf(forest.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(size.width * 1.05f, size.height * 0.18f),
                radius = size.width * 1.1f,
            )
            val lowerGlow = Brush.radialGradient(
                listOf(forest.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(size.width * 0.75f, size.height * 1.05f),
                radius = size.width * 0.9f,
            )
            val warmGlow = Brush.radialGradient(
                listOf(Color(0xFFE4C792).copy(alpha = 0.13f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.65f),
                radius = size.width * 0.8f,
            )
            onDrawBehind {
                drawRect(upperGlow)
                drawRect(lowerGlow)
                drawRect(warmGlow)
                drawPoints(dots, PointMode.Points, colors.onBackground.copy(alpha = 0.055f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            }
        },
    )
}
