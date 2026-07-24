package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import kotlinx.coroutines.launch
import java.io.File

// Historial de todo lo importado -- no solo una lista, cada tarjeta muestra
// el estado real por plataforma (publicado/descartado/pendiente, ver
// VideoFile.platforms/platformsDiscarded). Tocarla decide adónde ir según
// ese estado (ver EsseAnalyticsNavHost): sin nada publicado todavía, a Subir
// con ese archivo ya elegido; con algo ya publicado, a Estadísticas.
// Miniatura real (AndroidFrameThumbnailGenerator, ver core:media).
//
// Fusión local+remoto (Parte D del plan): si el usuario tiene
// canUseCloudStorage, esta lista TAMBIÉN trae la cola remota (central) y
// muestra chips de filtro arriba -- sin el entitlement, se ve exactamente
// igual que antes (cero chips, solo locales).
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit = {},
    onLocalClick: (VideoFile) -> Unit = {},
    onRemoteClick: (RemoteLibraryVideoDto) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val canUseCloudStorage by viewModel.canUseCloudStorage.collectAsState()
    val canSeeBackupCatalog by viewModel.canSeeBackupCatalog.collectAsState()
    val nextUploads by viewModel.nextUploads.collectAsState()
    var deleteTarget by remember { mutableStateOf<LibraryListItem?>(null) }
    var playingLocal by remember { mutableStateOf<VideoFile?>(null) }
    var playingRemote by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    var editingFile by remember { mutableStateOf<VideoFile?>(null) }
    var editingRemoteVideo by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(canUseCloudStorage) {
        if (canUseCloudStorage) viewModel.refreshRemote()
    }
    LaunchedEffect(canSeeBackupCatalog) {
        if (canSeeBackupCatalog) viewModel.refreshBackupCatalog()
    }
    LaunchedEffect(Unit) { viewModel.refreshNextUploads() }

    Scaffold(
        modifier = modifier,
        // Sin esto, Scaffold reserva DE NUEVO el alto de la barra de estado y
        // la barra de navegación del sistema (contentWindowInsets default =
        // systemBarsForVisualComponents) -- espacio que el Scaffold de afuera
        // (MainAppScaffold, compartido por las 4 pestañas del bottom nav) ya
        // consumió con su TopAppBar/NavigationBar. Se veía como una franja
        // vacía extra arriba y abajo SOLO en esta pantalla, porque es la
        // única con un Scaffold propio (lo necesita para el FAB).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onImportClick) {
                Icon(Icons.Outlined.Add, contentDescription = "Importar video")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (canUseCloudStorage || canSeeBackupCatalog) {
                LibraryFilterChips(
                    filter = filter,
                    onFilterChange = viewModel::setFilter,
                    canUseCloudStorage = canUseCloudStorage,
                    canSeeBackupCatalog = canSeeBackupCatalog,
                )
            }

            if (items.isEmpty()) {
                PlaceholderScreen(
                    title = "Todavía no hay videos",
                    note = "Tocá + para importar uno, o compartilo desde Galería con \"Compartir → EsseAnalytics\".",
                    icon = Icons.Outlined.VideoLibrary,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // bottom=80: el FloatingActionButton de Scaffold flota SOBRE
                    // el contenido, no reserva espacio propio -- sin este margen
                    // extra abajo, el "+" tapaba la última tarjeta visible. FAB
                    // (56dp) + su margen por default de Scaffold (16dp) + 8dp de
                    // aire = 80dp -- lo mínimo para despejarlo (antes 96dp dejaba
                    // una franja vacía innecesariamente grande con listas cortas,
                    // que se ve como un borde oscuro pegado al bottom nav).
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items,
                        key = { item ->
                            when (item) {
                                is LibraryListItem.Local -> "local_${item.file.id}"
                                is LibraryListItem.Remote -> "remote_${item.video._id}"
                                is LibraryListItem.BackupCatalog -> "backup_${item.entry.file_name}"
                            }
                        },
                    ) { item ->
                        LibraryItemCard(
                            item,
                            nextUploads = nextUploads,
                            remoteThumbnailUrl = (item as? LibraryListItem.Remote)?.let { viewModel.thumbnailUrl(it.video) },
                            onClick = {
                                when (item) {
                                    is LibraryListItem.Local -> onLocalClick(item.file)
                                    is LibraryListItem.Remote -> onRemoteClick(item.video)
                                    // Solo lectura -- sin bytes, sin adónde navegar (ver
                                    // BackupApi). Avisa por qué en vez de no hacer nada.
                                    is LibraryListItem.BackupCatalog -> scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Este archivo vive en tu PC -- no se puede reproducir ni publicar desde acá.",
                                        )
                                    }
                                }
                            },
                            onPlayClick = {
                                when (item) {
                                    is LibraryListItem.Local -> playingLocal = item.file
                                    is LibraryListItem.Remote -> playingRemote = item.video
                                    is LibraryListItem.BackupCatalog -> Unit
                                }
                            },
                            onDeleteClick = { deleteTarget = item },
                            // Local edita contra Room (VideoDetailViewModel);
                            // Remote (todavía sin bajar -- Android no tiene forma
                            // de "descargar" un video de Nube a un registro local,
                            // a diferencia de iOS) edita directo contra
                            // RemoteLibraryVideoModel (RemoteVideoEditViewModel).
                            // BackupCatalog es de solo lectura, sin editor.
                            onEditPlatformsClick = when (item) {
                                is LibraryListItem.Local -> { { editingFile = item.file } }
                                is LibraryListItem.Remote -> { { editingRemoteVideo = item.video } }
                                is LibraryListItem.BackupCatalog -> null
                            },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteItemDialog(
            item = target,
            onConfirm = {
                viewModel.delete(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    playingLocal?.let { file ->
        LocalVideoPlayerDialog(file = file, onDismiss = { playingLocal = null })
    }
    playingRemote?.let { video ->
        RemoteVideoPlayerDialog(
            title = video.fileName,
            streamUrl = viewModel.streamUrl(video),
            onDismiss = { playingRemote = null },
        )
    }

    editingFile?.let { file ->
        VideoDetailSheet(file = file, onDismiss = { editingFile = null })
    }
    editingRemoteVideo?.let { video ->
        RemoteVideoDetailSheet(video = video, onDismiss = { editingRemoteVideo = null })
    }
}

// Remoto y Catálogo PC son gates DISTINTOS (canUseCloudStorage vs
// canSeeBackupCatalog) -- un premium sin el entitlement de storage ve Todos/
// Local/Catálogo PC pero no Remoto; el owner ve los 4.
@Composable
private fun LibraryFilterChips(
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    canUseCloudStorage: Boolean,
    canSeeBackupCatalog: Boolean,
) {
    val visibleFilters = buildList {
        add(LibraryFilter.ALL)
        add(LibraryFilter.LOCAL)
        if (canUseCloudStorage) add(LibraryFilter.REMOTE)
        if (canSeeBackupCatalog) add(LibraryFilter.BACKUP_CATALOG)
    }
    // horizontalScroll, NO fillMaxWidth -- con los 4 chips (owner) el ancho no
    // entra en pantallas angostas; sin scroll, Row comprime el último chip
    // hasta que su texto envuelve letra por letra en una columna pegada al borde.
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleFilters.forEach { entry ->
            FilterChip(
                selected = filter == entry,
                onClick = { onFilterChange(entry) },
                label = { Text(libraryFilterLabel(entry)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

private fun libraryFilterLabel(filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> "Todos"
    LibraryFilter.LOCAL -> "Local"
    LibraryFilter.REMOTE -> "Cola remota"
    LibraryFilter.BACKUP_CATALOG -> "Catálogo PC"
}

@Composable
private fun LibraryItemCard(
    item: LibraryListItem,
    nextUploads: Map<Platform, String>,
    remoteThumbnailUrl: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditPlatformsClick: (() -> Unit)? = null,
) {
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
            Box {
                when (item) {
                    is LibraryListItem.Local -> LocalThumbnail(item.file.thumbnailPath)
                    // El JWT viaja como ?token= en la URL (ver remoteLibraryThumbnailUrl),
                    // no como header -- por eso no hace falta un ImageLoader de Coil
                    // custom, AsyncImage normal alcanza. Mismo Box con fondo que
                    // OriginThumbnail SIEMPRE (antes el branch de AsyncImage no lo
                    // tenía -- un fallo de carga dejaba la fila completamente en
                    // blanco, sin caja ni ícono, en vez de degradar al ícono de nube).
                    is LibraryListItem.Remote -> Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (remoteThumbnailUrl != null) {
                            AsyncImage(
                                model = remoteThumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = rememberVectorPainter(Icons.Outlined.CloudQueue),
                            )
                        } else {
                            Icon(
                                Icons.Outlined.CloudQueue,
                                contentDescription = "Video en la cola remota",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    // Ícono de PC en vez de nube -- distingue de un vistazo la
                    // cola remota (publicable) del catálogo de solo lectura.
                    is LibraryListItem.BackupCatalog -> OriginThumbnail(Icons.Outlined.Computer, "Video en tu PC")
                }

                // Sin bytes reproducibles para el catálogo de solo lectura (ver
                // el snackbar de onClick más arriba) -- ningún play acá.
                if (item !is LibraryListItem.BackupCatalog) {
                    PlayBadge(onClick = onPlayClick, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val nextFor = Platform.publishable.filter { nextUploads[it] == item.displayName }
                if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
                when (item) {
                    is LibraryListItem.Local -> {
                        val durationLabel = item.file.duracionSegundos?.let { "${it}s" } ?: "—"
                        Text(
                            "$durationLabel · ${item.file.resolucion ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PlatformBadgeRow(published = item.file.platforms, discarded = item.file.platformsDiscarded.map { it.apiValue })
                    }
                    is LibraryListItem.Remote -> {
                        val durationLabel = item.video.durationSeconds?.let { "${it.toInt()}s" } ?: "—"
                        Text(
                            "$durationLabel · ${item.video.resolution ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PlatformBadgeRow(
                            published = item.video.platforms.mapNotNull { Platform.fromApiValue(it) },
                            discarded = item.video.platformsDiscarded,
                        )
                    }
                    is LibraryListItem.BackupCatalog -> {
                        val durationLabel = item.entry.duracion_segundos?.let { "${it.toInt()}s" } ?: "—"
                        Text(
                            "$durationLabel · ${item.entry.resolucion ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PlatformBadgeRow(
                            published = item.entry.platforms.mapNotNull { Platform.fromApiValue(it) },
                            discarded = item.entry.platforms_discarded,
                        )
                    }
                }
            }

            // Marcar publicado a mano + cargar el link real -- solo tiene
            // sentido en local (ver onEditPlatformsClick en LibraryScreen).
            // size(36.dp) en vez del default de IconButton (48dp): con los 2
            // botones a full size no quedaba ancho para el título -- se
            // truncaba mucho antes de lo necesario en pantallas angostas.
            if (onEditPlatformsClick != null) {
                IconButton(onClick = onEditPlatformsClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Editar plataformas",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Sin botón de borrar para el catálogo -- es un mirror de solo
            // lectura, no hay nada que la app pueda eliminar desde acá (ver
            // LibraryViewModel.delete()).
            if (item !is LibraryListItem.BackupCatalog) {
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Eliminar video",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteItemDialog(item: LibraryListItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val isReferenceOnly = (item as? LibraryListItem.Local)?.file?.filePath?.startsWith("content://") == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar \"${item.displayName}\"?") },
        text = {
            Text(
                when {
                    item is LibraryListItem.Remote -> "Esto borra el video de la nube. No se puede deshacer."
                    isReferenceOnly -> "EsseAnalytics solo tenía una referencia a este video -- se quita de la app, pero el archivo original sigue donde estaba (Galería/Archivos)."
                    else -> "Esto borra el archivo del almacenamiento del teléfono. No se puede deshacer."
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
// publicado. published/discarded llegan como listas ya normalizadas (Platform
// para local, String->Platform para remoto) para no duplicar esta función.
@Composable
private fun PlatformBadgeRow(published: List<Platform>, discarded: List<String>) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Platform.publishable.forEach { platform ->
            val state = when {
                platform in published -> PlatformBadgeState.PUBLISHED
                platform.apiValue in discarded -> PlatformBadgeState.DISCARDED
                else -> PlatformBadgeState.PENDING
            }
            PlatformBadge(platform, state)
        }
    }
}

// "Próximo" a publicar en cada plataforma según el calendario (central) --
// distinto de PlatformBadgeRow (que muestra el estado publicado/descartado/
// pendiente): esto avisa CUÁL de los pendientes es el que el calendario
// espera que salga a continuación, algo que antes no se veía en ningún
// lado de la lista (ni acá ni en el picker de Subir).
@Composable
private fun NextUploadBadgeRow(platforms: List<Platform>) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        platforms.forEach { platform ->
            val color = platformColor(platform)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "Próximo ${platformShortLabel(platform)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// internal, no private -- VideoDetailSheet.kt (editor manual de plataformas/
// links) también distingue estos 3 estados.
internal enum class PlatformBadgeState { PUBLISHED, DISCARDED, PENDING }

// internal, no private -- VideoDetailSheet.kt (editor manual de plataformas/
// links) las reusa para no duplicar la paleta de marca.
internal fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> Color.Gray
}

internal fun platformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}

// Logo real (mismo SVG que frontend/src/components/icons/PlatformLogos.tsx)
// en vez de iniciales de texto -- Facebook no tiene logo propio ahí tampoco
// (es crosspost, no una plataforma publicable directa), se queda con el
// fallback de texto.
internal fun platformIcon(platform: Platform): ImageVector? = when (platform) {
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

// Badge chico en la esquina de la miniatura en vez de cubrirla entera -- así
// el resto del thumbnail sigue mandando el tap al onClick de la Card (que va
// a Estadísticas/Subir/publish-form, ver LibraryScreen), solo este círculo
// dispara el reproductor.
@Composable
private fun PlayBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Reproducir",
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun LocalThumbnail(thumbnailPath: String?) {
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

// Sin miniatura real para remoto/catálogo -- ninguno de los dos tiene bytes
// accesibles desde acá (ver el comentario en LibraryItemCard). Un solo
// composable parametrizado por ícono en vez de uno por origen.
@Composable
private fun OriginThumbnail(icon: ImageVector, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
