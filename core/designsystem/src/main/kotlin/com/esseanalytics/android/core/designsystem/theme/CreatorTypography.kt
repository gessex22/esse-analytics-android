package com.esseanalytics.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.esseanalytics.android.core.designsystem.R

// Fuentes incluidas en la app: misma forma y pesos sin depender del fabricante
// ni de una descarga en ejecución. Android del proyecto empieza en API 26.
@OptIn(ExperimentalTextApi::class)
val CreatorDisplayFont = FontFamily(
    *listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.fraunces_variable,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", 32f),
                FontVariation.Setting("SOFT", 50f),
                FontVariation.Setting("WONK", 1f),
            ),
        )
    }.toTypedArray(),
)

@OptIn(ExperimentalTextApi::class)
val CreatorBodyFont = FontFamily(
    *listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.dm_sans_variable,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", 14f),
            ),
        )
    }.toTypedArray(),
)

private val baseTypography = Typography()

val CreatorTypography = Typography(
    displayLarge = baseTypography.displayLarge.copy(fontFamily = CreatorDisplayFont),
    displayMedium = baseTypography.displayMedium.copy(fontFamily = CreatorDisplayFont),
    displaySmall = baseTypography.displaySmall.copy(fontFamily = CreatorDisplayFont),
    headlineLarge = baseTypography.headlineLarge.copy(fontFamily = CreatorDisplayFont),
    headlineMedium = baseTypography.headlineMedium.copy(fontFamily = CreatorDisplayFont),
    headlineSmall = baseTypography.headlineSmall.copy(fontFamily = CreatorDisplayFont),
    titleLarge = baseTypography.titleLarge.copy(fontFamily = CreatorDisplayFont),
    titleMedium = baseTypography.titleMedium.copy(fontFamily = CreatorBodyFont),
    titleSmall = baseTypography.titleSmall.copy(fontFamily = CreatorBodyFont),
    bodyLarge = baseTypography.bodyLarge.copy(fontFamily = CreatorBodyFont),
    bodyMedium = baseTypography.bodyMedium.copy(fontFamily = CreatorBodyFont),
    bodySmall = baseTypography.bodySmall.copy(fontFamily = CreatorBodyFont),
    labelLarge = baseTypography.labelLarge.copy(fontFamily = CreatorBodyFont),
    labelMedium = baseTypography.labelMedium.copy(fontFamily = CreatorBodyFont),
    labelSmall = baseTypography.labelSmall.copy(fontFamily = CreatorBodyFont),
)
