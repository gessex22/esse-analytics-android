package com.esseanalytics.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.WorkflowMode

// Fixtures de desarrollo: no crea ViewModels ni realiza peticiones.
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsPreview(
    dark: Boolean = false,
    expanded: Boolean = false,
    loading: Boolean = false,
    error: Boolean = false,
    opening: Boolean = false,
) {
    var mode by remember { mutableStateOf(WorkflowMode.AVANZADO) }
    var wifi by remember { mutableStateOf(true) }
    var accent by remember { mutableStateOf("ambar") }
    var serverDraft by remember { mutableStateOf("") }
    CreatorHomeTheme(darkTheme = dark) {
        SettingsStudioContent(
            username = "María Fernanda · Estudio creativo",
            workflowMode = mode,
            wifiOnly = wifi,
            connections = if (loading) emptyMap() else mapOf(Platform.YOUTUBE to true, Platform.INSTAGRAM to false, Platform.TIKTOK to true),
            serverIsCustom = false,
            connecting = if (opening) Platform.INSTAGRAM else null,
            connectionError = if (error) "No se pudo iniciar la conexión con Instagram. Volvé a intentarlo." else null,
            onBack = {},
            onWorkflowMode = { mode = it },
            onWifiOnly = { wifi = it },
            onConnection = {},
            onLogout = {},
            initiallyExpandedSections = if (expanded) setOf("workflow", "accent", "server") else emptySet(),
            serverContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Central", "PC local", "Laboratorio").forEach { label ->
                            OutlinedButton(onClick = {}) { Text(label) }
                        }
                    }
                    OutlinedTextField(value = serverDraft, onValueChange = { serverDraft = it }, label = { Text("URL opcional") }, modifier = Modifier.fillMaxWidth())
                    Text("Dejá vacío para usar la central.", modifier = Modifier.padding(top = 4.dp))
                }
            },
        )
    }
}

@Preview(name = "Ajustes · claro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsLightPreview() = SettingsPreview()

@Preview(name = "Ajustes · oscuro", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsDarkPreview() = SettingsPreview(dark = true)

@Preview(name = "Ajustes · 320 dp y texto grande", showBackground = true, widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
private fun SettingsLargeTextPreview() = SettingsPreview(expanded = true)

@Preview(name = "Ajustes · opciones desplegadas", showBackground = true, widthDp = 400, heightDp = 1400)
@Composable
private fun SettingsExpandedPreview() = SettingsPreview(expanded = true)

@Preview(name = "Ajustes · consultando redes", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsLoadingPreview() = SettingsPreview(loading = true)

@Preview(name = "Ajustes · error al conectar", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsErrorPreview() = SettingsPreview(error = true)

@Preview(name = "Ajustes · abriendo conexión", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsConnectingPreview() = SettingsPreview(opening = true)
