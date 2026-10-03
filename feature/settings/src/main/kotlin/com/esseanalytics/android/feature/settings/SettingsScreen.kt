package com.esseanalytics.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import com.esseanalytics.android.core.model.Platform
import androidx.compose.runtime.Composable
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
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.esseanalytics.android.core.model.WorkflowMode

// Clearance vertical para la barra de navegación flotante global, que es un
// overlay y no reserva espacio en el layout (mismo criterio que
// FloatingNavClearance en LibraryScreen).
private val FloatingNavClearance = 80.dp

// Junta los settings que ya existían sueltos (workflowMode, wifiOnlyUploads)
// más el selector de tema Rojo/Ámbar/Morado -- ver SettingsViewModel.
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val colorTheme by viewModel.colorTheme.collectAsState()
    val workflowMode by viewModel.workflowMode.collectAsState()
    val wifiOnly by viewModel.wifiOnlyUploads.collectAsState()
    var pendingWorkflowMode by remember { mutableStateOf<WorkflowMode?>(null) }
    val connections by viewModel.connections.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // Inset inferior real del sistema (gestos o navegación de 3 botones).
    val systemNavigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Sin scroll acá, la sección "Cuenta" (con el botón de cerrar sesión) al
    // final de 3 secciones + un divisor quedaba cortada fuera de la pantalla
    // en cualquier equipo donde el contenido no entrara entero -- sin forma
    // de llegar a ella.
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        LaunchedEffect(Unit) {
            viewModel.refreshConnections()
        }
        SettingsSection(title = "Tema") {
            ThemeOptionRow(
                label = "Rojo",
                swatch = Color(0xFFE63946),
                selected = colorTheme == "rojo",
            ) { viewModel.setColorTheme("rojo") }
            ThemeOptionRow(
                label = "Ámbar",
                swatch = Color(0xFFF59E0B),
                selected = colorTheme == "ambar",
            ) { viewModel.setColorTheme("ambar") }
            ThemeOptionRow(
                label = "Morado",
                swatch = Color(0xFFA855F7),
                selected = colorTheme == "morado",
            ) { viewModel.setColorTheme("morado") }
        }

        SettingsSection(title = "Modo de flujo") {
            RadioOptionRow(
                label = "Simple",
                description = "Publicar en una red descarta automáticamente las otras 2 pendientes.",
                selected = workflowMode == WorkflowMode.SIMPLE,
            ) { if (workflowMode == WorkflowMode.AVANZADO) pendingWorkflowMode = WorkflowMode.SIMPLE else viewModel.setWorkflowMode(WorkflowMode.SIMPLE) }
            RadioOptionRow(
                label = "Avanzado",
                description = "Cada plataforma se controla por separado, sin auto-descarte.",
                selected = workflowMode == WorkflowMode.AVANZADO,
            ) { viewModel.setWorkflowMode(WorkflowMode.AVANZADO) }
        }

        SettingsSection(title = "Subidas") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Solo por WiFi", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "No subir videos usando datos móviles.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = viewModel::setWifiOnlyUploads)
            }
        }

        SettingsSection(title = "Servidor de la PC") {
            ServerSettingsContent(viewModel = viewModel)
        }

        SettingsSection(title = "Cuentas conectadas") {
            Platform.publishable.forEach { platform ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(platform.displayName, modifier = Modifier.weight(1f))
                    if (connections[platform] == true) {
                        Text("Conectada", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        TextButton(onClick = { viewModel.disconnect(platform) }) { Text("Desconectar") }
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                viewModel.connectUrl(platform)?.let(uriHandler::openUri)
                            }
                        }) { Text("Conectar") }
                    }
                }
            }
        }

        HorizontalDivider()

        SettingsSection(title = "Cuenta") {
            LogoutRow(onLogout = viewModel::logout)
        }

        // Clearance dentro del contenido desplazable: como el padding(16.dp)
        // del Column está antes de verticalScroll, el margen inferior no
        // scrollea y "Cerrar sesión" quedaba bajo la barra flotante global y
        // el inset del sistema. Este Spacer sí es contenido desplazable, así
        // el botón puede subir completamente visible y pulsable (mismo
        // criterio que LibraryScreen).
        Spacer(modifier = Modifier.height(FloatingNavClearance + systemNavigationBarInset))
    }

    pendingWorkflowMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingWorkflowMode = null },
            title = { Text("Cambiar a modo simple") },
            text = { Text("Al cambiar a modo simple, las plataformas no seleccionadas podrán marcarse como descartadas al publicar. Los videos ya publicados no se modificarán.") },
            confirmButton = {
                TextButton(onClick = { viewModel.setWorkflowMode(mode); pendingWorkflowMode = null }) { Text("Cambiar") }
            },
            dismissButton = { TextButton(onClick = { pendingWorkflowMode = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    var confirming by remember { mutableStateOf(value = false) }

    if (confirming) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) { Text("Confirmar salida") }
            TextButton(onClick = { confirming = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        }
    } else {
        OutlinedButton(
            onClick = { confirming = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cerrar sesión") }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun ThemeOptionRow(label: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(swatch, CircleShape),
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun RadioOptionRow(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
