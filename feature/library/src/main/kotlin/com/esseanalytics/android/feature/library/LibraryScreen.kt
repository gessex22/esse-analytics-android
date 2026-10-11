package com.esseanalytics.android.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.SwissButton
import com.esseanalytics.android.core.designsystem.component.SwissCard
import com.esseanalytics.android.core.designsystem.component.SwissChip
import com.esseanalytics.android.core.designsystem.component.SwissIconButton
import com.esseanalytics.android.core.designsystem.component.SwissScreenHeader
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.Swiss
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.io.File

private val FloatingNavClearance = 80.dp

// REDISEÑO BENTO: Biblioteca como retícula de tarjetas — cada video en su
// propia tarjeta con miniatura, título, badges de plataforma y acciones.
// Toda la funcionalidad intacta: filtros Todos/Local/Cola remota/Biblioteca
// LAN (animados), play, editar plataformas, borrar (con su diálogo según
// origen), sheets de detalle y reproductores.
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit = {},
    onOpenUpload: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val canUseCloudStorage by viewModel.canUseCloudStorage.collectAsState()
    val canSeeLanLibrary by viewModel.canSeeLanLibrary.collectAsState()
    val nextUploads by viewModel.nextUploads.collectAsState()
    var deleteTarget by remember { mutableStateOf<LibraryListItem?>(null) }
    var playingLocal by remember { mutableStateOf<VideoFile?>(null) }
    var playingRemote by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    var playingLan by remember { mutableStateOf<LibraryListItem.LanVideo?>(null) }
    var publishingLan by remember { mutableStateOf<LibraryListItem.LanVideo?>(null) }
    var editingFile by remember { mutableStateOf<VideoFile?>(null) }
    var editingRemoteVideo by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    var editingLan by remember { mutableStateOf<LibraryListItem.LanVideo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val systemNavigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(canUseCloudStorage) {
        if (canUseCloudStorage) viewModel.refreshRemote()
    }
    LaunchedEffect(Unit) { viewModel.refreshNextUploads() }
    // Descubrimiento corre SOLO mientras esta pantalla está visible --
    // ref-count compartido con Ajustes si esa pantalla también lo pide al
    // mismo tiempo (ver LanPcDiscoveryStore.start/stop), no un simple on/off
    // que se pisarían.
    DisposableEffect(Unit) {
        viewModel.startLanDiscovery()
        onDispose { viewModel.stopLanDiscovery() }
    }

    Scaffold(
        modifier = modifier,
        // Sin esto, Scaffold reserva DE NUEVO el alto de la barra de estado y
        // la barra de navegación del sistema (contentWindowInsets default =
        // systemBarsForVisualComponents) -- espacio que el Scaffold de afuera
        // (MainAppScaffold, compartido por las 4 pestañas del bottom nav) ya
        // consumió con su TopAppBar/NavigationBar. Se veía como una franja
        // vacía extra arriba y abajo SOLO en esta pantalla, porque es la
        // única con un Scaffold propio (lo necesita para el SnackbarHost).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Swiss.contentHorizontal, end = Swiss.contentHorizontal, top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SwissScreenHeader(
                    title = "Videos",
                    subtitle = if (items.isEmpty()) "Tu biblioteca" else "${items.size} en la biblioteca",
                )
                SwissButton(
                    text = "Importar video",
                    onClick = onImportClick,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                )
            }

            if (canUseCloudStorage || canSeeLanLibrary) {
                LibraryFilterRow(
                    filter = filter,
                    onFilterChange = viewModel::setFilter,
                    canUseCloudStorage = canUseCloudStorage,
                    canSeeLanLibrary = canSeeLanLibrary,
                )
            }

            if (items.isEmpty()) {
                PlaceholderScreen(
                    title = "Todavía no hay videos",
                    note = "Tocá \"Importar video\" arriba para agregar uno, o compartilo desde Galería con \"Compartir → EsseAnalytics\".",
                    icon = Icons.Outlined.VideoLibrary,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // La barra flotante global (FloatingBottomNavigation en
                    // EsseAnalyticsNavHost) es un overlay sobre el contenido, no
                    // reserva espacio propio ni aplica la barra de navegación del
                    // sistema a este contenido -- sin este margen abajo, tapaba la
                    // última tarjeta. Cápsula (62dp) + su margen inferior (2dp) +
                    // 16dp de aire, más el inset real de la navegación del sistema.
                    contentPadding = PaddingValues(
                        start = Swiss.contentHorizontal,
                        end = Swiss.contentHorizontal,
                        top = 8.dp,
                        bottom = FloatingNavClearance + systemNavigationBarInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Swiss.gap),
                ) {
                    items(
                        items,
                        key = { item ->
                            when (item) {
                                is LibraryListItem.Local -> "local_${item.file.id}"
                                is LibraryListItem.Remote -> "remote_${item.video._id}"
                                is LibraryListItem.LanVideo -> "lan_${item.video._id}"
                            }
                        },
                    ) { item ->
                        LibraryItemCard(
                            item,
                            nextUploads = nextUploads,
                            remoteThumbnailUrl = (item as? LibraryListItem.Remote)?.let { viewModel.thumbnailUrl(it.video) },
                            lanThumbnailUrl = (item as? LibraryListItem.LanVideo)?.let { viewModel.lanThumbnailUrl(it) },
                            onClick = {
                                when (item) {
                                    is LibraryListItem.Local -> editingFile = item.file
                                    is LibraryListItem.Remote -> editingRemoteVideo = item.video
                                    is LibraryListItem.LanVideo -> editingLan = item
                                }
                            },
                            onPlayClick = {
                                when (item) {
                                    is LibraryListItem.Local -> playingLocal = item.file
                                    is LibraryListItem.Remote -> playingRemote = item.video
                                    is LibraryListItem.LanVideo -> playingLan = item
                                }
                            },
                            onDeleteClick = { deleteTarget = item },
                            // Local edita contra Room (VideoDetailViewModel);
                            // Remote (todavía sin bajar -- Android no tiene forma
                            // de "descargar" un video de Nube a un registro local,
                            // a diferencia de iOS) edita directo contra
                            // RemoteLibraryVideoModel (RemoteVideoEditViewModel).
                            onEditPlatformsClick = when (item) {
                                is LibraryListItem.Local -> { { editingFile = item.file } }
                                is LibraryListItem.Remote -> { { editingRemoteVideo = item.video } }
                                is LibraryListItem.LanVideo -> { { editingLan = item } }
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
    playingLan?.let { item ->
        // Genérico -- title+streamUrl+onDismiss, sin nada específico de la
        // cola remota (ver investigación previa a esta implementación).
        RemoteVideoPlayerDialog(
            title = item.video.fileName,
            streamUrl = viewModel.lanStreamUrl(item),
            onDismiss = { playingLan = null },
        )
    }
    publishingLan?.let { item ->
        LocalPcPublishSheet(
            item = item,
            viewModel = viewModel,
            onDismiss = {
                publishingLan = null
                viewModel.resetLanPublishState()
            },
        )
    }

    editingFile?.let { file ->
        VideoDetailSheet(
            file = file,
            onDismiss = { editingFile = null },
            onPublish = {
                editingFile = null
                onOpenUpload(file.id)
            },
        )
    }
    editingRemoteVideo?.let { video ->
        RemoteVideoDetailSheet(
            video = video,
            streamUrl = viewModel.streamUrl(video),
            onDismiss = { editingRemoteVideo = null },
        )
    }
    editingLan?.let { item ->
        LanVideoDetailSheet(
            item = item,
            streamUrl = viewModel.lanStreamUrl(item),
            onDismiss = { editingLan = null },
            onPublish = {
                editingLan = null
                publishingLan = item
            },
        )
    }
}

// Remoto y Biblioteca LAN son gates DISTINTOS (canUseCloudStorage vs
// canSeeLanLibrary) -- un premium sin el entitlement de storage ve Todos/
// Local/Biblioteca LAN pero no Remoto; el owner ve los 4. canSeeLanLibrary es
// gratis (sin isPremium) -- un free con su PC en la LAN también ve el chip.
// FIX 2026-08-17: sin PC autorizada ahora mismo, el chip directamente no
// aparece (antes quedaba siempre visible para cualquier premium vía el
// mirror de metadata, sin bytes -- se veía como filas negras sin conexión).
@Composable
private fun LibraryFilterRow(
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    canUseCloudStorage: Boolean,
    canSeeLanLibrary: Boolean,
) {
    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .padding(horizontal = Swiss.contentHorizontal)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilterChip(LibraryFilter.ALL, filter, onFilterChange)
        LibraryFilterChip(LibraryFilter.LOCAL, filter, onFilterChange)
        // AnimatedVisibility (no un simple if) -- pedido explícito del
        // usuario: que el chip aparezca/desaparezca con una transición en
        // vez de aparecer de golpe cuando canUseCloudStorage/
        // canSeeLanLibrary cambian (ej. la PC de la LAN aparece o se
        // pierde). Sin transiciones custom -- mismo default (fadeIn+
        // expandIn / fadeOut+shrinkOut) que ya usan StatsScreen.kt/
        // UploadScreen.kt.
        AnimatedVisibility(visible = canUseCloudStorage) {
            LibraryFilterChip(LibraryFilter.REMOTE, filter, onFilterChange)
        }
        AnimatedVisibility(visible = canSeeLanLibrary) {
            LibraryFilterChip(LibraryFilter.LAN, filter, onFilterChange)
        }
    }
}

@Composable
private fun LibraryFilterChip(entry: LibraryFilter, filter: LibraryFilter, onFilterChange: (LibraryFilter) -> Unit) {
    SwissChip(
        selected = filter == entry,
        onClick = { onFilterChange(entry) },
        label = libraryFilterLabel(entry),
    )
}

private fun libraryFilterLabel(filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> "Todos"
    LibraryFilter.LOCAL -> "Local"
    LibraryFilter.REMOTE -> "Cola remota"
    LibraryFilter.LAN -> "Biblioteca LAN"
}

// Tarjeta bento por video: miniatura, título, duración y estado por
// plataforma, acciones como botones de icono en tarjeta. El tap abre el
// detalle; play, editar y borrar mantienen su acción.
@Composable
private fun LibraryItemCard(
    item: LibraryListItem,
    nextUploads: Map<Platform, String>,
    remoteThumbnailUrl: String?,
    lanThumbnailUrl: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditPlatformsClick: (() -> Unit)? = null,
) {
    SwissCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                when (item) {
                    is LibraryListItem.Local -> LocalThumbnail(item.file.thumbnailPath)
                    // El JWT viaja como ?token= en la URL (ver remoteLibraryThumbnailUrl),
                    // no como header -- por eso no hace falta un ImageLoader de Coil
                    // custom, AsyncImage normal alcanza.
                    is LibraryListItem.Remote -> Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 52.dp)
                            .clip(RoundedCornerShape(Swiss.cardSmall))
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
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    is LibraryListItem.LanVideo -> Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 52.dp)
                            .clip(RoundedCornerShape(Swiss.cardSmall))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (lanThumbnailUrl != null) {
                            AsyncImage(
                                model = lanThumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = rememberVectorPainter(Icons.Outlined.Computer),
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Computer,
                                contentDescription = "Video en tu PC (LAN)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                PlayBadge(onClick = onPlayClick, modifier = Modifier.align(Alignment.BottomEnd))
            }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val (durationLabel, resolution) = when (item) {
                    is LibraryListItem.Local -> (item.file.duracionSegundos?.let { "${it}s" } ?: "—") to item.file.resolucion
                    is LibraryListItem.Remote -> (item.video.durationSeconds?.let { "${it.toInt()}s" } ?: "—") to item.video.resolution
                    is LibraryListItem.LanVideo -> (item.video.duracion_segundos?.let { "${it.toInt()}s" } ?: "—") to item.video.resolucion
                }
                Text(
                    "$durationLabel · ${resolution ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val (published, discarded) = when (item) {
                    is LibraryListItem.Local -> item.file.platforms to item.file.platformsDiscarded.map { it.apiValue }
                    is LibraryListItem.Remote -> item.video.platforms.mapNotNull { Platform.fromApiValue(it) } to item.video.platformsDiscarded
                    is LibraryListItem.LanVideo -> item.video.platforms.mapNotNull { Platform.fromApiValue(it) } to item.video.platforms_discarded
                }
                PlatformBadgeRow(published = published, discarded = discarded)
                val nextFor = Platform.publishable.filter { nextUploads[it] == item.displayName }
                if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onEditPlatformsClick != null) {
                    SwissIconButton(onClick = onEditPlatformsClick) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Editar plataformas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // Sin botón de borrar para LanVideo -- el archivo vive en la PC,
                // no hay nada que la app pueda eliminar desde acá (ver
                // LibraryViewModel.delete()).
                if (item !is LibraryListItem.LanVideo) {
                    SwissIconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Eliminar video",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
// distinción demasiado sutil para leerse de un vistazo.
@Composable
private fun PlatformBadgeRow(published: List<Platform>, discarded: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
// espera que salga a continuación.
@Composable
private fun NextUploadBadgeRow(platforms: List<Platform>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        platforms.forEach { platform ->
            val color = platformColor(platform)
            Text(
                "Próximo · ${platformShortLabel(platform)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
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
    val dotColor = when (state) {
        PlatformBadgeState.PUBLISHED -> color
        PlatformBadgeState.DISCARDED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        PlatformBadgeState.PENDING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, shape = androidx.compose.foundation.shape.CircleShape),
        )
        val icon = platformIcon(platform)
        if (icon != null) {
            Icon(
                icon,
                contentDescription = platformShortLabel(platform),
                tint = if (state == PlatformBadgeState.PUBLISHED) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                platformShortLabel(platform),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// Badge sobrio en la esquina de la miniatura -- así el resto del thumbnail
// sigue mandando el tap al onClick de la tarjeta (que abre el detalle), solo
// este bloque dispara el reproductor.
@Composable
private fun PlayBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(24.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(scheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Reproducir",
            tint = scheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun LocalThumbnail(thumbnailPath: String?) {
    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 52.dp)
            .clip(RoundedCornerShape(Swiss.cardSmall))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailPath != null) {
            AsyncImage(
                model = File(thumbnailPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
