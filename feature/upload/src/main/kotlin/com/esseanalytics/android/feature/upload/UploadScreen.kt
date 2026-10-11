package com.esseanalytics.android.feature.upload

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.Data
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.SwissAccentCard
import com.esseanalytics.android.core.designsystem.component.SwissButton
import com.esseanalytics.android.core.designsystem.component.SwissCard
import com.esseanalytics.android.core.designsystem.component.SwissLabel
import com.esseanalytics.android.core.designsystem.component.SwissScreenHeader
import com.esseanalytics.android.core.designsystem.component.SwissSectionTitle
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.Swiss
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.io.File

// REDISEÑO BENTO: Subir como retícula de tarjetas en dos pasos — PASO 1
// "elegí el video" con cada fuente en tarjetas, PASO 2 el formulario como
// tarjetas: detalles, portada, plataformas y el botón publicar expresivo.
// Toda la funcionalidad intacta: auto-selección del pendiente, lote activo
// con progreso y cancelación, reintento de fallidas, confirmación humana de
// blocked, crosspost a Facebook, picker de portada, diálogos de éxito y de
// salida.
@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
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
    val files by viewModel.files.collectAsState()
    val remoteVideos by viewModel.remoteVideos.collectAsState()
    val remoteVideosLoading by viewModel.remoteVideosLoading.collectAsState()
    val remoteVideosError by viewModel.remoteVideosError.collectAsState()
    val importingRemoteId by viewModel.importingRemoteId.collectAsState()
    val remoteImportError by viewModel.remoteImportError.collectAsState()
    val nextUploads by viewModel.nextUploads.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
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
    LaunchedEffect(Unit) { viewModel.refreshNextUploads() }
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
    var browsingList by remember { mutableStateOf(false) }

    // Entrar a Subir abre el formulario DE UNA, no una lista para elegir
    // primero: con fileId puntual (viene de Biblioteca) usa ese archivo: si
    // no, el pendiente más nuevo (findAll ya ordena por fecha desc), que es
    // el caso común de "el próximo que falta subir". "Elegir otro video"
    // dentro del formulario es la única forma de volver a la lista completa.
    // pendingBatchFileId manda por encima de todo -- si quedó un lote sin
    // resolver de una corrida anterior (proceso muerto a mitad de publicar,
    // ver PendingBatchStore), mostrar ESE archivo es más útil que el
    // pendiente más nuevo cualquiera.
    LaunchedEffect(initialFileId, pendingBatchFileId, files) {
        if (selectedFile == null && selectedLan == null && !browsingList) {
            selectedFile = when {
                pendingBatchFileId != null -> files.find { it.id == pendingBatchFileId }
                initialFileId != null -> files.find { it.id == initialFileId }
                else -> files.firstOrNull()
            }
        }
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
    if (files.isEmpty() && !canUseCloudStorage && !canSeeLanLibrary) {
        PlaceholderScreen(
            title = "Nada para subir todavía",
            note = "Importá un video primero desde la pestaña Videos.",
            icon = Icons.Outlined.CloudUpload,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        val currentLan = selectedLan
        val current = selectedFile
        if (currentLan != null) {
            lanPublishContent(currentLan.first, currentLan.second) {
                selectedLan = null
                browsingList = true
            }
        } else if (current == null) {
            // PASO 1 -- elegir el video. Cada fuente (Nube, PC local,
            // archivos del teléfono) es una sección de tarjetas.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Swiss.contentHorizontal,
                    top = 24.dp,
                    end = Swiss.contentHorizontal,
                    bottom = 16.dp + 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Swiss.sectionSpacing),
            ) {
                item(key = "pickerHeader") {
                    SwissScreenHeader(
                        title = "Subir",
                        subtitle = "Paso 1 — Elegí qué video querés publicar.",
                    )
                }
                // Antes un video de Nube sin bytes (storage dinámico ya los
                // liberó) fallaba en silencio -- el spinner se apagaba y no
                // pasaba nada visible. Ahora se muestra el motivo real.
                remoteImportError?.let { message ->
                    item(key = "remoteImportError") {
                        SwissCard {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { viewModel.clearRemoteImportError() },
                            )
                        }
                    }
                }
                // Además de lo local, deja elegir un video de Nube como fuente --
                // se baja y de ahí en más se publica igual que cualquier archivo
                // local (mismo criterio que VideoPickerView en iOS). Antes esta
                // pantalla solo consultaba el catálogo local, la cola remota no
                // aparecía acá aunque sí existiera en la pestaña Nube.
                if (canUseCloudStorage) {
                    item(key = "remotePanel") {
                        SourceSection(title = "Nube") {
                            when {
                                remoteVideosLoading -> SourceStatusRow("Cargando videos…", loading = true)
                                remoteVideosError != null -> SourceStatusRow(
                                    message = remoteVideosError ?: "No se pudo cargar la nube.",
                                    onRetry = viewModel::refreshRemoteVideos,
                                )
                                remoteVideos.isEmpty() -> SourceStatusRow("No hay videos pendientes en la nube.")
                                else -> RemoteVideoStrip(
                                    videos = remoteVideos,
                                    importingId = importingRemoteId,
                                    nextUploads = nextUploads,
                                    thumbnailUrl = viewModel::thumbnailUrl,
                                    onSelect = { video ->
                                        viewModel.importFromRemote(video) { file ->
                                            if (file != null) {
                                                selectedFile = file
                                                browsingList = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                // FIX 2026-08-18: mismo gap que BUG-2026-08-16-03 (ver
                // docs/bug-reports.md en content-automation-dashboard) -- antes
                // este picker no tenía forma de cambiar a un video de la PC en
                // Biblioteca LAN. Solo aparece con PC autorizada ahora mismo
                // (canSeeLanLibrary), sin fallback a ningún mirror.
                if (canSeeLanLibrary) {
                    item(key = "lanPanel") {
                        val currentLanBaseUrl = lanBaseUrl
                        SourceSection(title = "PC local") {
                            when {
                                lanLoadError != null -> SourceStatusRow(
                                    message = lanLoadError ?: "No se pudo cargar la biblioteca de la PC.",
                                    onRetry = viewModel::refreshLanLibrary,
                                )
                                currentLanBaseUrl == null -> SourceStatusRow("Conectando con la PC…", loading = true)
                                lanVideos.isEmpty() -> SourceStatusRow("No hay videos pendientes en la PC.")
                                else -> LanVideoStrip(
                                    videos = lanVideos,
                                    nextUploads = nextUploads,
                                    thumbnailUrl = { video -> viewModel.lanThumbnailUrl(video, currentLanBaseUrl) },
                                    onSelect = { video ->
                                        selectedFile = null
                                        selectedLan = video to currentLanBaseUrl
                                        browsingList = false
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "localPanel") {
                    SourceSection(title = "En este teléfono") {
                        FileList(
                            files = files,
                            nextUploads = nextUploads,
                            onSelect = {
                                selectedFile = it
                                selectedLan = null
                                browsingList = false
                            },
                        )
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
                onBackToList = {
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
            SwissButton(text = "Listo", onClick = onDismiss)
        },
    )
}

// Sección de fuente del PASO 1 — título expresivo + contenido en tarjetas.
@Composable
private fun SourceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
        SwissSectionTitle(index = "01", title = title)
        content()
    }
}

@Composable
private fun SourceStatusRow(
    message: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    SwissCard {
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

// Tira horizontal aparte (no mezclada en la lista de locales) -- son
// fuentes distintas (uno ya está en el teléfono, el otro hay que bajarlo
// primero), mezclarlas en una sola lista larga sería confuso sobre cuál
// acción va a disparar tocar la tarjeta.
@Composable
private fun RemoteVideoStrip(
    videos: List<RemoteLibraryVideoDto>,
    importingId: String?,
    nextUploads: Map<Platform, String>,
    thumbnailUrl: (RemoteLibraryVideoDto) -> String?,
    onSelect: (RemoteLibraryVideoDto) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Swiss.gap)) {
        items(videos, key = { it._id }) { video ->
            val isImporting = importingId == video._id
            SwissCard(
                modifier = Modifier.width(150.dp),
                onClick = { if (importingId == null) onSelect(video) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(Swiss.cardSmall))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val url = thumbnailUrl(video)
                    when {
                        isImporting -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        url != null -> AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    video.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val nextFor = Platform.publishable.filter { nextUploads[it] == video.fileName }
                if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
            }
        }
    }
}

// Tira horizontal para Biblioteca LAN, mismo patrón visual que RemoteVideoStrip
// -- fuente distinta (server-side, sin bytes que bajar al teléfono), por eso
// onSelect acá NO dispara ningún import: solo avisa al caller (ver
// onSelectLan en UploadScreen) qué video+baseUrl se tocó.
@Composable
private fun LanVideoStrip(
    videos: List<LocalPcVideoDto>,
    nextUploads: Map<Platform, String>,
    thumbnailUrl: (LocalPcVideoDto) -> String?,
    onSelect: (LocalPcVideoDto) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Swiss.gap)) {
        items(videos, key = { it._id }) { video ->
            SwissCard(
                modifier = Modifier.width(150.dp),
                onClick = { onSelect(video) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(Swiss.cardSmall))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val url = thumbnailUrl(video)
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    video.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val nextFor = Platform.publishable.filter { nextUploads[it] == video.fileName }
                if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
            }
        }
    }
}

// Tarjetas del PASO 1: miniatura, nombre, sello de qué plataformas faltan
// y flecha de affordance.
@Composable
private fun FileList(
    files: List<VideoFile>,
    nextUploads: Map<Platform, String>,
    onSelect: (VideoFile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
        files.forEach { file ->
            SwissCard(onClick = { onSelect(file) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UploadThumbnail(file.thumbnailPath)
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            file.fileName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Faltan: ${pendingPlatformsLabel(file)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val nextFor = Platform.publishable.filter { nextUploads[it] == file.fileName }
                        if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
                    }
                    Icon(
                        Icons.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

// Mismo badge que LibraryScreen.kt (feature:library) -- duplicado a
// propósito, mismo criterio que platformColor/platformIcon acá abajo: cada
// feature module ya repite estos helpers en vez de forzar una dependencia
// cruzada solo para esto.
@Composable
private fun NextUploadBadgeRow(platforms: List<Platform>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        platforms.forEach { platform ->
            Text(
                "Próximo · ${platformShortLabel(platform)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = platformColor(platform),
            )
        }
    }
}

private fun platformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}

@Composable
private fun UploadThumbnail(thumbnailPath: String?) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 48.dp)
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

private fun pendingPlatformsLabel(file: VideoFile): String =
    Platform.publishable
        .filter { it !in file.platforms && it !in file.platformsDiscarded }
        .joinToString(", ") { it.apiValue }

// ── PASO 2: formulario de publicación ─────────────────────────────────────

@Composable
private fun PublishForm(
    file: VideoFile,
    viewModel: UploadViewModel,
    activeBatch: PublishBatchState?,
    onBackToList: () -> Unit,
) {
    var title by remember(file.id) { mutableStateOf(file.fileName.substringBeforeLast('.')) }
    var description by remember(file.id) { mutableStateOf("") }
    var selectedPlatforms by remember(file.id) {
        mutableStateOf(
            Platform.publishable.filter { it !in file.platforms && it !in file.platformsDiscarded }.toSet(),
        )
    }
    var thumbnailOffsetSec by remember(file.id) { mutableFloatStateOf(0f) }
    var crossPostFacebook by remember(file.id) { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // bottom extra: FloatingBottomNavigation (EsseAnalyticsNavHost) es
            // un overlay real, no un bottomBar de Scaffold -- no reserva
            // espacio propio, así que sin este margen extra el botón
            // "Publicar" (última fila) queda scrolleable hasta quedar tapado
            // detrás de la cápsula flotante.
            .padding(
                start = Swiss.contentHorizontal,
                top = 24.dp,
                end = Swiss.contentHorizontal,
                bottom = 16.dp + 80.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(Swiss.sectionSpacing),
    ) {
        // Cabecera del PASO 2: el archivo como titular en tarjeta de acento.
        SwissAccentCard {
            SwissLabel("Paso 2 — Publicar", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Text(
                file.fileName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            SwissButton(
                text = "Elegir otro video",
                onClick = { if (isPublishing) showExitConfirmation = true else onBackToList() },
                primary = false,
            )
        }

        AnimatedVisibility(visible = isPublishing) {
            PublishingBatchBlock(
                batch = activeBatch,
                onCancel = viewModel::cancelBatch,
            )
        }

        if (activeBatch != null && !activeBatch.isActive && activeBatch.stage != BatchStage.COMPLETED) {
            SwissCard {
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
                        )
                    },
                    onConfirmNotPublished = viewModel::confirmNotPublished,
                    onDismiss = viewModel::dismissBatch,
                )
            }
        }

        // Bloque DETALLES.
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "02", title = "Detalles")
            SwissCard {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    minLines = 2,
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
            )
        }

        // Bloque PLATAFORMAS.
        Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
            SwissSectionTitle(index = "03", title = "Plataformas", subtitle = "Tildá dónde se publica este video.")
            Platform.publishable.forEach { platform ->
                val alreadyDone = platform in file.platforms
                PlatformCard(
                    platform = platform,
                    fileId = file.id,
                    checked = platform in selectedPlatforms,
                    enabled = !alreadyDone && !isPublishing,
                    alreadyDone = alreadyDone,
                    onCheckedChange = { checked ->
                        selectedPlatforms = if (checked) selectedPlatforms + platform else selectedPlatforms - platform
                    },
                    viewModel = viewModel,
                )
                // Facebook no es una plataforma publicable aparte (no está en
                // Platform.publishable) -- es un crosspost del MISMO archivo que
                // Instagram acepta, vía la Reels Publishing API de la Página
                // vinculada (ver InstagramUploader.publishReelToFacebookPage).
                // Solo tiene sentido si Instagram va a subirse en esta tanda.
                if (platform == Platform.INSTAGRAM) {
                    FacebookCrossPostRow(
                        checked = crossPostFacebook,
                        enabled = Platform.INSTAGRAM in selectedPlatforms && !isPublishing,
                        onCheckedChange = { crossPostFacebook = it },
                    )
                }
            }
        }

        SwissButton(
            text = if (isPublishing) "Publicando…" else "Publicar",
            onClick = {
                viewModel.publish(
                    file,
                    selectedPlatforms,
                    title,
                    description,
                    thumbnailOffsetMs = (thumbnailOffsetSec * 1000).toLong(),
                    crossPostFacebook = crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms,
                )
            },
            enabled = !isPublishing && selectedPlatforms.isNotEmpty() && title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Bloque de estado persistente durante toda la publicación -- reemplaza el
// feedback chico anterior (un "Subido ✓"/progreso por fila nada más). Mismo
// vocabulario que iOS: plataforma activa, progreso, aviso de "no cierres la
// app", botón para cancelar lo que todavía no arrancó.
@Composable
private fun PublishingBatchBlock(batch: PublishBatchState?, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SwissAccentCard {
        SwissLabel("Publicando", color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
        batch?.platforms.orEmpty().forEach { state ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.platform.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
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
        SwissButton(
            text = "Cancelar publicación",
            onClick = onCancel,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
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
private fun BatchResultSummary(
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (batch.stage == BatchStage.PARTIAL_FAILURE) "Publicación parcial" else "No se pudo publicar",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.error,
        )
        batch.platforms.forEach { state ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.platform.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
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
            SwissButton(
                text = "Reintentar fallidas",
                onClick = onRetryFailed,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = onDismiss) { Text("Descartar") }
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
private fun FacebookCrossPostRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwissCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked && enabled, enabled = enabled, onCheckedChange = onCheckedChange)
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mismo logo de marca que el toggle equivalente en el dashboard
                    // (YoutubeUploadView.tsx) y iOS (InstagramOptionsFields) -- antes
                    // este toggle no tenía ningún ícono.
                    Icon(PlatformIcons.FacebookLogo, contentDescription = null, tint = FacebookBlue, modifier = Modifier.size(20.dp))
                    Text("También publicar en Facebook", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Text(
                    "Mismo video, como Reel en la Página vinculada a tu cuenta de Instagram.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

    Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
        SwissSectionTitle(index = "04", title = "Portada", subtitle = "Elegí el frame que se va a usar de miniatura.")
        SwissCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(Swiss.cardSmall))
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

            Slider(
                value = offsetSec,
                onValueChange = onOffsetChange,
                onValueChangeFinished = { committedOffsetSec = offsetSec },
                valueRange = 0f..durationSec.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    formatSecondsLabel(offsetSec),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SwissLabel("Frame elegido")
            }
        }
    }
}

private fun formatSecondsLabel(seconds: Float): String {
    val totalSec = seconds.toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
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
    onCheckedChange: (Boolean) -> Unit,
    viewModel: UploadViewModel,
) {
    val workInfoFlow = remember(fileId, platform) { viewModel.observeWork(fileId, platform) }
    val workInfo by workInfoFlow.collectAsState(initial = null)
    val color = platformColor(platform)
    val scheme = MaterialTheme.colorScheme

    SwissCard(onClick = if (enabled) ({ onCheckedChange(!checked) }) else null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = platformIcon(platform)
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = platformFullLabel(platform),
                    tint = if (checked || alreadyDone) color else scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    platformFullLabel(platform),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                PlatformStatusLabel(alreadyDone = alreadyDone, workInfo = workInfo)
                // El crosspost a Facebook viaja en el MISMO WorkInfo que Instagram
                // (ver UploadWorker) -- no es una plataforma con su propio job.
                if (platform == Platform.INSTAGRAM) {
                    workInfo?.takeIf { it.state == WorkInfo.State.SUCCEEDED }?.let { info ->
                        FacebookCrossPostStatus(info.outputData)
                    }
                }
            }
            Checkbox(
                checked = checked || alreadyDone,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(Swiss.touchMin),
            )
        }
    }
}

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> FacebookBlue
}

private fun platformFullLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YouTube"
    Platform.INSTAGRAM -> "Instagram"
    Platform.TIKTOK -> "TikTok"
    Platform.FACEBOOK -> "Facebook"
}

private fun platformIcon(platform: Platform): ImageVector? = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> PlatformIcons.FacebookLogo
}

@Composable
private fun PlatformStatusLabel(alreadyDone: Boolean, workInfo: WorkInfo?) {
    when {
        alreadyDone -> Text(
            "Ya publicado",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        workInfo?.state == WorkInfo.State.RUNNING -> {
            val progress = workInfo.progress.getFloat(UploadWorker.KEY_PROGRESS, 0f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(end = 8.dp).height(4.dp).weight(1f))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
        workInfo?.state == WorkInfo.State.ENQUEUED -> Text(
            "En cola…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        workInfo?.state == WorkInfo.State.SUCCEEDED -> Text(
            "Subido ✓",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        workInfo?.state == WorkInfo.State.FAILED -> Text(
            workInfo.outputData.getString(UploadWorker.KEY_ERROR) ?: "Falló la subida",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
    }
}

// No-fatal: si no se pidió crosspost, ninguna de las 2 claves está en el
// output y no se muestra nada acá -- si se pidió y falló, Instagram arriba
// igual dice "Subido ✓" (es verdad, se publicó bien) y esto muestra el error
// real en vez de fingir que Facebook también salió.
@Composable
private fun FacebookCrossPostStatus(outputData: Data) {
    val facebookUrl = outputData.getString(UploadWorker.KEY_FACEBOOK_URL)
    val facebookError = outputData.getString(UploadWorker.KEY_FACEBOOK_ERROR)
    when {
        facebookUrl != null -> Text(
            "Facebook: publicado ✓",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        facebookError != null -> Text(
            "Facebook: $facebookError",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
    }
}
