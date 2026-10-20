package com.esseanalytics.android.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@Composable fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel(), onOpenDestination: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("08:00") }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.enablePush() else viewModel.pushPermissionDenied()
    }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.quietHours) {
        state.quietHours?.let { quietStart = it.start; quietEnd = it.end }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bandeja", style = MaterialTheme.typography.titleMedium)
            if (state.unread > 0) TextButton(onClick = viewModel::markAllRead) { Text("Marcar todas leídas") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Preferencias", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Resumen diario del calendario"); Text("A las 09:00 en tu zona horaria", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = state.dailyEnabled, onCheckedChange = viewModel::setDailyEnabled)
        }
        Text("Avisos externos", style = MaterialTheme.typography.titleSmall)
        listOf("calendar" to "Calendario", "publication" to "Publicaciones", "connection" to "Conexiones", "local" to "App de escritorio", "account" to "Cuenta", "announcement" to "Comunicados").forEach { (key, label) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, modifier = Modifier.weight(1f)); Checkbox(checked = key in state.externalCategories, onCheckedChange = { viewModel.setExternalCategory(key, it) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Horario de descanso", modifier = Modifier.weight(1f))
            Switch(checked = state.quietHours != null, onCheckedChange = { viewModel.saveQuietHours(it, quietStart, quietEnd) })
        }
        if (state.quietHours != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = quietStart, onValueChange = { quietStart = it.take(5) }, label = { Text("Desde · HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = quietEnd, onValueChange = { quietEnd = it.take(5) }, label = { Text("Hasta · HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Button(onClick = { viewModel.saveQuietHours(true, quietStart, quietEnd) }) { Text("Guardar horario") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Avisos del sistema en este dispositivo")
                Text(state.pushMessage ?: if (state.pushConfigured) "Requiere permiso del sistema y una categoría activada." else "FCM está deshabilitado hasta configurar Firebase y el servidor.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.pushEnabled,
                enabled = state.pushEnabled || state.pushConfigured,
                onCheckedChange = { enabled ->
                    if (!enabled) viewModel.disablePush()
                    else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else viewModel.enablePush()
                },
            )
        }
        if (state.pushMessage != null) Text(state.pushMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (!state.loading && state.items.isEmpty()) Text("Sin notificaciones por ahora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        items(state.items, key = { it.id }) { item ->
                val route = item.destination?.takeIf { it in setOf("calendar", "history", "connections", "notifications") }
                Surface(
                    modifier = Modifier.fillMaxWidth().then(if (route != null) Modifier.clickable {
                        viewModel.markRead(item.id); onOpenDestination(route)
                    } else Modifier),
                    shape = RoundedCornerShape(14.dp), color = if (item.readAt == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .34f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.body, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (item.readAt == null) TextButton(onClick = { viewModel.markRead(item.id) }) { Text("Leída") }
                    }
                }
        }
    }
}
