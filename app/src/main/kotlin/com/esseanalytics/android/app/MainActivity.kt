package com.esseanalytics.android.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.esseanalytics.android.core.designsystem.theme.EsseAnalyticsTheme
import dagger.hilt.android.AndroidEntryPoint

// Single-activity: toda la navegación vive en EsseAnalyticsNavHost (Compose
// Navigation). El deep link essenalytics://oauth-callback (ver AndroidManifest
// y Parte A del plan) también entra por acá — feature:auth lo lee del intent
// cuando se implemente el flujo de conectar YouTube/Instagram/TikTok (Fase 1).
//
// El Share Sheet ("Compartir → EsseAnalytics" desde Galería/Archivos, ver
// AndroidManifest y el plan, sección "Ingesta de videos") también entra acá —
// se captura el/los Uri del intent y se pasan como estado de Compose hacia
// abajo hasta IngestScreen, en vez de un event bus: launchMode="singleTop"
// hace que SIEMPRE pase por onCreate (cold start) u onNewIntent (app ya
// abierta), así que un solo punto de entrada alcanza.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingImportUris by mutableStateOf<List<Uri>>(emptyList())

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
        handleIncomingIntent(intent)
        setContent {
            EsseAnalyticsTheme {
                EsseAnalyticsNavHost(
                    pendingImportUris = pendingImportUris,
                    onPendingImportUrisConsumed = { pendingImportUris = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getStreamUri())
            Intent.ACTION_SEND_MULTIPLE -> intent.getStreamUriList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) pendingImportUris = uris
    }

    @Suppress("DEPRECATION")
    private fun Intent.getStreamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.getStreamUriList(): List<Uri> =
        (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            ) ?: emptyList()
}
