package com.esseanalytics.android.feature.settings

import android.content.ActivityNotFoundException
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.WorkflowMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    username: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colorTheme by viewModel.colorTheme.collectAsState()
    val workflowMode by viewModel.workflowMode.collectAsState()
    val wifiOnly by viewModel.wifiOnlyUploads.collectAsState()
    val connections by viewModel.connections.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    var confirmSimple by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf<Platform?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    // También actualiza al volver del navegador después de autorizar una red.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshConnections() }

    CreatorHomeTheme {
        SettingsStudioContent(
            username = username,
            colorTheme = colorTheme,
            workflowMode = workflowMode,
            wifiOnly = wifiOnly,
            connections = connections,
            serverIsCustom = serverUrl.isNotBlank(),
            connecting = connecting,
            connectionError = connectionError,
            onBack = onBack,
            onColorTheme = viewModel::setColorTheme,
            onWorkflowMode = { mode ->
                if (workflowMode == WorkflowMode.AVANZADO && mode == WorkflowMode.SIMPLE) {
                    confirmSimple = true
                } else {
                    viewModel.setWorkflowMode(mode)
                }
            },
            onWifiOnly = viewModel::setWifiOnlyUploads,
            onConnection = { platform ->
                connectionError = null
                if (connections[platform] == true) {
                    viewModel.disconnect(platform)
                } else if (connecting == null) {
                    connecting = platform
                    scope.launch {
                        try {
                            val url = viewModel.connectUrl(platform)
                            if (url == null) {
                                connectionError = "No se pudo iniciar la conexión con ${platform.displayName}. Volvé a intentarlo."
                            } else {
                                try {
                                    uriHandler.openUri(url)
                                } catch (_: IllegalArgumentException) {
                                    connectionError = "No se pudo abrir el navegador para conectar ${platform.displayName}."
                                } catch (_: ActivityNotFoundException) {
                                    connectionError = "No se pudo abrir el navegador para conectar ${platform.displayName}."
                                }
                            }
                        } finally {
                            connecting = null
                        }
                    }
                }
            },
            onLogout = { confirmLogout = true },
            serverContent = { ServerSettingsContent(viewModel = viewModel) },
            modifier = modifier,
        )

        if (confirmSimple) {
            AlertDialog(
                onDismissRequest = { confirmSimple = false },
                title = { Text("Cambiar a modo simple") },
                text = { Text("Al cambiar a modo simple, las plataformas no seleccionadas podrán marcarse como descartadas al publicar. Los videos ya publicados no se modificarán.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setWorkflowMode(WorkflowMode.SIMPLE)
                        confirmSimple = false
                    }) { Text("Cambiar") }
                },
                dismissButton = { TextButton(onClick = { confirmSimple = false }) { Text("Cancelar") } },
            )
        }
        if (confirmLogout) {
            AlertDialog(
                onDismissRequest = { confirmLogout = false },
                title = { Text("Cerrar sesión") },
                text = { Text("Tendrás que volver a iniciar sesión para usar tu cuenta.") },
                confirmButton = { TextButton(onClick = viewModel::logout) { Text("Cerrar sesión") } },
                dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
            )
        }
    }
}
