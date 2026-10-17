package com.esseanalytics.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DashboardTheme(apple: Boolean, content: @Composable () -> Unit) {
    if (!apple) { CreatorHomeTheme(content = content); return }
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF0A84FF), onPrimary = Color.White,
        background = Color.Black, onBackground = Color.White,
        surface = Color(0xFF2C2C2E), onSurface = Color.White,
        surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFFAEAEB2),
        outlineVariant = Color(0xFF48484A),
    ) else lightColorScheme(
        primary = Color(0xFF007AFF), onPrimary = Color.White,
        background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
        surface = Color.White, onSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF636366),
        outlineVariant = Color(0xFFE5E5EA),
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp))) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
