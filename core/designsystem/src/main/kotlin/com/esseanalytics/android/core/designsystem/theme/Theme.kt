package com.esseanalytics.android.core.designsystem.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// El frontend web es dark-only (sin modo claro real, ver Color.kt) con dos
// temas de color conmutables — se refleja acá igual, en vez de inventar un
// light mode que el resto de la marca no tiene.
enum class EsseAnalyticsColorTheme { ROJO, AMBAR }

// darkColorScheme() rellena con la paleta "Purple" default de Material3
// cualquier rol que no se pase acá (primaryContainer, surfaceContainer*,
// secondary/tertiary, etc.) -- por eso el FAB salía morado (PrimaryContainer
// default = #4F378B) y el NavigationBar tenía un borde visible arriba
// (SurfaceContainer default = un gris frío #211F26 que no pega con el fondo
// custom). Se completan TODOS los roles con la paleta real de la marca, la
// misma que theme.css, para que nada caiga al morado de Material.
private val RojoColors = darkColorScheme(
    primary = PrimaryRojo,
    onPrimary = Color.White,
    primaryContainer = PrimaryRojo,
    onPrimaryContainer = Color.White,
    inversePrimary = PrimaryRojo,
    // Sin segundo/tercer color de marca (--accent == --primary en
    // theme.css) -- se reusa el mismo rojo en vez de heredar el
    // púrpura/rosa default de Material.
    secondary = PrimaryRojo,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantRojo,
    onSecondaryContainer = OnSurfaceVariantRojo,
    tertiary = PrimaryRojo,
    onTertiary = Color.White,
    tertiaryContainer = SurfaceVariantRojo,
    onTertiaryContainer = OnSurfaceVariantRojo,
    background = BackgroundRojo,
    onBackground = OnSurfaceRojo,
    surface = SurfaceRojo,
    onSurface = OnSurfaceRojo,
    surfaceVariant = SurfaceVariantRojo,
    onSurfaceVariant = OnSurfaceVariantRojo,
    inverseSurface = OnSurfaceRojo,
    inverseOnSurface = BackgroundRojo,
    error = DestructiveRojo,
    onError = Color.White,
    errorContainer = DestructiveRojo,
    onErrorContainer = Color.White,
    outline = OutlineRojo,
    outlineVariant = OutlineRojo,
    surfaceBright = InputBackgroundRojo,
    surfaceDim = BackgroundRojo,
    // NavigationBar/BottomSheet/menús usan surfaceContainer por default --
    // se mapea a --card (mismo tono que el resto de las Card() de la app)
    // para que no se note la costura contra el fondo.
    surfaceContainer = SurfaceRojo,
    surfaceContainerHigh = PopoverRojo,
    surfaceContainerHighest = InputBackgroundRojo,
    surfaceContainerLow = BackgroundRojo,
    surfaceContainerLowest = BackgroundRojo,
)

private val AmbarColors = darkColorScheme(
    primary = PrimaryAmbar,
    onPrimary = Color.Black,
    primaryContainer = PrimaryAmbar,
    onPrimaryContainer = Color.Black,
    inversePrimary = PrimaryAmbar,
    secondary = PrimaryAmbar,
    onSecondary = Color.Black,
    secondaryContainer = SurfaceVariantAmbar,
    onSecondaryContainer = OnSurfaceVariantAmbar,
    tertiary = PrimaryAmbar,
    onTertiary = Color.Black,
    tertiaryContainer = SurfaceVariantAmbar,
    onTertiaryContainer = OnSurfaceVariantAmbar,
    background = BackgroundAmbar,
    onBackground = OnSurfaceAmbar,
    surface = SurfaceAmbar,
    onSurface = OnSurfaceAmbar,
    surfaceVariant = SurfaceVariantAmbar,
    onSurfaceVariant = OnSurfaceVariantAmbar,
    inverseSurface = OnSurfaceAmbar,
    inverseOnSurface = BackgroundAmbar,
    error = DestructiveAmbar,
    onError = Color.White,
    errorContainer = DestructiveAmbar,
    onErrorContainer = Color.White,
    outline = OutlineAmbar,
    outlineVariant = OutlineAmbar,
    surfaceBright = InputBackgroundAmbar,
    surfaceDim = BackgroundAmbar,
    surfaceContainer = SurfaceAmbar,
    surfaceContainerHigh = PopoverAmbar,
    surfaceContainerHighest = InputBackgroundAmbar,
    surfaceContainerLow = BackgroundAmbar,
    surfaceContainerLowest = BackgroundAmbar,
)

@Composable
fun EsseAnalyticsTheme(
    colorTheme: EsseAnalyticsColorTheme = EsseAnalyticsColorTheme.ROJO,
    // false por default: la marca EsseAnalytics tiene colores propios (los
    // mismos que el frontend web) — el dynamic color de Android 12+ los tapa
    // con una paleta sacada del wallpaper del usuario, que no es lo que
    // queremos acá. Se deja como parámetro por si en algún momento se ofrece
    // como opción en Ajustes, pero no como comportamiento por default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        colorTheme == EsseAnalyticsColorTheme.AMBAR -> AmbarColors
        else -> RojoColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EsseAnalyticsTypography,
        shapes = EsseAnalyticsShapes,
        content = content,
    )
}
