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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.io.File

// Elegir un video ya importado, tildar a qué plataformas publicarlo,
// completar título/descripción/portada, y encolar un UploadWorker por
// plataforma. initialFileId llega desde Biblioteca (ver EsseAnalyticsNavHost)
// cuando se toca un video ahí en vez de entrar por la pestaña "Subir".
@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
    initialFileId: Long? = null,
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
    val importingRemoteId by viewModel.importingRemoteId.collectAsState()
    val remoteImportError by viewModel.remoteImportError.collectAsState()
    val nextUploads by viewModel.nextUploads.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val pendingBatchFileId by viewModel.pendingBatchFileId.collectAsState()
    val canSeeLanLibrary by viewModel.canSeeLanLibrary.collectAsState()
    val lanVideos by viewModel.lanVideos.collectAsState()
    val lanBaseUrl by viewModel.lanBaseUrl.collectAsState()
    var selectedFile by remember { mutableStateOf<VideoFile?>(null) }
    var selectedLan by remember { mutableStateOf<Pair<LocalPcVideoDto, String>?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Best-effort: si el usuario no tiene el entitlement de Nube, la API
    // devuelve 403 y remoteVideos queda vacío -- la sección de Nube
    // simplemente no aparece, sin error visible (mismo criterio que
    // RemoteLibraryViewModel/LibraryViewModel).
    LaunchedEffect(Unit) { viewModel.refreshRemoteVideos() }
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
    if (files.isEmpty() && remoteVideos.isEmpty() && !canSeeLanLibrary) {
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
            // Encabezado propio para este paso -- sin esto, esta lista es
            // visualmente indistinguible de Biblioteca (misma tarjeta:
            // miniatura + título + subtítulo), y quedaba ambiguo si estabas
            // en Videos o en el primer paso del formulario de Subir.
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("Subir", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Elegí qué video querés publicar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Antes un video de Nube sin bytes (storage dinámico ya los
            // liberó) fallaba en silencio -- el spinner se apagaba y no
            // pasaba nada visible. Ahora se muestra el motivo real.
            remoteImportError?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clickable { viewModel.clearRemoteImportError() },
                )
            }
            // Además de lo local, deja elegir un video de Nube como fuente --
            // se baja y de ahí en más se publica igual que cualquier archivo
            // local (mismo criterio que VideoPickerView en iOS). Antes esta
            // pantalla solo consultaba el catálogo local, la cola remota no
            // aparecía acá aunque sí existiera en la pestaña Nube.
            if (remoteVideos.isNotEmpty()) {
                RemoteVideoStrip(
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
            // FIX 2026-08-18: mismo gap que BUG-2026-08-16-03 (ver
            // docs/bug-reports.md en content-automation-dashboard) -- antes
            // este picker no tenía forma de cambiar a un video de la PC en
            // Biblioteca LAN. Solo aparece con PC autorizada ahora mismo
            // (canSeeLanLibrary), sin fallback a ningún mirror.
            val currentLanBaseUrl = lanBaseUrl
            if (canSeeLanLibrary && currentLanBaseUrl != null && lanVideos.isNotEmpty()) {
                LanVideoStrip(
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
            FileList(
                files = files,
                nextUploads = nextUploads,
                onSelect = {
                    selectedFile = it
                    selectedLan = null
                    browsingList = false
                },
                modifier = Modifier.weight(1f),
            )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("¡Publicado!") },
        text = {
            Column {
                batch?.platforms.orEmpty().forEach { state ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.platform.displayName, modifier = Modifier.weight(1f))
                        Text(
                            "Publicado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        },
    )
}

// Tira horizontal aparte (no mezclada en el LazyColumn de locales) -- son
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
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            "Cola remota",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(videos, key = { it._id }) { video ->
                val isImporting = importingId == video._id
                Card(
                    modifier = Modifier.width(140.dp),
                    onClick = { if (importingId == null) onSelect(video) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(6.dp))
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
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        val nextFor = Platform.publishable.filter { nextUploads[it] == video.fileName }
                        if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
                    }
                }
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
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            "Biblioteca LAN",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(videos, key = { it._id }) { video ->
                Card(
                    modifier = Modifier.width(140.dp),
                    onClick = { onSelect(video) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(6.dp))
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
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        val nextFor = Platform.publishable.filter { nextUploads[it] == video.fileName }
                        if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<VideoFile>,
    nextUploads: Map<Platform, String>,
    onSelect: (VideoFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(files, key = { it.id }) { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(file) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UploadThumbnail(file.thumbnailPath)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            file.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Faltan: ${pendingPlatformsLabel(file)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val nextFor = Platform.publishable.filter { nextUploads[it] == file.fileName }
                        if (nextFor.isNotEmpty()) NextUploadBadgeRow(nextFor)
                    }
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
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp))
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun pendingPlatformsLabel(file: VideoFile): String =
    Platform.publishable
        .filter { it !in file.platforms && it !in file.platformsDiscarded }
        .joinToString(", ") { it.apiValue }

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
    val isPublishing = activeBatch?.isActive == true

    // Mismo criterio que iOS (interactiveDismissDisabled + confirmationDialog):
    // salir mientras hay un lote en curso pide confirmación, tanto por el
    // botón "← Elegir otro video" (más abajo) como por el back del sistema.
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
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp + 80.dp),
    ) {
        Text(file.fileName, style = MaterialTheme.typography.titleLarge)
        Text(
            "← Elegir otro video",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 16.dp)
                .clickable { if (isPublishing) showExitConfirmation = true else onBackToList() },
        )

        AnimatedVisibility(visible = isPublishing) {
            PublishingBatchCard(
                batch = activeBatch,
                onCancel = viewModel::cancelBatch,
            )
        }

        if (activeBatch != null && !activeBatch.isActive && activeBatch.stage != BatchStage.COMPLETED) {
            BatchResultSummary(
                batch = activeBatch,
                onRetryFailed = {
                    viewModel.retryFailedInBatch(
                        file,
                        title,
                        description,
                        thumbnailOffsetMs = (thumbnailOffsetSec * 1000).toLong(),
                        crossPostFacebook = crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms,
                    )
                },
                onDismiss = viewModel::dismissBatch,
            )
        }

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

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

        Text(
            "Plataformas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        Platform.publishable.forEach { platform ->
            val alreadyDone = platform in file.platforms
            PlatformRow(
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

        Button(
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) { Text(if (isPublishing) "Publicando…" else "Publicar") }
    }
}

// Tarjeta persistente y visible durante toda la publicación -- reemplaza el
// feedback chico anterior (un "Subido ✓"/progreso por fila nada más). Mismo
// vocabulario que iOS: plataforma activa, progreso, aviso de "no cierres la
// app", botón para cancelar lo que todavía no arrancó.
@Composable
private fun PublishingBatchCard(batch: PublishBatchState?, onCancel: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Publicando…", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            batch?.platforms.orEmpty().forEach { state ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.platform.displayName, modifier = Modifier.weight(1f))
                    PlatformBatchStatusLabel(state)
                }
                val progress = state.progress
                if (state.stage == PlatformPublishStage.UPLOADING && progress != null) {
                    // `progress` capturado en un val local -- Float? no se
                    // promueve solo a Float dentro del lambda de
                    // LinearProgressIndicator (evaluado después, no smart-cast).
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Mantené la app abierta hasta que termine. Si salís o la cerrás, la subida puede interrumpirse.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("Cancelar publicación") }
        }
    }
}

@Composable
private fun PlatformBatchStatusLabel(state: PlatformPublishState) {
    when (state.stage) {
        PlatformPublishStage.PENDING -> Text(
            "En espera",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PlatformPublishStage.UPLOADING -> Text(
            "${((state.progress ?: 0f) * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
        )
        PlatformPublishStage.SUCCESS -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "Publicado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        PlatformPublishStage.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            Text(
                state.error ?: "Error",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        PlatformPublishStage.CANCELLED -> Text(
            "Cancelado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Resumen post-lote cuando NO fue éxito total -- éxito total lo maneja
// PublishSuccessDialog (modal notorio en vez de esto). Acá se cubre falla
// total y parcial: qué salió, qué no, y reintentar solo lo que falta.
@Composable
private fun BatchResultSummary(batch: PublishBatchState, onRetryFailed: () -> Unit, onDismiss: () -> Unit) {
    val hasRetriable = batch.platforms.any {
        it.stage == PlatformPublishStage.FAILED || it.stage == PlatformPublishStage.CANCELLED
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (batch.stage == BatchStage.PARTIAL_FAILURE) "Publicación parcial" else "No se pudo publicar",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            batch.platforms.forEach { state ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.platform.displayName, modifier = Modifier.weight(1f))
                    PlatformBatchStatusLabel(state)
                }
            }
            if (hasRetriable) {
                Button(
                    onClick = onRetryFailed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) { Text("Reintentar fallidas") }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Descartar") }
            }
        }
    }
}

@Composable
private fun FacebookCrossPostRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, bottom = 4.dp),
    ) {
        Checkbox(checked = checked && enabled, enabled = enabled, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text("También publicar en Facebook", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Mismo video, como Reel en la Página vinculada a tu cuenta de Instagram.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text("Portada", style = MaterialTheme.typography.titleMedium)
        Text(
            "Elegí el frame que se va a usar de miniatura.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
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
        Text(
            formatSecondsLabel(offsetSec),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSecondsLabel(seconds: Float): String {
    val totalSec = seconds.toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

// Fila entera clickeable (no solo el Checkbox), con el logo real de la
// plataforma en un badge circular que se ilumina con el color de marca al
// elegirla -- mismo lenguaje visual que los badges de Biblioteca/
// Estadísticas/Calendario, pero acá además es la forma de seleccionar qué
// publicar, no solo un indicador de estado.
@Composable
private fun PlatformRow(
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
    val selected = checked || alreadyDone

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (selected) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = platformIcon(platform)
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = platformFullLabel(platform),
                    tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(
                platformFullLabel(platform),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
        Checkbox(checked = selected, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> Color.Gray
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
    Platform.FACEBOOK -> null
}

@Composable
private fun PlatformStatusLabel(alreadyDone: Boolean, workInfo: WorkInfo?) {
    when {
        alreadyDone -> Text(
            "Ya publicado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        workInfo?.state == WorkInfo.State.RUNNING -> {
            val progress = workInfo.progress.getFloat(UploadWorker.KEY_PROGRESS, 0f)
            Row {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
        workInfo?.state == WorkInfo.State.ENQUEUED -> Text(
            "En cola…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        workInfo?.state == WorkInfo.State.SUCCEEDED -> Text(
            "Subido ✓",
            style = MaterialTheme.typography.labelSmall,
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
            style = MaterialTheme.typography.labelSmall,
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
