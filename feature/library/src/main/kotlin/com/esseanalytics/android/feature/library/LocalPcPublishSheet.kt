package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
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
            LocalPcPublishContent(
                item = item,
                viewModel = viewModel,
                onChooseAnother = onDismiss,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// El mismo contenido se usa dentro de la pestaña Subir y desde Biblioteca.
// El contenedor (pantalla normal o diálogo de detalle) deja de decidir qué
// formulario se muestra; solamente aporta cómo volver al selector.
@Composable
fun LocalPcPublishContent(
    item: LibraryListItem.LanVideo,
    viewModel: LibraryViewModel,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier,
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
    // Paridad con UploadScreen.kt (FacebookCrossPostRow) -- no se puede
    // reusar ese composable directo, feature:library no puede depender de
    // feature:upload (feature→feature prohibido, ver comentario de arriba).
    // El contrato ya lo soporta (LocalPcInstagramUploadRequest.crossPostFacebook,
    // local-backend/src/controllers/instagram-upload.controller.ts), solo
    // faltaba el toggle en esta hoja.
    var crossPostFacebook by remember(item.video._id) { mutableStateOf(false) }
    // FIX 2026-08-18 (ver UIEssePanel/PLAN_LAN_PICKER_Y_REPRODUCTOR-2026-08-18.md):
    // antes esta hoja no tenía forma de VER el video antes de publicar, solo
    // nombre+checkboxes. RemoteVideoPlayerDialog ya es genérico (title +
    // streamUrl + onDismiss, sin nada específico de la cola remota -- mismo
    // componente que usa LibraryScreen para playingLan), se reusa tal cual.
    var showPlayer by remember { mutableStateOf(false) }

    val isSubmitting = publishState.values.any { it is LanPublishResult.InProgress }

    if (showPlayer) {
        RemoteVideoPlayerDialog(
            title = item.video.fileName,
            streamUrl = viewModel.lanStreamUrl(item),
            onDismiss = { showPlayer = false },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            // bottom extra: mismo motivo que PublishForm en feature:upload --
            // cuando esta hoja se abre desde la tab "Subir" (puente del
            // NavHost) la cápsula flotante es un overlay, no reserva espacio,
            // y el botón "Publicar" quedaba tapable al final del scroll.
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp + 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
                Text(item.video.fileName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "← Elegir otro video",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        viewModel.resetLanPublishState()
                        onChooseAnother()
                    },
                )
                // FIX 2026-08-18: miniatura tocable con ícono play, antes de
                // los campos del formulario (mismo lugar que PublishForm en
                // feature:upload) -- ver showPlayer/RemoteVideoPlayerDialog
                // arriba.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPlayer = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val thumbnailUrl = viewModel.lanThumbnailUrl(item)
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Text(
                    "Esta PC sube el video ella misma -- el teléfono solo manda el pedido. No se puede pausar ni reanudar la subida desde acá.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Plataformas", style = MaterialTheme.typography.titleMedium)
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
                        // Mismo criterio que PublishForm en feature:upload:
                        // Facebook no es una plataforma publicable aparte, es
                        // un crosspost del mismo archivo que Instagram acepta
                        // (ver instagram-upload.controller.ts, local-backend).
                        if (platform == Platform.INSTAGRAM) {
                            LanFacebookCrossPostRow(
                                checked = crossPostFacebook,
                                enabled = Platform.INSTAGRAM in selectedPlatforms && !isSubmitting,
                                onCheckedChange = { crossPostFacebook = it },
                            )
                        }
                    }
                }
                if (Platform.TIKTOK in selectedPlatforms) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = tiktokPublic, onCheckedChange = { tiktokPublic = it }, enabled = !isSubmitting)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (tiktokPublic) "TikTok: público" else "TikTok: solo yo (privado)")
                    }
                }

                Button(
                    onClick = {
                        viewModel.publishLan(
                            item,
                            selectedPlatforms,
                            title,
                            description,
                            tiktokPublic,
                            crossPostFacebook = crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms,
                        )
                    },
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

// Mirror de FacebookCrossPostRow (feature:upload/UploadScreen.kt) -- no se
// puede importar directo por la prohibición feature→feature de esta
// arquitectura (ver comentario grande al inicio del archivo), así que queda
// duplicado a propósito, con el mismo texto y layout.
@Composable
private fun LanFacebookCrossPostRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, bottom = 4.dp),
    ) {
        Checkbox(checked = checked && enabled, enabled = enabled, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text("También publicar en Facebook", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Mismo video, como Reel en la Página vinculada a tu cuenta de Instagram.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
