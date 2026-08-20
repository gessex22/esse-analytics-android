package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

// Editor manual de "publicado + link real" por plataforma, para un archivo
// local -- mirror de VideoDetailView (iOS): tocar el badge cicla pendiente ->
// publicado -> descartado -> pendiente, tocar el ícono de link abre el
// editor de URL. Ambas acciones se propagan a la central si el archivo viene
// de Nube (ver VideoDetailViewModel).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailSheet(
    file: VideoFile,
    onDismiss: () -> Unit,
    onPublish: () -> Unit = {},
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    // BUG-2026-08-15-03: antes `hasLink` usaba `platform in file.platforms`,
    // tautológicamente igual a la condición PUBLISHED de la fila -- el
    // ícono LinkOff/label "Sin enlace" nunca se mostraba. Reactivo por
    // file.id para reflejar altas/bajas de link sin recomposición manual.
    val linkedPlatforms by remember(file.id) { viewModel.linkedPlatforms(file.id) }
        .collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var galleryMessage by remember { mutableStateOf<String?>(null) }
    val exoPlayer = remember(file.filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(file.filePath)))
            playWhenReady = true
            prepare()
        }
    }
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { PlayerView(context).apply { player = exoPlayer } },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
            Text(file.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                listOfNotNull(
                    file.resolucion?.let { "Resolución: $it" },
                    file.duracionSegundos?.let { "Duración: ${it}s" },
                    file.formato?.let { "Formato: $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    scope.launch {
                        galleryMessage = saveVideoToGallery(context, file)
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text("Guardar en galería")
                }
                TextButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                    Text("Publicar")
                }
            }
            galleryMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

                    }
                    item { Text("Estado por plataforma", style = MaterialTheme.typography.titleSmall) }
                    item {
            Platform.publishable.forEach { platform ->
                VideoDetailPlatformRow(
                    platform = platform,
                    status = when {
                        platform in file.platforms -> PlatformBadgeState.PUBLISHED
                        platform in file.platformsDiscarded -> PlatformBadgeState.DISCARDED
                        else -> PlatformBadgeState.PENDING
                    },
                    hasLink = platform in linkedPlatforms,
                    onToggleStatus = { viewModel.togglePlatform(file, platform) },
                    onEditLink = {
                        scope.launch {
                            linkEditorText = viewModel.existingLink(file.id, platform) ?: ""
                            linkEditorPlatform = platform
                        }
                    },
                )
            }

            errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
                    }
                    item {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Cerrar", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    linkEditorPlatform?.let { platform ->
        LinkEditorDialog(
            platform = platform,
            text = linkEditorText,
            onTextChange = { linkEditorText = it },
            isSaving = isSaving,
            onDismiss = { linkEditorPlatform = null },
            onSave = {
                viewModel.saveLink(file, platform, linkEditorText)
                linkEditorPlatform = null
            },
        )
    }

}

private suspend fun saveVideoToGallery(context: android.content.Context, file: VideoFile): String {
    return withContext(Dispatchers.IO) { runCatching {
        val source = File(file.filePath)
        require(source.exists()) { "No se encontró el archivo de video." }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/${source.extension.ifEmpty { "mp4" }}")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EsseAnalytics")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo en la galería.")
        try {
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: error("No se pudo escribir el video.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Video guardado en Películas/EsseAnalytics"
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }.getOrElse { "No se pudo guardar: ${it.message ?: "error desconocido"}" } }
}

// internal, no private -- RemoteVideoDetailSheet.kt (editor equivalente para
// un video que todavía vive SOLO en Nube, sin bajar a Room) reusa esta fila
// y el diálogo de abajo para no duplicar la UI.
@Composable
internal fun VideoDetailPlatformRow(
    platform: Platform,
    status: PlatformBadgeState,
    hasLink: Boolean,
    onToggleStatus: () -> Unit,
    onEditLink: () -> Unit,
) {
    val color = platformColor(platform)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = platformIcon(platform)
        if (icon != null) {
            Icon(icon, contentDescription = platformShortLabel(platform), tint = color, modifier = Modifier.size(20.dp))
        }
        Text(
            platformShortLabel(platform),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )

        // Solo tiene sentido con la plataforma ya publicada -- pendiente/
        // descartado no tiene un link que editar todavía.
        if (status == PlatformBadgeState.PUBLISHED) {
            IconButton(onClick = onEditLink) {
                Icon(
                    if (hasLink) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                    contentDescription = "Editar link",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // BUG-2026-08-15-03: el ícono de arriba (Link vs LinkOff) ya
        // distinguía un link real de una marca manual sin link -- el label
        // del chip ahora también lo hace explícito, mismo criterio que
        // VideoDetailView.swift/RemoteVideoDetailView.swift (iOS).
        val (background, content, label) = when (status) {
            PlatformBadgeState.PUBLISHED -> Triple(
                color.copy(alpha = 0.15f),
                color,
                if (hasLink) "Publicado" else "Publicado · Sin enlace",
            )
            PlatformBadgeState.DISCARDED -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                "Descartado",
            )
            PlatformBadgeState.PENDING -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Pendiente",
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(background)
                .clickable(onClick = onToggleStatus)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun LinkEditorDialog(
    platform: Platform,
    text: String,
    onTextChange: (String) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Link de ${platformShortLabel(platform)}") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isSaving) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !isSaving) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        },
    )
}
