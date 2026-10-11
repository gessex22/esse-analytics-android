package com.esseanalytics.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Paleta del prototipo design/mobile-home-refinement. Se aplica al Home y a
// su navegación sin cambiar las preferencias de color de las otras pantallas.
private val CreatorLightColors = lightColorScheme(
    primary = Color(0xFF147C71),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF2ED),
    onPrimaryContainer = Color(0xFF0D655D),
    secondary = Color(0xFF147C71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF7F5),
    onSecondaryContainer = Color(0xFF17312F),
    background = Color(0xFFEAF1ED),
    onBackground = Color(0xFF17312F),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF17312F),
    surfaceVariant = Color(0xFFEFF7F5),
    onSurfaceVariant = Color(0xFF546D68),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FBFA),
    surfaceContainer = Color(0xFFEFF7F5),
    surfaceContainerHigh = Color(0xFFE8F2EF),
    surfaceContainerHighest = Color(0xFFDDF2ED),
    outline = Color(0xFF748783),
    outlineVariant = Color(0xFFE8EFED),
    surfaceTint = Color(0xFF147C71),
)

private val CreatorDarkColors = darkColorScheme(
    primary = Color(0xFF77D4C2),
    onPrimary = Color(0xFF10332D),
    primaryContainer = Color(0xFF264B45),
    onPrimaryContainer = Color(0xFF8CE0CF),
    secondary = Color(0xFF77D4C2),
    onSecondary = Color(0xFF10332D),
    secondaryContainer = Color(0xFF203330),
    onSecondaryContainer = Color(0xFFE7F2EF),
    background = Color(0xFF0F1B18),
    onBackground = Color(0xFFE7F2EF),
    surface = Color(0xFF1A2826),
    onSurface = Color(0xFFE7F2EF),
    surfaceVariant = Color(0xFF203330),
    onSurfaceVariant = Color(0xFFB0C2BD),
    surfaceContainerLowest = Color(0xFF111B1A),
    surfaceContainerLow = Color(0xFF1A2826),
    surfaceContainer = Color(0xFF203330),
    surfaceContainerHigh = Color(0xFF233633),
    surfaceContainerHighest = Color(0xFF30423F),
    outline = Color(0xFF9AADAA),
    outlineVariant = Color(0xFF30423F),
    surfaceTint = Color(0xFF77D4C2),
)

@Composable
fun CreatorHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) CreatorDarkColors else CreatorLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CreatorTypography,
        shapes = MaterialTheme.shapes,
    ) {
        // MaterialTheme no cambia LocalContentColor. Sin este proveedor, el
        // Scaffold oscuro exterior dejaba texto blanco sobre el Home claro.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
