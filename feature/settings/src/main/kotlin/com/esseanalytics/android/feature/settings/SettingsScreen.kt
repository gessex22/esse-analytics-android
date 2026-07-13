package com.esseanalytics.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.esseanalytics.android.core.model.WorkflowMode

// Junta los settings que ya existían sueltos (workflowMode, wifiOnlyUploads)
// más el selector de tema Rojo/Ámbar -- ver SettingsViewModel.
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val colorTheme by viewModel.colorTheme.collectAsState()
    val workflowMode by viewModel.workflowMode.collectAsState()
    val wifiOnly by viewModel.wifiOnlyUploads.collectAsState()

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        SettingsSection(title = "Tema") {
            ThemeOptionRow(
                label = "Rojo",
                swatch = Color(0xFFE63946),
                selected = colorTheme == "rojo",
                onClick = { viewModel.setColorTheme("rojo") },
            )
            ThemeOptionRow(
                label = "Ámbar",
                swatch = Color(0xFFF59E0B),
                selected = colorTheme == "ambar",
                onClick = { viewModel.setColorTheme("ambar") },
            )
        }

        SettingsSection(title = "Modo de flujo") {
            RadioOptionRow(
                label = "Simple",
                description = "Publicar en una red descarta automáticamente las otras 2 pendientes.",
                selected = workflowMode == WorkflowMode.SIMPLE,
                onClick = { viewModel.setWorkflowMode(WorkflowMode.SIMPLE) },
            )
            RadioOptionRow(
                label = "Avanzado",
                description = "Cada plataforma se controla por separado, sin auto-descarte.",
                selected = workflowMode == WorkflowMode.AVANZADO,
                onClick = { viewModel.setWorkflowMode(WorkflowMode.AVANZADO) },
            )
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

        HorizontalDivider()

        SettingsSection(title = "Cuenta") {
            LogoutRow(onLogout = viewModel::logout)
        }
    }
}

@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

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
