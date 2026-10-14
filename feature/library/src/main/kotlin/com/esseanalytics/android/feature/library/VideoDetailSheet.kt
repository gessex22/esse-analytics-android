package com.esseanalytics.android.feature.library

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VideoDetailSheet(
    file: VideoFile,
    onDismiss: () -> Unit,
    onPublish: () -> Unit = {},
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(file.id) {
        viewModel.setInitial(file)
        // Bug real SYNC-01 #2 -- ver comentario completo en
        // VideoDetailViewModel.backfillRemoteLinks. Best-effort, no bloquea
        // la apertura del detalle si falla.
        viewModel.backfillRemoteLinks(file)
    }
    val currentFile by viewModel.file.collectAsState()
    val shownFile = currentFile ?: file
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val linkedPlatforms by remember(file.id) { viewModel.linkedPlatforms(file.id) }
        .collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var galleryStatus by remember { mutableStateOf<VideoSaveStatus>(VideoSaveStatus.Idle) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    VideoDetailDialog(
        title = shownFile.fileName,
        metadata = listOfNotNull(
            shownFile.resolucion?.let { "Resolución: $it" },
            shownFile.duracionSegundos?.let { "Duración: ${it}s" },
            shownFile.formato?.let { "Formato: $it" },
        ).joinToString(" · "),
        playbackUri = shownFile.filePath.takeIf(String::isNotBlank)?.let(Uri::parse),
        playbackErrorText = "No hay una ruta de reproducción disponible para este video.",
        onDismiss = onDismiss,
        stateFor = { platform ->
            when {
                platform in shownFile.platforms -> PlatformBadgeState.PUBLISHED
                platform in shownFile.platformsDiscarded -> PlatformBadgeState.DISCARDED
                else -> PlatformBadgeState.PENDING
            }
        },
        hasLink = { it in linkedPlatforms },
        onToggle = viewModel::togglePlatform,
        onEditLink = { platform ->
            scope.launch {
                linkEditorText = viewModel.existingLink(file.id, platform) ?: ""
                linkEditorPlatform = platform
            }
        },
        errorMessage = errorMessage ?: galleryError,
        actions = {
            val galleryRunning = galleryStatus is VideoSaveStatus.Running
            DetailPrimaryAction(
                text = "Publicar",
                icon = Icons.Outlined.CloudUpload,
                enabled = !galleryRunning,
                onClick = onPublish,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            DetailSaveAction(
                idleText = "Guardar en galería",
                subtitle = "Crea una copia en la galería del teléfono",
                savedText = "Guardado en galería",
                icon = Icons.Outlined.Download,
                status = galleryStatus,
                enabled = !galleryRunning,
                onClick = {
                    galleryError = null
                    galleryStatus = VideoSaveStatus.Running
                    scope.launch {
                        val status = saveVideoToGallery(context, shownFile).toVideoSaveStatus()
                        galleryStatus = status
                        galleryError = (status as? VideoSaveStatus.Failed)?.message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    linkEditorPlatform?.let { platform ->
        CreatorHomeTheme {
            LinkEditorDialog(
                platform = platform,
                text = linkEditorText,
                onTextChange = { linkEditorText = it },
                isSaving = isSaving,
                onDismiss = { linkEditorPlatform = null },
                onSave = {
                    viewModel.saveLink(platform, linkEditorText)
                    linkEditorPlatform = null
                },
            )
        }
    }
}

private suspend fun saveVideoToGallery(
    context: android.content.Context,
    file: VideoFile,
): GallerySaveResult =
    withContext(Dispatchers.IO) {
        try {
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
                GallerySaveResult.Saved
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            GallerySaveResult.Failed("No se pudo guardar: ${error.message ?: "error desconocido"}")
        }
    }

@Composable
internal fun VideoDetailPlatformRow(
    platform: Platform,
    status: PlatformBadgeState,
    hasLink: Boolean,
    onToggleStatus: () -> Unit,
    onEditLink: () -> Unit,
) {
    val color = platformColor(platform)
    val label = when (status) {
        PlatformBadgeState.PUBLISHED -> if (hasLink) "Publicado" else "Publicado · Sin enlace"
        PlatformBadgeState.DISCARDED -> "Descartado"
        PlatformBadgeState.PENDING -> "Pendiente"
    }
    val stateColor = if (status == PlatformBadgeState.PUBLISHED) color else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleStatus, role = Role.Button)
                .semantics(mergeDescendants = true) { contentDescription = "${platformShortLabel(platform)}: $label" }
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryStudioPlatformBadge(platform, status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(platformShortLabel(platform), style = MaterialTheme.typography.bodyMedium)
                Text(label, style = MaterialTheme.typography.labelMedium, color = stateColor)
            }
        }
        if (status == PlatformBadgeState.PUBLISHED) {
            IconButton(onClick = onEditLink, modifier = Modifier.size(48.dp)) {
                Icon(if (hasLink) Icons.Outlined.Link else Icons.Outlined.LinkOff, contentDescription = "Editar enlace de ${platformShortLabel(platform)}", tint = MaterialTheme.colorScheme.primary)
            }
        }
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
        confirmButton = { TextButton(onClick = onSave, enabled = !isSaving) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") } },
    )
}
