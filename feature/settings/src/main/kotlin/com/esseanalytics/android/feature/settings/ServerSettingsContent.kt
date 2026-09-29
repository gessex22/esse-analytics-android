package com.esseanalytics.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Preset de Ajustes → Servidor, SOLO en builds DEBUG (buildFeatures.buildConfig
// habilitado en build.gradle.kts de este módulo -- nunca compila en Release/
// Play Store). Desde el emulador, 10.0.2.2 es el alias fijo de Android para
// "localhost de la máquina host" (127.0.0.1 desde el emulador apunta al
// EMULADOR mismo, no a tu PC -- trampa de entorno distinta a la de iOS/
// Electron, confirmado en UIEssePanel/CLAUDE.md). Desde un teléfono físico
// hay que editar el campo a mano con la IP LAN de la PC que corre lab-backend,
// igual que ya hace falta para "PC local".
private const val LAB_PRESET_URL = "http://10.0.2.2:5055"

// Bloque de servidor extraído de SettingsScreen para reusarlo también en
// ServerSettingsScreen (la pantalla alcanzable desde Login, antes de
// autenticarse). Conserva: Central (campo vacío), PC local descubierta por
// NSD, URL manual y preset Laboratorio sólo en BuildConfig.DEBUG. El discovery
// se inicia al entrar y se frena en onDispose para no dejar el listener NSD
// vivo fuera de la pantalla.
@Composable
fun ServerSettingsContent(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    var serverUrlDraft by remember(serverUrl) { mutableStateOf(serverUrl) }
    val discoveredPc by viewModel.discoveredPc.collectAsState()
    val isLabMode by viewModel.isLabMode.collectAsState()
    val saveState by viewModel.serverSaveState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshLabMode()
        viewModel.discoverPc()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopDiscoveringPc() }
    }

    Column(modifier = modifier) {
        discoveredPc?.let { (name, url) ->
            Text("PC encontrada: $name", color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = {
                serverUrlDraft = url
                viewModel.resetServerSaveState()
            }) { Text("Usar esta PC ($url)") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = {
                serverUrlDraft = ""
                viewModel.resetServerSaveState()
            }) { Text("Central") }
            OutlinedButton(
                onClick = {
                    discoveredPc?.let { (_, url) -> serverUrlDraft = url }
                    viewModel.resetServerSaveState()
                },
                enabled = discoveredPc != null,
            ) { Text("PC local") }
            if (BuildConfig.DEBUG) {
                OutlinedButton(onClick = {
                    serverUrlDraft = LAB_PRESET_URL
                    viewModel.resetServerSaveState()
                }) { Text("Laboratorio") }
            }
        }
        OutlinedTextField(
            value = serverUrlDraft,
            onValueChange = {
                serverUrlDraft = it
                viewModel.resetServerSaveState()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("URL opcional") },
            placeholder = { Text("http://192.168.1.50:4000") },
        )
        Text(
            "Dejá vacío para usar la central. Después de guardar, reiniciá la app para aplicar el servidor.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "La PC y el telefono deben estar en la misma red. Si no aparece, permite EsseAnalytics en el firewall de Windows para el puerto 4000.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(
            onClick = { viewModel.saveServerUrl(serverUrlDraft) },
            enabled = saveState !is ServerSaveState.Checking,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Guardar servidor") }
        when (val state = saveState) {
            ServerSaveState.Idle -> Unit
            ServerSaveState.Checking -> Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text("Probando…", style = MaterialTheme.typography.bodyMedium)
            }
            is ServerSaveState.Saved -> Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Servidor guardado. Cerrá y volvé a abrir la app para aplicarlo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.environment?.let { environment ->
                    Text(
                        "Entorno: $environment",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ServerSaveState.Error -> Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (isLabMode) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧪", modifier = Modifier.padding(end = 6.dp))
                Text(
                    "Laboratorio · Datos simulados",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7C3AED),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
