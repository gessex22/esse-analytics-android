package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import java.io.File

// Historial de todo lo importado -- no solo una lista, cada tarjeta muestra
// el estado real por plataforma (publicado/descartado/pendiente, ver
// VideoFile.platforms/platformsDiscarded). Tocarla decide adónde ir según
// ese estado (ver EsseAnalyticsNavHost): sin nada publicado todavía, a Subir
// con ese archivo ya elegido; con algo ya publicado, a Estadísticas.
// Miniatura real (AndroidFrameThumbnailGenerator, ver core:media).
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit = {},
    onVideoClick: (VideoFile) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val files by viewModel.files.collectAsState()
    var deleteTarget by remember { mutableStateOf<VideoFile?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onImportClick) {
                Icon(Icons.Outlined.Add, contentDescription = "Importar video")
            }
        },
    ) { padding ->
        if (files.isEmpty()) {
            PlaceholderScreen(
                title = "Todavía no hay videos",
                note = "Tocá + para importar uno, o compartilo desde Galería con \"Compartir → EsseAnalytics\".",
                icon = Icons.Outlined.VideoLibrary,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.id }) { file ->
                    VideoFileCard(
                        file,
                        onClick = { onVideoClick(file) },
                        onDeleteClick = { deleteTarget = file },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteVideoDialog(
            file = target,
            onConfirm = {
                viewModel.delete(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun VideoFileCard(file: VideoFile, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    // elevation = 0.dp: la elevación por defecto de Card mezcla el color
    // primario sobre la superficie -- se ve como una tarjeta más clara/tibia
    // de lo que pide el tema (--card plano en theme.css). Mismo fix en todas
    // las Card() de la app.
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumbnail(file.thumbnailPath)

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(file.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val durationLabel = file.duracionSegundos?.let { "${it}s" } ?: "—"
                Text(
                    "$durationLabel · ${file.resolucion ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlatformBadgeRow(file)
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Eliminar video",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// El texto cambia según si hay algo que de verdad se borra del teléfono
// (copia propia, filesDir/videos) o si el archivo nunca se copió (referencia
// persistida vía SAF, ver DeleteVideoUseCase/ImportUseCase) -- ahí solo se
// suelta la referencia, el video del usuario no se toca.
@Composable
private fun DeleteVideoDialog(file: VideoFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val isReferenceOnly = file.filePath.startsWith("content://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar \"${file.fileName}\"?") },
        text = {
            Text(
                if (isReferenceOnly) {
                    "EsseAnalytics solo tenía una referencia a este video -- se quita de la app, pero el archivo original sigue donde estaba (Galería/Archivos)."
                } else {
                    "Esto borra el archivo del almacenamiento del teléfono. No se puede deshacer."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// Regla simple: color de marca SOLO si está publicado, gris en cualquier
// otro caso (pendiente o descartado) -- antes pendiente quedaba con el
// círculo vacío/transparente y descartado con un gris apenas distinto, una
// distinción demasiado sutil para leerse de un vistazo. Ahora los dos son
// el mismo círculo gris relleno; descartado se ve un poco más apagado
// (alpha más bajo) que pendiente, pero ninguno de los dos se confunde con
// publicado.
@Composable
private fun PlatformBadgeRow(file: VideoFile) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Platform.publishable.forEach { platform ->
            val state = when {
                platform in file.platforms -> PlatformBadgeState.PUBLISHED
                platform in file.platformsDiscarded -> PlatformBadgeState.DISCARDED
                else -> PlatformBadgeState.PENDING
            }
            PlatformBadge(platform, state)
        }
    }
}

private enum class PlatformBadgeState { PUBLISHED, DISCARDED, PENDING }

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> Color.Gray
}

private fun platformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}

// Logo real (mismo SVG que frontend/src/components/icons/PlatformLogos.tsx)
// en vez de iniciales de texto -- Facebook no tiene logo propio ahí tampoco
// (es crosspost, no una plataforma publicable directa), se queda con el
// fallback de texto.
private fun platformIcon(platform: Platform): ImageVector? = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> null
}

@Composable
private fun PlatformBadge(platform: Platform, state: PlatformBadgeState) {
    val color = platformColor(platform)
    val (background, content) = when (state) {
        PlatformBadgeState.PUBLISHED -> color.copy(alpha = 0.15f) to color
        PlatformBadgeState.DISCARDED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        PlatformBadgeState.PENDING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val icon = platformIcon(platform)
        if (icon != null) {
            Icon(
                icon,
                contentDescription = platformShortLabel(platform),
                tint = content,
                modifier = Modifier.size(11.dp),
            )
        } else {
            Text(
                platformShortLabel(platform).take(1),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun VideoThumbnail(thumbnailPath: String?) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailPath != null) {
            AsyncImage(
                model = File(thumbnailPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
