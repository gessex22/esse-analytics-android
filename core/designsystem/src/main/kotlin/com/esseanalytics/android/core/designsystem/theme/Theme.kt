package com.esseanalytics.android.core.designsystem.theme
import androidx.compose.runtime.Composable
// Kept for source compatibility with old previews; preferences no longer change Studio.
enum class EsseAnalyticsColorTheme { ROJO, AMBAR, MORADO }
@Suppress("UNUSED_PARAMETER")
@Composable
fun EsseAnalyticsTheme(colorTheme: EsseAnalyticsColorTheme = EsseAnalyticsColorTheme.ROJO, dynamicColor: Boolean = false, content: @Composable () -> Unit) = StudioTheme(content = content)
