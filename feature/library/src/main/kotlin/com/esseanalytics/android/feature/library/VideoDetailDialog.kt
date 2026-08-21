package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.esseanalytics.android.core.model.Platform

// Cuerpo visual único de los detalles Local, Nube y PC/LAN. Los wrappers
// aportan el reproductor y las acciones específicas; estados y filas se
// dibujan siempre con la misma jerarquía.
@Composable
internal fun VideoDetailDialog(
    title: String,
    metadata: String,
    onDismiss: () -> Unit,
    stateFor: (Platform) -> PlatformBadgeState,
    hasLink: (Platform) -> Boolean,
    onToggle: (Platform) -> Unit,
    onEditLink: (Platform) -> Unit,
    errorMessage: String?,
    player: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                player()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        if (metadata.isNotBlank()) {
                            Text(
                                metadata,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
                            )
                        }
                    }
                    item { Text("Estado por plataforma", style = MaterialTheme.typography.titleSmall) }
                    items(Platform.publishable, key = { it.apiValue }) { platform ->
                        VideoDetailPlatformRow(
                            platform = platform,
                            status = stateFor(platform),
                            hasLink = hasLink(platform),
                            onToggleStatus = { onToggle(platform) },
                            onEditLink = { onEditLink(platform) },
                        )
                    }
                    errorMessage?.let { message ->
                        item {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item { actions() }
                    item {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Cerrar", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
