package com.esseanalytics.android.feature.upload

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.Data
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.SwissLabel
import com.esseanalytics.android.core.designsystem.component.CreatorLoadingBar
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import com.esseanalytics.android.core.network.util.withCanonicalPlatformState
import java.io.File

// Subir en dos pasos: selector compacto con una sola fuente visible y
// formulario de publicación con detalles, portada, plataformas y confirmación.
// Toda la funcionalidad intacta: auto-selección del pendiente, lote activo
// con progreso y cancelación, reintento de fallidas, confirmación humana de
// blocked, crosspost a Facebook, picker de portada, diálogos de éxito y de
// salida.
@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
    username: String = "",
    onProfileClick: () -> Unit = {},
    isRefreshing: Boolean = false,
    initialFileId: Long? = null,
    canUseCloudStorage: Boolean = false,
    // Se llama tras un éxito TOTAL (todas las plataformas elegidas
    // publicadas) -- EsseAnalyticsNavHost lo usa para redirigir a Inicio.
    onPublishedAllSuccess: () -> Unit = {},
    // FIX 2026-08-18 (ver UIEssePanel/PLAN_LAN_PICKER_Y_REPRODUCTOR-2026-08-18.md):
    // un video de Biblioteca LAN NUNCA se convierte en VideoFile/selectedFile
    // -- no hay bytes que copiar al teléfono (feature:upload no puede
    // depender de feature:library para reusar su sheet/ViewModel directo, esa
    // dependencia cruzada está prohibida). El caller (EsseAnalyticsNavHost,
    // que sí puede ver los dos feature modules) decide qué hacer con el tap.
    lanPublishContent: @Composable (LocalPcVideoDto, String, () -> Unit) -> Unit = { _, _, _ -> },
    viewModel: UploadViewModel = hiltViewModel(),
) {
    CreatorHomeTheme {
        Box(modifier.fillMaxSize()) {
            CreatorStudioBackdrop()
            UploadScreenContent(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
                username = username,
                onProfileClick = onProfileClick,
                initialFileId = initialFileId,
                canUseCloudStorage = canUseCloudStorage,
                onPublishedAllSuccess = onPublishedAllSuccess,
                lanPublishContent = lanPublishContent,
                viewModel = viewModel,
            )
            CreatorLoadingBar(isRefreshing, Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding())
        }
    }
}

@Composable
private fun UploadScreenContent(
    modifier: Modifier,
    username: String,
    onProfileClick: () -> Unit,
    initialFileId: Long?,
    canUseCloudStorage: Boolean,
    onPublishedAllSuccess: () -> Unit,
    lanPublishContent: @Composable (LocalPcVideoDto, String, () -> Unit) -> Unit,
    viewModel: UploadViewModel,
) {
    val files by viewModel.files.collectAsState()
    val remoteVideos by viewModel.remoteVideos.collectAsState()
    val remoteVideosLoading by viewModel.remoteVideosLoading.collectAsState()
    val remoteVideosError by viewModel.remoteVideosError.collectAsState()
    val importingRemoteId by viewModel.importingRemoteId.collectAsState()
    val remoteImportError by viewModel.remoteImportError.collectAsState()
    val isGalleryImporting by viewModel.isGalleryImporting.collectAsState()
    val isVideoImporting by viewModel.isVideoImporting.collectAsState()
    val galleryImportError by viewModel.galleryImportError.collectAsState()
    val galleryImportSelection by viewModel.galleryImportSelection.collectAsState()
    val nextUploads by viewModel.nextUploads.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val canonicalPlatformStates by viewModel.canonicalPlatformStates.collectAsState()
    val refreshingPlatformStates by viewModel.refreshingPlatformStates.collectAsState()
    val platformStateRefreshError by viewModel.platformStateRefreshError.collectAsState()
    val pendingBatchFileId by viewModel.pendingBatchFileId.collectAsState()
    val canSeeLanLibrary by viewModel.canSeeLanLibrary.collectAsState()
    val lanVideos by viewModel.lanVideos.collectAsState()
    val lanBaseUrl by viewModel.lanBaseUrl.collectAsState()
    val lanLoadError by viewModel.lanLoadError.collectAsState()
    var selectedFile by remember { mutableStateOf<VideoFile?>(null) }
    var selectedLan by remember { mutableStateOf<Pair<LocalPcVideoDto, String>?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(canUseCloudStorage) {
        if (canUseCloudStorage) viewModel.refreshRemoteVideos()
    }
    LaunchedEffect(Unit) {
        viewModel.refreshNextUploads()
        viewModel.refreshPlatformStates()
    }
    // Mismo patrón ref-counted que LibraryScreen.kt (feature:library) --
    // LanLibraryRepository soporta los dos consumidores activos a la vez sin
    // pisarse. Ver ese archivo.
    DisposableEffect(Unit) {
        viewModel.startLanDiscovery()
        onDispose { viewModel.stopLanDiscovery() }
    }
    // Distingue "todavía no autoseleccionó nada" de "el usuario tocó Elegir
    // otro video a propósito" -- sin esto, un simple cambio en la lista (se
    // importa/publica/borra algo mientras el usuario está mirando la lista a
    // mano) dispara el LaunchedEffect de abajo de nuevo y lo saca de la
    // lista empujándolo de vuelta al formulario sin que él lo pidiera.
    var browsingList by rememberSaveable { mutableStateOf(false) }
    var galleryPickerOpen by rememberSaveable { mutableStateOf(false) }
    var sourceRequestId by rememberSaveable { mutableStateOf(0L) }
    val isVideoImportInProgress = isVideoImporting || isGalleryImporting || importingRemoteId != null || galleryImportSelection != null
    var selectedPickerSource by rememberSaveable { mutableStateOf<String?>(null) }
    val pickerSources = buildList {
        if (canUseCloudStorage) add(UploadPickerSource.CLOUD)
        if (canSeeLanLibrary) add(UploadPickerSource.PC)
        add(UploadPickerSource.IMPORTED)
    }
    val pickerSource = pickerSources.firstOrNull { it.name == selectedPickerSource } ?: pickerSources.first()
    val pickerListState = rememberLazyListState()
    LaunchedEffect(pickerSource) { pickerListState.scrollToItem(0) }
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        galleryPickerOpen = false
        if (uri != null) {
            viewModel.importFromGallery(uri)
        }
    }
    val openGallery = {
        if (!galleryPickerOpen && !isVideoImportInProgress) {
            browsingList = true
            sourceRequestId += 1L
            galleryPickerOpen = true
            viewModel.clearGalleryImportError()
            galleryPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        }
    }

    // Entrar a Subir abre el formulario DE UNA, no una lista para elegir
    // primero: con fileId puntual (viene de Biblioteca) usa ese archivo: si
    // no, el pendiente más nuevo (findAll ya ordena por fecha desc), que es
    // el caso común de "el próximo que falta subir". "Elegir otro video"
    // dentro del formulario es la única forma de volver a la lista completa.
    // pendingBatchFileId manda por encima de todo -- si quedó un lote sin
    // resolver de una corrida anterior (proceso muerto a mitad de publicar,
    // ver PendingBatchStore), mostrar ESE archivo es más útil que el
    // pendiente más nuevo cualquiera.
    LaunchedEffect(initialFileId, pendingBatchFileId, files, isVideoImportInProgress, galleryImportSelection, galleryImportError) {
        if (selectedFile == null && selectedLan == null && !browsingList && !isVideoImportInProgress && galleryImportSelection == null && galleryImportError == null) {
            selectedFile = when {
                pendingBatchFileId != null -> files.find { it.id == pendingBatchFileId }
                initialFileId != null -> files.find { it.id == initialFileId }
                else -> files.firstOrNull()
            }
        }
    }

    LaunchedEffect(galleryImportSelection) {
        val selection = galleryImportSelection ?: return@LaunchedEffect
        selectedFile = selection.file
        selectedLan = null
        browsingList = false
        viewModel.acknowledgeGalleryImport(selection.requestId)
    }

    // El formulario mantiene una referencia al video elegido. Cuando un
    // worker termina, Room actualiza la lista reactiva; reemplazamos esa
    // referencia para que el formulario refleje de inmediato las plataformas
    // publicadas, incluso si otro destino del mismo lote falló.
    LaunchedEffect(files, canonicalPlatformStates, selectedFile?.id) {
        val selected = selectedFile ?: return@LaunchedEffect
        val latest = files.firstOrNull { it.id == selected.id }
            ?: selected.withCanonicalPlatformState(canonicalPlatformStates[selected.id])
        if (latest != selected) selectedFile = latest
    }

    // Éxito total -- se muestra el modal, y al cerrarlo se limpia todo
    // (soltar selectedFile para que la próxima entrada resuelva de nuevo el
    // siguiente pendiente) y se redirige a Inicio. Biblioteca se refresca
    // sola (Room es reactivo, ver FileRepository.observeAll); Dashboard lo
    // refresca EsseAnalyticsNavHost al navegar (ver popUpTo inclusive ahí).
    LaunchedEffect(activeBatch?.stage) {
        if (activeBatch?.stage == BatchStage.COMPLETED) showSuccessDialog = true
    }

    if (showSuccessDialog) {
        PublishSuccessDialog(
            batch = activeBatch,
            onDismiss = {
                showSuccessDialog = false
                viewModel.dismissBatch()
                selectedFile = null
                browsingList = false
                onPublishedAllSuccess()
            },
        )
    }

    // canSeeLanLibrary (no lanVideos.isEmpty()) evita el falso "Nada para
    // subir" mientras la PC recién se está autorizando/cargando -- mismo
    // criterio que showsChips en LibraryView.swift (iOS): el chip/sección
    // existe apenas hay una PC autorizada, sin esperar a que termine de
    // traer su lista.
    Column(modifier = modifier.fillMaxSize()) {
        val currentLan = selectedLan
        val current = selectedFile
        if (currentLan != null) {
            lanPublishContent(currentLan.first, currentLan.second) {
                selectedLan = null
                browsingList = true
            }
        } else if (current == null) {
            // Una sola fuente visible; las identidades y los callbacks de selección no cambian.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = pickerListState,
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "pickerHeader") {
                    UploadStudioHeader(username, onProfileClick, subtitle = "Elegí un video para continuar.")
                }
                item(key = "galleryAction") {
                    Column(Modifier.animateContentSize(tween(180)).padding(top = 12.dp, bottom = 16.dp)) {
                        UploadGalleryAction(
                            isLoading = isGalleryImporting,
                            error = galleryImportError,
                            enabled = !isVideoImportInProgress && !galleryPickerOpen,
                            onClick = openGallery,
                        )
                    }
                }
                item(key = "pickerSources") {
                    UploadPickerSources(
                        sources = pickerSources,
                        selected = pickerSource,
                        enabled = !isVideoImportInProgress && !galleryPickerOpen,
                        onSelect = { selectedPickerSource = it.name },
                    )
                }
                remoteImportError?.let { message ->
                    item(key = "remoteImportError") {
                        UploadStudioCard {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { viewModel.clearRemoteImportError() },
                            )
                        }
                    }
                }
                when (pickerSource) {
                    UploadPickerSource.CLOUD -> {
                        when {
                            remoteVideosLoading -> item(key = "cloudLoading") { SourceStatusRow("Cargando videos…", loading = true) }
                            remoteVideosError != null -> item(key = "cloudError") {
                                SourceStatusRow(remoteVideosError ?: "No se pudo cargar la nube.", onRetry = viewModel::refreshRemoteVideos)
                            }
                            remoteVideos.isEmpty() -> item(key = "cloudEmpty") { SourceStatusRow("No hay videos pendientes en la nube.") }
                            else -> items(remoteVideos, key = { "remote-${it._id}" }) { video ->
                                UploadCloudVideoCard(
                                    video = video,
                                    importing = importingRemoteId == video._id,
                                    enabled = !isVideoImportInProgress && !galleryPickerOpen,
                                    nextUploads = nextUploads,
                                    thumbnailUrl = viewModel.thumbnailUrl(video),
                                    modifier = Modifier.animateItem(fadeInSpec = tween(160), placementSpec = tween(200), fadeOutSpec = tween(100)),
                                    onClick = {
                                        if (!isVideoImportInProgress && !galleryPickerOpen) {
                                            sourceRequestId += 1L
                                            val requestId = sourceRequestId
                                            browsingList = true
                                            viewModel.importFromRemote(video) { file ->
                                                if (requestId == sourceRequestId && file != null) {
                                                    selectedFile = file
                                                    selectedLan = null
                                                    browsingList = false
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    UploadPickerSource.PC -> {
                        val currentLanBaseUrl = lanBaseUrl
                        when {
                            lanLoadError != null -> item(key = "lanError") {
                                SourceStatusRow(lanLoadError ?: "No se pudo cargar la biblioteca de la PC.", onRetry = viewModel::refreshLanLibrary)
                            }
                            currentLanBaseUrl == null -> item(key = "lanLoading") { SourceStatusRow("Conectando con la PC…", loading = true) }
                            lanVideos.isEmpty() -> item(key = "lanEmpty") { SourceStatusRow("No hay videos pendientes en la PC.") }
                            else -> items(lanVideos, key = { "lan-${it._id}" }) { video ->
                                UploadPickerVideoRow(
                                    modifier = Modifier.animateItem(fadeInSpec = tween(160), placementSpec = tween(200), fadeOutSpec = tween(100)),
                                    fileName = video.fileName,
                                    thumbnail = viewModel.lanThumbnailUrl(video, currentLanBaseUrl),
                                    published = video.platforms.mapNotNull(Platform::fromApiValue),
                                    discarded = video.platforms_discarded.mapNotNull(Platform::fromApiValue),
                                    nextFor = Platform.publishable.filter { nextUploads[it] == video.fileName },
                                    enabled = !isVideoImportInProgress && !galleryPickerOpen,
                                    onClick = {
                                        if (!isVideoImportInProgress && !galleryPickerOpen) {
                                            sourceRequestId += 1L
                                            selectedFile = null
                                            selectedLan = video to currentLanBaseUrl
                                            browsingList = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                    UploadPickerSource.IMPORTED -> {
                        if (refreshingPlatformStates) {
                            item(key = "platformStatesLoading") { SourceStatusRow("Actualizando estados desde la PC…", loading = true) }
                        } else if (platformStateRefreshError != null) {
                            item(key = "platformStatesError") {
                                SourceStatusRow(platformStateRefreshError ?: "No se pudieron actualizar los estados.", onRetry = viewModel::refreshPlatformStates)
                            }
                        }
                        if (files.isEmpty()) {
                            item(key = "importedEmpty") {
                                Text(
                                    "Tus videos importados aparecerán acá. Elegí uno desde la galería para empezar.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }
                        items(files, key = { "local-${it.id}" }) { file ->
                            UploadPickerVideoRow(
                                modifier = Modifier.animateItem(fadeInSpec = tween(160), placementSpec = tween(200), fadeOutSpec = tween(100)),
                                fileName = file.fileName,
                                thumbnail = file.thumbnailPath?.let(::File),
                                published = file.platforms,
                                discarded = file.platformsDiscarded,
                                nextFor = Platform.publishable.filter { nextUploads[it] == file.fileName },
                                enabled = !isVideoImportInProgress && !galleryPickerOpen,
                                onClick = {
                                    if (!isVideoImportInProgress && !galleryPickerOpen) {
                                        sourceRequestId += 1L
                                        selectedFile = file
                                        selectedLan = null
                                        browsingList = false
                                    }
                                },
                            )
                        }
                    }
                }
            }
        } else {
            // El lote activo solo aplica a ESTE archivo -- si el usuario
            // volvió a la lista y eligió otro mientras algo publicaba en
            // segundo plano (WorkManager sigue corriendo igual), la tarjeta
            // no debe aparecer sobre un video que no tiene nada en curso.
            val batchForCurrent = activeBatch?.takeIf { it.fileId == current.id }
            PublishForm(
                file = current,
                viewModel = viewModel,
                activeBatch = batchForCurrent,
                username = username,
                onProfileClick = onProfileClick,
                onBackToList = {
                    viewModel.refreshPlatformStates()
                    selectedFile = null
                    browsingList = true
                },
            )
        }
    }
}

// Modal de éxito notorio -- antes un video 100% publicado (todas las
// plataformas elegidas) solo dejaba un "Subido ✓" chico en cada fila. Este
// diálogo tapa la pantalla un instante con un check grande y los links
// reales de cada plataforma.
@Composable
private fun PublishSuccessDialog(batch: PublishBatchState?, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(40.dp)) },
        title = { Text("¡Publicado!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column {
                batch?.platforms.orEmpty().forEach { state ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.platform.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        SwissLabel("Publicado", color = scheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            UploadStudioButton(text = "Listo", onClick = onDismiss)
        },
    )
}

// Sección de fuente del PASO 1 — título expresivo + contenido en tarjetas.
@Composable
internal fun SourceSection(title: String, content: @Composable () -> Unit) {
    UploadStudioSection(title = title, content = content)
}

@Composable
internal fun SourceStatusRow(
    message: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    UploadStudioCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
    }
}

// El mismo componente compacto se usa en los fixtures del selector.
@Composable
internal fun FileList(
    files: List<VideoFile>,
    nextUploads: Map<Platform, String>,
    onSelect: (VideoFile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        files.forEach { file ->
            UploadPickerVideoRow(
                fileName = file.fileName,
                thumbnail = file.thumbnailPath?.let(::File),
                published = file.platforms,
                discarded = file.platformsDiscarded,
                nextFor = Platform.publishable.filter { nextUploads[it] == file.fileName },
                enabled = true,
                onClick = { onSelect(file) },
            )
        }
    }
}

@Composable
private fun UploadThumbnail(thumbnailPath: String?) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
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

// ── PASO 2: formulario de publicación ─────────────────────────────────────

@Composable
private fun PublishForm(
    file: VideoFile,
    viewModel: UploadViewModel,
    activeBatch: PublishBatchState?,
    username: String,
    onProfileClick: () -> Unit,
    onBackToList: () -> Unit,
) {
    var title by remember(file.id) { mutableStateOf(file.fileName.substringBeforeLast('.')) }
    var description by remember(file.id) { mutableStateOf("") }
    var selectedPlatforms by remember(file.id) {
        mutableStateOf(
            Platform.publishable.filter { it !in file.platforms && it !in file.platformsDiscarded }.toSet(),
        )
    }
    LaunchedEffect(file.id, file.platforms, file.platformsDiscarded) {
        // Un resultado de otro lote/dispositivo puede resolver este destino
        // mientras el formulario sigue abierto. No lo dejamos marcado para
        // volver a publicar; el usuario puede volver a seleccionarlo después.
        selectedPlatforms = selectedPlatforms - (file.platforms + file.platformsDiscarded).toSet()
    }
    var thumbnailOffsetSec by remember(file.id) { mutableFloatStateOf(0f) }
    var crossPostFacebook by remember(file.id) { mutableStateOf(false) }
    var publicationOptions by remember(file.id) { mutableStateOf(PlatformPublicationOptions()) }
    val tiktokCreator by viewModel.tiktokCreator.collectAsState()
    val tiktokCreatorLoading by viewModel.tiktokCreatorLoading.collectAsState()
    val tiktokCreatorError by viewModel.tiktokCreatorError.collectAsState()
    LaunchedEffect(file.id, Platform.TIKTOK in selectedPlatforms) {
        if (Platform.TIKTOK in selectedPlatforms) viewModel.loadTiktokCreator()
    }
    val optionsError = publicationOptions.validationError(selectedPlatforms, tiktokCreator, file.duracionSegundos)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var showExitConfirmation by remember { mutableStateOf(false) }
    val blockedPlatforms by viewModel.blockedPlatformsForActiveFile.collectAsState()
    val blockedConfirmationError by viewModel.blockedConfirmationError.collectAsState()
    val isPublishing = activeBatch?.isActive == true

    // Mismo criterio que iOS (interactiveDismissDisabled + confirmationDialog):
    // salir mientras hay un lote en curso pide confirmación, tanto por el
    // botón "Elegir otro video" (más abajo) como por el back del sistema.
    BackHandler(enabled = isPublishing) { showExitConfirmation = true }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text("¿Salir mientras se publica?") },
            text = { Text("La publicación sigue en curso. Si salís ahora, puede quedar interrumpida.") },
            confirmButton = {
                TextButton(onClick = { showExitConfirmation = false; onBackToList() }) {
                    Text("Salir de todos modos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Seguir acá") }
            },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = if (keyboardVisible) 104.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            UploadStudioHeader(username, onProfileClick, subtitle = "Prepara tu contenido y elige dónde publicarlo.")
            UploadStudioSelectedVideo(
                file.fileName, file.duracionSegundos,
                onChangeVideo = { if (isPublishing) showExitConfirmation = true else onBackToList() },
            ) { UploadThumbnail(file.thumbnailPath) }

            AnimatedVisibility(visible = isPublishing) {
                PublishingBatchBlock(
                    batch = activeBatch,
                    onCancel = viewModel::cancelBatch,
                )
            }

            if (activeBatch != null && !activeBatch.isActive && activeBatch.stage != BatchStage.COMPLETED) {
                UploadStudioCard {
                    BatchResultSummary(
                        batch = activeBatch,
                        blockedPlatforms = blockedPlatforms,
                        confirmationError = blockedConfirmationError,
                        onRetryFailed = {
                            viewModel.retryFailedInBatch(
                                file,
                                title,
                                description,
                                thumbnailOffsetMs = (thumbnailOffsetSec * 1000).toLong(),
                                crossPostFacebook = crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms,
                                options = publicationOptions,
                            )
                        },
                        onConfirmNotPublished = viewModel::confirmNotPublished,
                        onDismiss = viewModel::dismissBatch,
                    )
                }
            }

            // Bloque DETALLES.
            UploadStudioSection(title = "Contenido") {
                UploadStudioDetailsFields(
                    title = title,
                    onTitleChange = { title = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    enabled = !isPublishing,
                    showYoutubeDescription = Platform.YOUTUBE in selectedPlatforms,
                )
            }

            val durationSec = file.duracionSegundos
            if (durationSec != null && durationSec > 0) {
                ThumbnailPicker(
                    filePath = file.filePath,
                    thumbnailPath = file.thumbnailPath,
                    durationSec = durationSec,
                    offsetSec = thumbnailOffsetSec,
                    onOffsetChange = { thumbnailOffsetSec = it },
                    viewModel = viewModel,
                    enabled = !isPublishing,
                )
            }

            // Bloque PLATAFORMAS.
            UploadStudioSection(title = "Publicar en", subtitle = "Elige tus plataformas y revisa sus ajustes.") {
                Platform.publishable.forEach { platform ->
                    val alreadyDone = platform in file.platforms
                    val selected = platform in selectedPlatforms
                    UploadStudioCard(
                        contentPadding = 12.dp,
                        contentSpacing = 8.dp,
                        borderColor = if (selected && !alreadyDone) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        } else MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        PlatformCard(
                            platform = platform,
                            fileId = file.id,
                            checked = selected,
                            enabled = !alreadyDone && !isPublishing,
                            alreadyDone = alreadyDone,
                            discarded = platform in file.platformsDiscarded,
                            crossPostFacebook = crossPostFacebook,
                            crossPostFacebookEnabled = Platform.INSTAGRAM in selectedPlatforms && !isPublishing,
                            facebookAlreadyPublished = Platform.FACEBOOK in file.platforms,
                            onCrossPostFacebookChange = { crossPostFacebook = it },
                            onCheckedChange = { checked ->
                                selectedPlatforms = if (checked) selectedPlatforms + platform else selectedPlatforms - platform
                            },
                            viewModel = viewModel,
                            configuration = {
                                AnimatedVisibility(
                                    visible = !alreadyDone && selected,
                                    enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                                    exit = fadeOut(tween(100)) + shrinkVertically(tween(180)),
                                ) {
                                    PlatformPublicationConfiguration(
                                        platform, publicationOptions, { publicationOptions = it }, !isPublishing,
                                        tiktokCreator, tiktokCreatorLoading, tiktokCreatorError, viewModel::loadTiktokCreator,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        // This footer reserves space above the floating navigation capsule.
        if (!keyboardVisible) UploadStudioPublishFooter(
            selectedPlatforms = if (crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms) selectedPlatforms + Platform.FACEBOOK else selectedPlatforms,
            isPublishing = isPublishing,
            onPublish = {
                viewModel.publish(
                    file,
                    selectedPlatforms,
                    title,
                    description,
                    thumbnailOffsetMs = (thumbnailOffsetSec * 1000).toLong(),
                    crossPostFacebook = crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms,
                    options = publicationOptions,
                )
            },
            enabled = !isPublishing && selectedPlatforms.isNotEmpty() && title.isNotBlank() && optionsError == null,
            message = optionsError,
            confirmationKey = file.id,
            completedAttemptKey = activeBatch?.takeUnless { it.isActive }?.operationId,
        )
    }
}

// Bloque de estado persistente durante toda la publicación -- reemplaza el
// feedback chico anterior (un "Subido ✓"/progreso por fila nada más). Mismo
// vocabulario que iOS: plataforma activa, progreso, aviso de "no cierres la
// app", botón para cancelar lo que todavía no arrancó.
@Composable
internal fun PublishingBatchBlock(batch: PublishBatchState?, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    UploadStudioCard(containerColor = scheme.primaryContainer) {
        SwissLabel("Publicando", color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
        batch?.platforms.orEmpty().forEach { state ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.stage == PlatformPublishStage.FAILED) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlatformBatchIdentity(state)
                        PlatformBatchStatusLabel(state)
                    }
                } else Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformBatchIdentity(state)
                    Spacer(Modifier.weight(1f))
                    PlatformBatchStatusLabel(state)
                }
                val progress = state.progress
                if (state.stage == PlatformPublishStage.UPLOADING && progress != null) {
                    // `progress` capturado en un val local -- Float? no se
                    // promueve solo a Float dentro del lambda de
                    // LinearProgressIndicator (evaluado después, no smart-cast).
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        trackColor = scheme.onPrimaryContainer.copy(alpha = 0.25f),
                        color = scheme.primary,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = scheme.onPrimaryContainer.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Mantené la app abierta hasta que termine. Si salís o la cerrás, la subida puede interrumpirse.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        UploadStudioButton(
            text = "Cancelar publicación",
            onClick = onCancel,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlatformBatchIdentity(state: PlatformPublishState) {
    val published = state.stage == PlatformPublishStage.SUCCESS
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = platformIcon(state.platform) ?: PlatformIcons.YoutubeLogo,
            contentDescription = null,
            tint = if (published) platformColor(state.platform) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(state.platform.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlatformBatchStatusLabel(state: PlatformPublishState) {
    val scheme = MaterialTheme.colorScheme
    when (state.stage) {
        PlatformPublishStage.PENDING -> Text(
            "En espera",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        PlatformPublishStage.UPLOADING -> Text(
            "${((state.progress ?: 0f) * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = scheme.primary,
        )
        PlatformPublishStage.SUCCESS -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Publicado",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = scheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        PlatformPublishStage.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                state.error ?: "Error",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
                modifier = Modifier.padding(start = 4.dp).weight(1f),
            )
        }
        PlatformPublishStage.CANCELLED -> Text(
            "Cancelado",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

// Resumen post-lote cuando NO fue éxito total -- éxito total lo maneja
// PublishSuccessDialog (modal notorio en vez de esto). Acá se cubre falla
// total y parcial: qué salió, qué no, y reintentar solo lo que falta.
//
// Recuperación de publicación ambigua (docs/ambiguous-publish-recovery-design-
// 2026-09-22.md): una plataforma cuya fila del journal está `blocked` NO se
// reintenta con el botón normal — la plataforma pudo procesar el pedido aunque
// el teléfono no haya confirmado la respuesta, y reintentar a ciegas podría
// duplicar el video. Su única salida es la confirmación humana «Revisé la
// plataforma y el video no se publicó» (doble confirmación Material), que
// registra la evidencia y habilita UN intento nuevo limpio vía publish().
@Composable
internal fun BatchResultSummary(
    batch: PublishBatchState,
    blockedPlatforms: Set<Platform>,
    confirmationError: String?,
    onRetryFailed: () -> Unit,
    onConfirmNotPublished: (Platform) -> Unit,
    onDismiss: () -> Unit,
) {
    // El retry normal cubre solo lo fallido/cancelado que NO está blocked --
    // lo blocked queda exclusivamente para la confirmación humana. El gate
    // del journal excluye de facto al flujo LAN (la PC publica y nunca
    // escribe publish_operations).
    val hasRetriable = batch.platforms.any {
        (it.stage == PlatformPublishStage.FAILED || it.stage == PlatformPublishStage.CANCELLED) &&
            it.platform !in blockedPlatforms
    }
    // Plataforma cuya confirmación está pidiendo el usuario ahora mismo;
    // cancelar el diálogo la vuelve a null y NO toca Room (la fila queda
    // como estaba).
    var confirmTarget by remember { mutableStateOf<Platform?>(null) }
    val allCancelled = batch.platforms.isNotEmpty() && batch.platforms.all { it.stage == PlatformPublishStage.CANCELLED }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    batch.stage == BatchStage.PARTIAL_FAILURE -> "Publicación parcial"
                    allCancelled -> "Publicación cancelada"
                    else -> "No se pudo publicar"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (allCancelled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, enabled = !batch.isActive) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar aviso de publicación")
            }
        }
        batch.platforms.forEach { state ->
            Column {
                if (state.stage == PlatformPublishStage.FAILED) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlatformBatchIdentity(state)
                        PlatformBatchStatusLabel(state)
                    }
                } else Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformBatchIdentity(state)
                    Spacer(Modifier.weight(1f))
                    PlatformBatchStatusLabel(state)
                }
                // Desenlace ambiguo respaldado por una fila `blocked` del
                // journal: la salida es la confirmación humana, no el
                // retry.
                if (state.platform in blockedPlatforms) {
                    TextButton(onClick = { confirmTarget = state.platform }) {
                        Text("Revisé la plataforma y el video no se publicó")
                    }
                }
            }
        }
        if (confirmationError != null) {
            Text(
                confirmationError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (hasRetriable) {
            UploadStudioButton(
                text = if (allCancelled) "Reintentar publicación" else "Reintentar pendientes",
                onClick = onRetryFailed,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Doble confirmación Material (mismo patrón que showExitConfirmation):
    // el dismiss solo cierra — cancelar no cambia la fila. El copy es parte
    // del diseño: si el video SÍ aparece, se vincula por el flujo de Matching
    // (Sincronización → "Vincular con archivo local", o el link manual en el
    // detalle del video), jamás se re-sube.
    confirmTarget?.let { platform ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text("¿Confirmás que no se publicó?") },
            text = {
                Text(
                    "Solo reintentá si ya revisaste ${platform.displayName} y el video NO está público. " +
                        "Si el video sí aparece, no lo subas de nuevo: vinculalo desde Sincronización " +
                        "→ «Vincular con archivo local».",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    onConfirmNotPublished(platform)
                }) { Text("Sí, no se publicó") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
internal fun FacebookCrossPostRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    alreadyPublished: Boolean = false,
    presentation: FacebookCrossPostPresentation? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = checked && enabled,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ).semantics {
            contentDescription = buildString {
                append("Facebook, ")
                append(if (checked && enabled) "seleccionado" else "no seleccionado")
                if (alreadyPublished || presentation?.published == true) append(", publicado")
                presentation?.takeUnless { it.published }?.let { append(", error: ${it.message}") }
                if (!enabled) append(", deshabilitado")
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                PlatformIcons.FacebookLogo,
                contentDescription = null,
                tint = if (alreadyPublished || presentation?.published == true) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        val visibleStatus = when {
            presentation?.published == true -> "Publicado ✓"
            presentation != null -> presentation.message
            alreadyPublished -> "Ya publicado"
            else -> null
        }
        if (visibleStatus != null) {
            Text(
                visibleStatus,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (presentation?.error == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Spacer(Modifier.weight(1f))
        Checkbox(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.size(48.dp).clearAndSetSemantics {},
        )
    }
}

// Elegir portada -- mismo criterio que desktop (YoutubeUploadView.tsx,
// ThumbnailScrubber/ThumbOffsetPicker): un slider sobre la duración del
// video, con preview del frame elegido. YouTube recibe el frame capturado
// como imagen (ver YoutubeUploader.setCustomThumbnail); Instagram/TikTok
// solo el offset en ms, cada red saca el frame de su lado.
@Composable
private fun ThumbnailPicker(
    filePath: String,
    thumbnailPath: String?,
    durationSec: Int,
    offsetSec: Float,
    onOffsetChange: (Float) -> Unit,
    viewModel: UploadViewModel,
    enabled: Boolean = true,
) {
    var preview by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(filePath) { mutableStateOf(false) }
    // null = todavía no se tocó el slider -- se muestra la miniatura que ya
    // se generó al importar (barata, un archivo en disco) en vez de decodificar
    // un frame de video real (MediaMetadataRetriever.getFrameAtTime) apenas
    // se entra a la pantalla. Navigation Compose descarta y recompone cada
    // pestaña al volver a ella, así que sin este freno cada vez que se
    // visitaba Subir se pagaba el costo de esa decodificación de nuevo --
    // notable en videos grandes/storage lento, es el motivo real del lag al
    // cambiar de pestaña. Ahora solo se paga cuando el usuario de verdad
    // mueve el slider (onValueChangeFinished).
    var committedOffsetSec by remember(filePath) { mutableStateOf<Float?>(null) }

    LaunchedEffect(filePath, committedOffsetSec) {
        val committed = committedOffsetSec ?: return@LaunchedEffect
        loading = true
        preview = viewModel.captureThumbnailPreview(filePath, (committed * 1000).toLong())
        loading = false
    }

    UploadStudioCoverPicker(
        offsetSec = offsetSec,
        durationSec = durationSec,
        onOffsetChange = onOffsetChange,
        onOffsetChangeFinished = { committedOffsetSec = offsetSec },
        enabled = enabled,
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val currentPreview = preview
                when {
                    currentPreview != null -> Image(
                        bitmap = currentPreview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    thumbnailPath != null -> AsyncImage(
                        model = File(thumbnailPath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Unit
                }
                if (loading) {
                    CircularProgressIndicator()
                }
            }
    }
}

// Tarjeta de plataforma: logo, nombre, estado y checkbox grande a la
// derecha. Toda la tarjeta es clickeable (no solo el Checkbox).
@Composable
private fun PlatformCard(
    platform: Platform,
    fileId: Long,
    checked: Boolean,
    enabled: Boolean,
    alreadyDone: Boolean,
    discarded: Boolean,
    crossPostFacebook: Boolean,
    crossPostFacebookEnabled: Boolean,
    facebookAlreadyPublished: Boolean,
    onCrossPostFacebookChange: (Boolean) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    viewModel: UploadViewModel,
    configuration: @Composable () -> Unit,
) {
    val workInfoFlow = remember(fileId, platform) { viewModel.observeWork(fileId, platform) }
    val workInfo by workInfoFlow.collectAsState(initial = null)
    val observedWorkInfo = workInfo
    val color = platformColor(platform)
    val runningProgress = if (observedWorkInfo?.state == WorkInfo.State.RUNNING) {
        observedWorkInfo.progress.getFloat(UploadWorker.KEY_PROGRESS, 0f)
    } else null
    val presentation = uploadStudioPlatformPresentation(
        alreadyDone = alreadyDone,
        queued = observedWorkInfo?.state == WorkInfo.State.ENQUEUED,
        runningProgress = runningProgress,
        succeeded = observedWorkInfo?.state == WorkInfo.State.SUCCEEDED,
        failureMessage = if (observedWorkInfo?.state == WorkInfo.State.FAILED) {
            observedWorkInfo.outputData.getString(UploadWorker.KEY_ERROR) ?: "Falló la subida"
        } else null,
    )
    UploadStudioPlatformRow(
        label = platformFullLabel(platform),
        icon = platformIcon(platform),
        brandColor = color,
        checked = checked,
        enabled = enabled,
        alreadyDone = alreadyDone,
        presentation = presentation,
        discarded = discarded,
        onCheckedChange = onCheckedChange,
        extraContent = {
            configuration()
            // El crosspost a Facebook viaja en el MISMO WorkInfo que Instagram.
            if (platform == Platform.INSTAGRAM) {
                val facebookPresentation = observedWorkInfo
                    ?.takeIf { it.state == WorkInfo.State.SUCCEEDED }
                    ?.let { facebookCrossPostPresentation(it.outputData) }
                FacebookCrossPostRow(
                    checked = crossPostFacebook,
                    enabled = crossPostFacebookEnabled,
                    onCheckedChange = onCrossPostFacebookChange,
                    alreadyPublished = facebookAlreadyPublished,
                    presentation = facebookPresentation,
                )
            }
        },
    )
}

internal fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> FacebookBlue
}

internal fun platformFullLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YouTube"
    Platform.INSTAGRAM -> "Instagram"
    Platform.TIKTOK -> "TikTok"
    Platform.FACEBOOK -> "Facebook"
}

internal fun platformIcon(platform: Platform): ImageVector? = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> PlatformIcons.FacebookLogo
}

internal data class FacebookCrossPostPresentation(
    val published: Boolean,
    val message: String,
    val error: Boolean = false,
)

// No se infiere el resultado de Facebook desde el éxito de Instagram; solo se
// devuelve feedback cuando WorkManager trae una URL o un error de Facebook.
internal fun facebookCrossPostPresentation(outputData: Data): FacebookCrossPostPresentation? {
    val facebookUrl = outputData.getString(UploadWorker.KEY_FACEBOOK_URL)
    val facebookError = outputData.getString(UploadWorker.KEY_FACEBOOK_ERROR)
    return when {
        facebookUrl != null -> FacebookCrossPostPresentation(published = true, message = "Publicado ✓")
        facebookError != null -> FacebookCrossPostPresentation(published = false, message = facebookError, error = true)
        else -> null
    }
}
