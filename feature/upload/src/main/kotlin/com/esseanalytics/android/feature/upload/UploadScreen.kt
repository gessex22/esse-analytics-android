package com.esseanalytics.android.feature.upload

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen

// Fase 1: selector de plataformas, formulario de título/caption, y las 3
// implementaciones de PlatformUploader + UploadWorker (WorkManager). Ver el
// plan, sección "Subidas directas".
@Composable
fun UploadScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Subir",
        note = "Publicar a YouTube / Instagram / TikTok — Fase 1",
        modifier = modifier,
    )
}
