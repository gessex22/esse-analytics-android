package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import kotlinx.coroutines.launch

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
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(file.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                "Marcar publicado a mano y cargar el link real de cada plataforma.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )

            Platform.publishable.forEach { platform ->
                VideoDetailPlatformRow(
                    platform = platform,
                    status = when {
                        platform in file.platforms -> PlatformBadgeState.PUBLISHED
                        platform in file.platformsDiscarded -> PlatformBadgeState.DISCARDED
                        else -> PlatformBadgeState.PENDING
                    },
                    hasLink = platform in file.platforms,
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

        val (background, content, label) = when (status) {
            PlatformBadgeState.PUBLISHED -> Triple(color.copy(alpha = 0.15f), color, "Publicado")
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
