package com.esseanalytics.android.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** Single semantic palette shared with iOS Studio (including system dark appearance). */
fun studioColors(dark: Boolean): ColorScheme {
    val background = Color(if (dark) 0xFF141211 else 0xFFF4F1EB)
    val foreground = Color(if (dark) 0xFFF4EBE3 else 0xFF292625)
    val card = Color(if (dark) 0xFF211C19 else 0xFFFFFDFA)
    val secondary = Color(if (dark) 0xFF2A2420 else 0xFFEDE7DF)
    val muted = Color(if (dark) 0xFFC4B5A8 else 0xFF706259)
    val accent = Color(if (dark) 0xFFEDAE8C else 0xFFA34E36)
    val onAccent = Color(if (dark) 0xFF321C12 else 0xFFFFFFFF)
    val border = Color(if (dark) 0xFF3B312B else 0xFFE2D9D0)
    val error = Color(if (dark) 0xFFFF8A80 else 0xFFB9343C)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent, primaryContainer = secondary, onPrimaryContainer = foreground,
        inversePrimary = accent, secondary = accent, onSecondary = onAccent,
        secondaryContainer = secondary, onSecondaryContainer = foreground,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = secondary, onTertiaryContainer = foreground,
        background = background, onBackground = foreground, surface = card, onSurface = foreground,
        surfaceVariant = secondary, onSurfaceVariant = muted, surfaceTint = accent,
        inverseSurface = foreground, inverseOnSurface = background,
        error = error, onError = onAccent, errorContainer = secondary, onErrorContainer = error,
        outline = border, outlineVariant = border, surfaceBright = card, surfaceDim = background,
        surfaceContainerLowest = background, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = secondary, surfaceContainerHighest = secondary,
    )
}

@Composable
fun StudioTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = studioColors(darkTheme)
    val typography = CreatorTypography.copy(
        displayLarge = CreatorTypography.displayLarge.copy(fontFamily = CreatorBodyFont),
        displayMedium = CreatorTypography.displayMedium.copy(fontFamily = CreatorBodyFont),
        displaySmall = CreatorTypography.displaySmall.copy(fontFamily = CreatorBodyFont),
        headlineLarge = CreatorTypography.headlineLarge.copy(fontFamily = CreatorBodyFont),
        headlineMedium = CreatorTypography.headlineMedium.copy(fontFamily = CreatorBodyFont),
        headlineSmall = CreatorTypography.headlineSmall.copy(fontFamily = CreatorBodyFont),
        titleLarge = CreatorTypography.titleLarge.copy(fontFamily = CreatorBodyFont),
    )
    MaterialTheme(colorScheme = colors, typography = typography, shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp))) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}

@Composable
fun studioPlatformColor(platform: String): Color = when (platform) {
    "youtube" -> Color(0xFFEF4444)
    "instagram" -> Color(0xFFA855F7)
    "facebook" -> Color(0xFF3B82F6)
    else -> if (isSystemInDarkTheme()) Color(0xFFE7F2EF) else Color(0xFF17312F)
}
@Composable
fun studioPendingColor(): Color = if (isSystemInDarkTheme()) Color(0xFF8A8F99) else Color(0xFF737373)
