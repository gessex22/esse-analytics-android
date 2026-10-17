package com.esseanalytics.android.core.designsystem.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
/** Compatibility name for existing screens; Studio is now the only visual system. */
@Composable
fun CreatorHomeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) = StudioTheme(darkTheme, content)
