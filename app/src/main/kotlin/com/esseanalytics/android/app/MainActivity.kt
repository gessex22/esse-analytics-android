package com.esseanalytics.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            EsseAnalyticsTheme {
                EsseAnalyticsNavHost()
            }
        }
    }
}
