package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.esseanalytics.android.core.model.Platform

// Publicar desde una PC de la LAN es server-side y sin bytes -- la PC lee su
// propio archivo del disco y lo sube ella misma (ver
// LibraryViewModel.publishLan), el celular solo manda un comando JSON por
// plataforma. Por eso esta pantalla NO reusa UploadScreen/WorkManager
// (feature:upload -- que además feature:library no puede depender, feature→
// feature está prohibido en esta arquitectura): no hay fileId: Long de Room,
// no hay progreso local de bytes que trackear, es 3 POSTs simples con la
// respuesta esperando en memoria. Mismo criterio de UX que PCLocalPublishView
// en iOS ("toca la fila -> mismo lugar donde se ve Y se publica"), pero
// construido desde cero -- Android no tenía ningún equivalente.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPcPublishSheet(
    item: LibraryListItem.LanVideo,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val publishState by viewModel.lanPublishState.collectAsState()
    // Preselecciona lo que todavía no está publicado ni descartado en esa
    // plataforma -- mismo criterio que isLanVideoPending (LibraryViewModel),
    // para no ofrecer por default re-publicar algo que ya salió.
    var selectedPlatforms by remember(item.video._id) {
        mutableStateOf(
            Platform.publishable.filter {
                it.apiValue !in item.video.platforms && it.apiValue !in item.video.platforms_discarded
            }.toSet(),
        )
    }
    var title by remember(item.video._id) { mutableStateOf(item.video.fileName.substringBeforeLast('.')) }
    var description by remember(item.video._id) { mutableStateOf("") }
    var tiktokPublic by remember { mutableStateOf(false) }

    val isSubmitting = publishState.values.any { it is LanPublishResult.InProgress }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Publicar desde esta PC") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Cerrar")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(item.video.fileName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Esta PC sube el video ella misma -- el teléfono solo manda el pedido. No se puede pausar ni reanudar la subida desde acá.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Platform.publishable.forEach { platform ->
                        LanPublishPlatformRow(
                            platform = platform,
                            selected = platform in selectedPlatforms,
                            result = publishState[platform],
                            enabled = !isSubmitting,
                            onToggle = { checked ->
                                selectedPlatforms = if (checked) selectedPlatforms + platform else selectedPlatforms - platform
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título (YouTube/TikTok)") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción / caption (YouTube/Instagram)") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (Platform.TIKTOK in selectedPlatforms) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = tiktokPublic, onCheckedChange = { tiktokPublic = it }, enabled = !isSubmitting)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (tiktokPublic) "TikTok: público" else "TikTok: solo yo (privado)")
                    }
                }

                Button(
                    onClick = { viewModel.publishLan(item, selectedPlatforms, title, description, tiktokPublic) },
                    enabled = !isSubmitting && selectedPlatforms.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text("Publicar")
                    }
                }
            }
        }
    }
}

@Composable
private fun LanPublishPlatformRow(
    platform: Platform,
    selected: Boolean,
    result: LanPublishResult?,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = selected, onCheckedChange = onToggle, enabled = enabled)
            val icon = platformIcon(platform)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = platformColor(platform), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(platform.displayName, modifier = Modifier.weight(1f))
            when (result) {
                is LanPublishResult.InProgress -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                is LanPublishResult.Success -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Publicado",
                    tint = MaterialTheme.colorScheme.primary,
                )
                is LanPublishResult.Failure -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = result.message,
                    tint = MaterialTheme.colorScheme.error,
                )
                null -> Unit
            }
        }
        if (result is LanPublishResult.Failure) {
            Text(
                result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}
