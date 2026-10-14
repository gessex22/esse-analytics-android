package com.esseanalytics.android.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.CreatorLoadingBar
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.component.CreatorVideoStyle
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.io.File
import kotlin.math.roundToInt

private val FloatingNavClearance = 124.dp

// Videos usa una lista Creator continua. Las hojas y reproductores conservan
// sus flujos y estados existentes.
@Composable
fun LibraryScreen(
    onImportClick: () -> Unit = {},
    onOpenUpload: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    username: String = "",
    onProfileClick: () -> Unit = {},
    isRefreshing: Boolean = false,
) {
    val items by viewModel.items.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val canUseCloudStorage by viewModel.canUseCloudStorage.collectAsState()
    val canSeeLanLibrary by viewModel.canSeeLanLibrary.collectAsState()
    val nextUploads by viewModel.nextUploads.collectAsState()
    val lanLoadError by viewModel.lanLoadError.collectAsState()
    var deleteTarget by remember { mutableStateOf<LibraryListItem?>(null) }
    var playingLocal by remember { mutableStateOf<VideoFile?>(null) }
    var playingRemote by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    var playingLan by remember { mutableStateOf<LibraryListItem.LanVideo?>(null) }
    var publishingLan by remember { mutableStateOf<LibraryListItem.LanVideo?>(null) }
    var editingFile by remember { mutableStateOf<VideoFile?>(null) }
    var editingRemoteVideo by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    var editingLan by remember { mutableStateOf<LibraryListItem.LanVideo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    CreatorHomeTheme {
        LibraryStudioContent(
            items = items,
            filter = filter,
            canUseCloudStorage = canUseCloudStorage,
            canSeeLanLibrary = canSeeLanLibrary,
            nextUploads = nextUploads,
            lanLoadError = lanLoadError,
            isRefreshing = isRefreshing,
            username = username,
            onProfileClick = onProfileClick,
            onRefresh = {
                if (canUseCloudStorage) viewModel.refreshRemote()
                viewModel.refreshNextUploads()
            },
            onImportClick = onImportClick,
            thumbnailUrl = { item ->
                when (item) {
                    is LibraryListItem.Local -> item.file.thumbnailPath
                    is LibraryListItem.Remote -> viewModel.thumbnailUrl(item.video)
                    is LibraryListItem.LanVideo -> viewModel.lanThumbnailUrl(item)
                }
            },
            onFilterChange = viewModel::setFilter,
            onOpenDetails = { item ->
                when (item) {
                    is LibraryListItem.Local -> editingFile = item.file
                    is LibraryListItem.Remote -> editingRemoteVideo = item.video
                    is LibraryListItem.LanVideo -> editingLan = item
                }
            },
            onPlay = { item ->
                when (item) {
                    is LibraryListItem.Local -> playingLocal = item.file
                    is LibraryListItem.Remote -> playingRemote = item.video
                    is LibraryListItem.LanVideo -> playingLan = item
                }
            },
            onEdit = { item ->
                when (item) {
                    is LibraryListItem.Local -> editingFile = item.file
                    is LibraryListItem.Remote -> editingRemoteVideo = item.video
                    is LibraryListItem.LanVideo -> editingLan = item
                }
            },
            deleteTarget = deleteTarget,
            onDeleteRequested = { deleteTarget = it },
            onDeleteConfirmed = { target ->
                viewModel.delete(target)
                deleteTarget = null
            },
            onDeleteDismissed = { deleteTarget = null },
            snackbarHostState = snackbarHostState,
            modifier = modifier,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryStudioContent(
    items: List<LibraryListItem>,
    filter: LibraryFilter,
    canUseCloudStorage: Boolean,
    canSeeLanLibrary: Boolean,
    nextUploads: Map<Platform, String>,
    lanLoadError: String?,
    isRefreshing: Boolean,
    username: String,
    onProfileClick: () -> Unit,
    onRefresh: () -> Unit,
    onImportClick: () -> Unit,
    thumbnailUrl: (LibraryListItem) -> String?,
    onFilterChange: (LibraryFilter) -> Unit,
    onOpenDetails: (LibraryListItem) -> Unit,
    onPlay: (LibraryListItem) -> Unit,
    onEdit: (LibraryListItem) -> Unit,
    deleteTarget: LibraryListItem?,
    onDeleteRequested: (LibraryListItem) -> Unit,
    onDeleteConfirmed: (LibraryListItem) -> Unit,
    onDeleteDismissed: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        Box(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            CreatorStudioBackdrop(Modifier.fillMaxSize())
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = CreatorVideoStyle.screenHorizontalPadding,
                    end = CreatorVideoStyle.screenHorizontalPadding,
                    bottom = FloatingNavClearance,
                ),
            ) {
                item(key = "videos-title") {
                    Text(
                        "Videos",
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 116.dp, top = 25.dp, bottom = 18.dp),
                        fontFamily = CreatorSerif,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item(key = "videos-toolbar") {
                    LibraryStudioToolbar(
                        filter = filter,
                        canUseCloudStorage = canUseCloudStorage,
                        canSeeLanLibrary = canSeeLanLibrary,
                        onFilterChange = onFilterChange,
                        onImportClick = onImportClick,
                    )
                }
                if (!lanLoadError.isNullOrBlank() && (filter == LibraryFilter.ALL || filter == LibraryFilter.LAN)) {
                    item(key = "lan-library-error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                        ) {
                            Text(
                                "PC: $lanLoadError",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                if (items.isEmpty()) {
                    item(key = "library-empty") {
                        if (!(filter == LibraryFilter.LAN && !lanLoadError.isNullOrBlank())) {
                            LibraryStudioEmptyState(filter = filter, onImportClick = onImportClick)
                        }
                    }
                } else {
                    item(key = "library-list-top-spacing") { Spacer(Modifier.fillMaxWidth().height(16.dp)) }
                    itemsIndexed(
                        items = items,
                        key = { _, item ->
                            when (item) {
                                is LibraryListItem.Local -> "local_${item.file.id}"
                                is LibraryListItem.Remote -> "remote_${item.video._id}"
                                is LibraryListItem.LanVideo -> "lan_${item.video._id}"
                            }
                        },
                    ) { index, item ->
                        LibraryStudioRow(
                            item = item,
                            nextUploads = nextUploads,
                            thumbnailUrl = thumbnailUrl(item),
                            isFirst = index == 0,
                            isLast = index == items.lastIndex,
                            onOpenDetails = { onOpenDetails(item) },
                            onPlay = { onPlay(item) },
                            onEdit = { onEdit(item) },
                            onDelete = { onDeleteRequested(item) },
                        )
                    }
                }
            }
            LibraryStudioFloatingActions(
                username = username,
                onRefresh = onRefresh,
                onProfileClick = onProfileClick,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp),
            )
            CreatorLoadingBar(
                isLoading = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth(),
            )
        }
    }
    deleteTarget?.let { target ->
        DeleteItemDialog(
            item = target,
            onConfirm = { onDeleteConfirmed(target) },
            onDismiss = onDeleteDismissed,
        )
    }
}

@Composable
private fun LibraryStudioToolbar(
    filter: LibraryFilter,
    canUseCloudStorage: Boolean,
    canSeeLanLibrary: Boolean,
    onFilterChange: (LibraryFilter) -> Unit,
    onImportClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canUseCloudStorage || canSeeLanLibrary) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryStudioFilter(LibraryFilter.ALL, filter, onFilterChange)
                LibraryStudioFilter(LibraryFilter.LOCAL, filter, onFilterChange)
                AnimatedVisibility(visible = canUseCloudStorage) {
                    LibraryStudioFilter(LibraryFilter.REMOTE, filter, onFilterChange)
                }
                AnimatedVisibility(visible = canSeeLanLibrary) {
                    LibraryStudioFilter(LibraryFilter.LAN, filter, onFilterChange)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Surface(
            onClick = onImportClick,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Importar video" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun LibraryStudioFilter(
    entry: LibraryFilter,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    val selected = entry == filter
    Surface(
        onClick = { onFilterChange(entry) },
        modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = selected; role = Role.Tab },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(libraryStudioFilterLabel(entry), fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}

private fun libraryStudioFilterLabel(filter: LibraryFilter): String = when (filter) {
    LibraryFilter.ALL -> "Todos"
    LibraryFilter.LOCAL -> "Local"
    LibraryFilter.REMOTE -> "Nube"
    LibraryFilter.LAN -> "PC"
}

@Composable
private fun LibraryStudioFloatingActions(
    username: String,
    onRefresh: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onRefresh,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Actualizar Videos" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp)) } }
        Surface(
            onClick = onProfileClick,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Abrir perfil de ${username.ifBlank { "Perfil" }}" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            Box(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(username.trim().firstOrNull()?.uppercase() ?: "E", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LibraryStudioEmptyState(
    filter: LibraryFilter,
    onImportClick: () -> Unit,
) {
    val source = when (filter) {
        LibraryFilter.ALL -> "tu biblioteca"
        LibraryFilter.LOCAL -> "este dispositivo"
        LibraryFilter.REMOTE -> "Nube"
        LibraryFilter.LAN -> "tu PC"
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("No hay videos disponibles", fontFamily = CreatorSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("No se muestran videos de $source.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (filter == LibraryFilter.ALL || filter == LibraryFilter.LOCAL) {
            Surface(
                onClick = onImportClick,
                modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Text("Importar video", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryStudioRow(
    item: LibraryListItem,
    nextUploads: Map<Platform, String>,
    thumbnailUrl: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenDetails: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(item) { mutableStateOf(false) }
    val (published, discarded) = when (item) {
        is LibraryListItem.Local -> item.file.platforms to item.file.platformsDiscarded.map { it.apiValue }
        is LibraryListItem.Remote -> item.video.platforms.mapNotNull(Platform::fromApiValue) to item.video.platformsDiscarded
        is LibraryListItem.LanVideo -> item.video.platforms.mapNotNull(Platform::fromApiValue) to item.video.platforms_discarded
    }
    val nextFor = Platform.publishable.filter { nextUploads[it] == item.displayName }
    val visiblePlatforms = Platform.publishable +
        if (Platform.FACEBOOK in published) listOf(Platform.FACEBOOK) else emptyList()
    val durationLabel = formatLibraryDuration(
        when (item) {
            is LibraryListItem.Local -> item.file.duracionSegundos
            is LibraryListItem.Remote -> item.video.durationSeconds
            is LibraryListItem.LanVideo -> item.video.duracion_segundos
        },
    )
    val rowShape = RoundedCornerShape(
        topStart = if (isFirst) 18.dp else 0.dp,
        topEnd = if (isFirst) 18.dp else 0.dp,
        bottomStart = if (isLast) 18.dp else 0.dp,
        bottomEnd = if (isLast) 18.dp else 0.dp,
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(rowShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 88.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                thickness = 0.7.dp,
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenDetails, role = Role.Button)
                .semantics {
                    contentDescription = buildString {
                        append("Detalles de ${item.displayName}")
                        durationLabel?.let { append(", duración $it") }
                    }
                }
                .padding(start = CreatorVideoStyle.cardStartPadding, end = CreatorVideoStyle.cardEndPadding, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryStudioThumbnail(item, thumbnailUrl, durationLabel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.displayName, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        visiblePlatforms.forEach { platform ->
                            val badgeState = when {
                                platform in published -> PlatformBadgeState.PUBLISHED
                                platform.apiValue in discarded -> PlatformBadgeState.DISCARDED
                                else -> PlatformBadgeState.PENDING
                            }
                            LibraryStudioPlatformBadge(platform, badgeState)
                        }
                    }
                    nextFor.forEach { platform ->
                        Text("Próximo · ${platformShortLabel(platform)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = platformColor(platform))
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Más opciones de ${item.displayName}" }) {
                    Icon(Icons.Outlined.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Reproducir") },
                        leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                        onClick = { menuExpanded = false; onPlay() },
                    )
                    DropdownMenuItem(
                        text = { Text("Editar plataformas") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    if (item !is LibraryListItem.LanVideo) {
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryStudioThumbnail(item: LibraryListItem, thumbnailUrl: String?, durationLabel: String?) {
    val durationFontSize = if (durationLabel == null) null else libraryDurationOverlayFontSize(durationLabel)
    Box(
        Modifier.size(CreatorVideoStyle.listThumbnailSize).clip(CreatorVideoStyle.listThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val model: Any? = when (item) {
            is LibraryListItem.Local -> item.file.thumbnailPath?.let(::File)?.takeIf(File::exists)
            else -> thumbnailUrl
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = rememberVectorPainter(
                    when (item) {
                        is LibraryListItem.Local -> Icons.Outlined.VideoLibrary
                        is LibraryListItem.Remote -> Icons.Outlined.CloudQueue
                        is LibraryListItem.LanVideo -> Icons.Outlined.Computer
                    },
                ),
            )
        } else {
            Icon(
                when (item) {
                    is LibraryListItem.Local -> Icons.Outlined.VideoLibrary
                    is LibraryListItem.Remote -> Icons.Outlined.CloudQueue
                    is LibraryListItem.LanVideo -> Icons.Outlined.Computer
                },
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        durationLabel?.let { duration ->
            Box(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 3.dp, bottom = 3.dp)
                    .widthIn(max = 56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.76f))
                    .clearAndSetSemantics { },
            ) {
                Text(
                    text = duration,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = libraryDurationOverlayTextStyle(durationFontSize ?: 10.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun formatLibraryDuration(value: Number?): String? {
    val secondsValue = value?.toDouble() ?: return null
    if (!secondsValue.isFinite() || secondsValue < 0.0 || secondsValue > Int.MAX_VALUE.toDouble()) return null

    val totalSeconds = secondsValue.toLong()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${(totalSeconds / 60)}:${seconds.toString().padStart(2, '0')}"
    }
}

@Composable
private fun libraryDurationOverlayFontSize(duration: String): TextUnit {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxWidthPx = with(density) { 48.dp.toPx().roundToInt() }
    return remember(duration, density.density, density.fontScale, textMeasurer) {
        var candidate = 10f
        var fits = false
        while (candidate >= 1f) {
            val layout = textMeasurer.measure(
                text = duration,
                style = libraryDurationOverlayTextStyle(candidate.sp),
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = maxWidthPx),
            )
            if (!layout.didOverflowWidth) {
                fits = true
                break
            }
            if (candidate == 1f) break
            candidate = (candidate - 0.5f).coerceAtLeast(1f)
        }
        if (fits) candidate.sp else 1.sp
    }
}

private fun libraryDurationOverlayTextStyle(fontSize: TextUnit): TextStyle = TextStyle(
    color = Color.White,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = fontSize,
    lineHeight = fontSize * 1.2f,
    letterSpacing = 0.sp,
)

@Composable
internal fun LibraryStudioPlatformBadge(platform: Platform, state: PlatformBadgeState) {
    val stateLabel = when (state) {
        PlatformBadgeState.PUBLISHED -> "publicado"
        PlatformBadgeState.DISCARDED -> "descartado"
        PlatformBadgeState.PENDING -> "pendiente"
    }
    val icon = libraryStudioPlatformIcon(platform)
    val tint = if (state == PlatformBadgeState.PUBLISHED) {
        libraryStudioPlatformColor(platform)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        Modifier.size(24.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${platform.displayName}: $stateLabel" }
            .then(
                if (state == PlatformBadgeState.DISCARDED) {
                    Modifier.drawWithContent {
                        drawContent()
                        val start = Offset(size.width * 0.25f, size.height * 0.75f)
                        val end = Offset(size.width * 0.75f, size.height * 0.25f)
                        drawLine(
                            color = surfaceColor,
                            start = start,
                            end = end,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = tint,
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = tint)
    }
}

private fun libraryStudioPlatformIcon(platform: Platform): ImageVector? = when (platform) {
    Platform.FACEBOOK -> PlatformIcons.FacebookLogo
    else -> platformIcon(platform)
}

private fun libraryStudioPlatformColor(platform: Platform): Color =
    if (platform == Platform.FACEBOOK) Color(0xFF1877F2) else platformColor(platform)

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
