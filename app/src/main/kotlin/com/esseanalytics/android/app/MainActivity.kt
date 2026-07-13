package com.esseanalytics.android.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.esseanalytics.android.core.designsystem.theme.EsseAnalyticsTheme
import dagger.hilt.android.AndroidEntryPoint

// Single-activity: toda la navegación vive en EsseAnalyticsNavHost (Compose
// Navigation). El deep link essenalytics://oauth-callback (ver AndroidManifest
// y Parte A del plan) también entra por acá — feature:auth lo lee del intent
// cuando se implemente el flujo de conectar YouTube/Instagram/TikTok (Fase 1).
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // EsseAnalyticsTheme fuerza dark siempre (la marca no tiene light mode
        // real, ver Color.kt) — forzamos también íconos claros acá, si no
        // SystemBarStyle.auto() los deja oscuros cuando el SO está en modo
        // claro y quedan invisibles contra el fondo oscuro de la app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            EsseAnalyticsTheme {
                EsseAnalyticsNavHost()
            }
        }
    }
}
