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

private val RojoColors = darkColorScheme(
    primary = PrimaryRojo,
    onPrimary = Color.White,
    background = BackgroundRojo,
    onBackground = OnSurfaceRojo,
    surface = SurfaceRojo,
    onSurface = OnSurfaceRojo,
    surfaceVariant = SurfaceVariantRojo,
    onSurfaceVariant = OnSurfaceVariantRojo,
    outline = OutlineRojo,
)

private val AmbarColors = darkColorScheme(
    primary = PrimaryAmbar,
    onPrimary = Color.Black,
    background = BackgroundAmbar,
    onBackground = OnSurfaceAmbar,
    surface = SurfaceAmbar,
    onSurface = OnSurfaceAmbar,
    surfaceVariant = SurfaceVariantAmbar,
    onSurfaceVariant = OnSurfaceVariantAmbar,
    outline = OutlineAmbar,
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
