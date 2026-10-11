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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.component.SwissButton
import com.esseanalytics.android.core.designsystem.component.SwissCard
import com.esseanalytics.android.core.designsystem.component.SwissLabel
import com.esseanalytics.android.core.designsystem.component.SwissSectionTitle
import com.esseanalytics.android.core.designsystem.theme.Swiss
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.WorkflowMode
import kotlinx.coroutines.launch

// Clearance vertical para la barra de navegación flotante global, que es un
// overlay y no reserva espacio en el layout.
private val FloatingNavClearance = 80.dp

// REDISEÑO BENTO: Ajustes como retícula de tarjetas — cada sección en su
// tarjeta, temas como tarjetas de color, modos como tarjetas
// seleccionables. Toda la funcionalidad intacta: tema rojo/ámbar/morado,
// modo simple/avanzado (con su diálogo de confirmación), solo-WiFi,
// servidor de la PC, cuentas conectadas y cierre de sesión en dos pasos.
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val colorTheme by viewModel.colorTheme.collectAsState()
    val workflowMode by viewModel.workflowMode.collectAsState()
    val wifiOnly by viewModel.wifiOnlyUploads.collectAsState()
    var pendingWorkflowMode by remember { mutableStateOf<WorkflowMode?>(null) }
    val connections by viewModel.connections.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val systemNavigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) {
        viewModel.refreshConnections()
    }

    Column(
        modifier = modifier
            .padding(horizontal = Swiss.contentHorizontal)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ── TEMA ──
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "01", title = "Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(Swiss.gap), modifier = Modifier.fillMaxWidth()) {
                ThemeSwatch(
                    label = "Rojo",
                    swatch = Color(0xFFE63946),
                    selected = colorTheme == "rojo",
                    onClick = { viewModel.setColorTheme("rojo") },
                    modifier = Modifier.weight(1f),
                )
                ThemeSwatch(
                    label = "Ámbar",
                    swatch = Color(0xFFF59E0B),
                    selected = colorTheme == "ambar",
                    onClick = { viewModel.setColorTheme("ambar") },
                    modifier = Modifier.weight(1f),
                )
                ThemeSwatch(
                    label = "Morado",
                    swatch = Color(0xFFA855F7),
                    selected = colorTheme == "morado",
                    onClick = { viewModel.setColorTheme("morado") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── MODO DE FLUJO ──
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "02", title = "Modo de flujo")
            WorkflowModeCard(
                label = "Simple",
                description = "Publicar en una red descarta automáticamente las otras 2 pendientes.",
                selected = workflowMode == WorkflowMode.SIMPLE,
                onClick = { if (workflowMode == WorkflowMode.AVANZADO) pendingWorkflowMode = WorkflowMode.SIMPLE else viewModel.setWorkflowMode(WorkflowMode.SIMPLE) },
            )
            WorkflowModeCard(
                label = "Avanzado",
                description = "Cada plataforma se controla por separado, sin auto-descarte.",
                selected = workflowMode == WorkflowMode.AVANZADO,
                onClick = { viewModel.setWorkflowMode(WorkflowMode.AVANZADO) },
            )
        }

        // ── SUBIDAS ──
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "03", title = "Subidas")
            SwissCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Solo por WiFi", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No subir videos usando datos móviles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = wifiOnly, onCheckedChange = viewModel::setWifiOnlyUploads)
                }
            }
        }

        // ── SERVIDOR DE LA PC ──
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "04", title = "Servidor de la PC")
            SwissCard {
                ServerSettingsContent(viewModel = viewModel)
            }
        }

        // ── CUENTAS CONECTADAS ──
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "05", title = "Cuentas conectadas")
            Platform.publishable.forEach { platform ->
                val connected = connections[platform] == true
                SwissCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                platform.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            SwissLabel(
                                text = if (connected) "Conectada" else "No conectada",
                                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (connected) {
                            SwissButton(
                                text = "Desconectar",
                                onClick = { viewModel.disconnect(platform) },
                                primary = false,
                            )
                        } else {
                            SwissButton(
                                text = "Conectar",
                                onClick = {
                                    scope.launch {
                                        viewModel.connectUrl(platform)?.let(uriHandler::openUri)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── CUENTA ──
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "06", title = "Cuenta")
            SwissCard {
                LogoutBlock(onLogout = viewModel::logout)
            }
        }

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
private fun ThemeSwatch(label: String, swatch: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    SwissCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(swatch),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
        }
    }
}

@Composable
private fun WorkflowModeCard(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SwissCard(
        onClick = onClick,
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f)),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LogoutBlock(onLogout: () -> Unit) {
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
        SwissButton(
            text = "Cerrar sesión",
            onClick = { confirming = true },
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
