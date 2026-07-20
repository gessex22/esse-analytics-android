package com.esseanalytics.android.feature.remotelibrary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto

// Cola de videos que vive físicamente en la central (carpeta /publicados/,
// owner-only por ahora, ver Parte C del plan) -- a diferencia de Biblioteca
// (feature:library), esto NO tiene equivalente en Room: la lista siempre sale
// de la red (GET /videos), no hay copia local.
@Composable
fun RemoteLibraryScreen(
    initialVideoId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: RemoteLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    var selectedVideo by remember { mutableStateOf<RemoteLibraryVideoDto?>(null) }
    // Llegar acá desde un ítem remoto en Videos (Parte D del plan) abre el
    // formulario de publicar de una, apenas la lista carga y lo encuentra --
    // no consumido de nuevo si el usuario después vuelve a la lista a mano.
    var consumedInitial by remember { mutableStateOf(false) }

    LaunchedEffect(initialVideoId, uiState) {
        if (!consumedInitial && initialVideoId != null) {
            val loaded = (uiState as? RemoteLibraryUiState.Loaded)?.videos
            val match = loaded?.find { it._id == initialVideoId }
            if (match != null) {
                selectedVideo = match
                consumedInitial = true
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::uploadVideo) }

    Box(modifier = modifier.fillMaxSize()) {
        val current = selectedVideo
        if (current != null) {
            RemotePublishForm(
                video = current,
                viewModel = viewModel,
                onBack = { selectedVideo = null },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text("Biblioteca remota", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Cola de videos en la central, publicable desde cualquier dispositivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                when (val state = uiState) {
                    is RemoteLibraryUiState.Loading -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    is RemoteLibraryUiState.Error -> PlaceholderScreen(
                        title = "No se pudo cargar la cola remota",
                        note = state.message,
                        icon = Icons.Outlined.CloudOff,
                        iconTint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )

                    is RemoteLibraryUiState.Loaded -> if (state.videos.isEmpty()) {
                        PlaceholderScreen(
                            title = "Cola remota vacía",
                            note = "Subí un video para poder publicarlo desde cualquier dispositivo, sin depender de la PC.",
                            icon = Icons.Outlined.CloudQueue,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.videos, key = { it._id }) { video ->
                                RemoteVideoRow(
                                    video = video,
                                    onClick = { selectedVideo = video },
                                    onDelete = { viewModel.delete(video) },
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { pickerLauncher.launch(arrayOf("video/*")) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                if (uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Outlined.Add, contentDescription = "Subir video a la cola remota")
                }
            }
        }
    }
}

@Composable
private fun RemoteVideoRow(video: RemoteLibraryVideoDto, onClick: () -> Unit, onDelete: () -> Unit) {
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
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(video.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    remoteStatusLabel(video),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Borrar de la cola remota",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun remoteStatusLabel(video: RemoteLibraryVideoDto): String {
    val pending = Platform.publishable.filter { it.apiValue !in video.platforms }
    return if (pending.isEmpty()) "Publicado en todas" else "Faltan: ${pending.joinToString(", ") { it.apiValue }}"
}

@Composable
private fun RemotePublishForm(video: RemoteLibraryVideoDto, viewModel: RemoteLibraryViewModel, onBack: () -> Unit) {
    var title by remember(video._id) { mutableStateOf(video.fileName.substringBeforeLast('.')) }
    var description by remember(video._id) { mutableStateOf("") }
    var selectedPlatforms by remember(video._id) {
        mutableStateOf(Platform.publishable.filter { it.apiValue !in video.platforms }.toSet())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(video.fileName, style = MaterialTheme.typography.titleLarge)
        Text(
            "← Volver a la cola",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 16.dp)
                .clickable(onClick = onBack),
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción") },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            "Plataformas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        Platform.publishable.forEach { platform ->
            val alreadyDone = platform.apiValue in video.platforms
            RemotePlatformRow(
                videoId = video._id,
                platform = platform,
                checked = platform in selectedPlatforms,
                enabled = !alreadyDone,
                alreadyDone = alreadyDone,
                onCheckedChange = { checked ->
                    selectedPlatforms = if (checked) selectedPlatforms + platform else selectedPlatforms - platform
                },
                viewModel = viewModel,
            )
        }

        Button(
            onClick = { viewModel.publish(video, selectedPlatforms, title, description) },
            enabled = selectedPlatforms.isNotEmpty() && title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) { Text("Publicar") }
    }
}

@Composable
private fun RemotePlatformRow(
    videoId: String,
    platform: Platform,
    checked: Boolean,
    enabled: Boolean,
    alreadyDone: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    viewModel: RemoteLibraryViewModel,
) {
    val workInfoFlow = remember(videoId, platform) { viewModel.observeWork(videoId, platform) }
    val workInfo by workInfoFlow.collectAsState(initial = null)

    // Apenas termina de publicar, refresca la lista -- así "Ya publicado"
    // queda reflejado sin que el usuario tenga que volver atrás y re-entrar.
    LaunchedEffect(workInfo?.state) {
        if (workInfo?.state == WorkInfo.State.SUCCEEDED) viewModel.refresh()
    }

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
            RemotePlatformStatusLabel(alreadyDone = alreadyDone, workInfo = workInfo)
        }
        Checkbox(checked = selected, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RemotePlatformStatusLabel(alreadyDone: Boolean, workInfo: WorkInfo?) {
    when {
        alreadyDone -> Text(
            "Ya publicado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        workInfo?.state == WorkInfo.State.RUNNING -> {
            val progress = workInfo.progress.getFloat(RemoteUploadWorker.KEY_PROGRESS, 0f)
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
            workInfo.outputData.getString(RemoteUploadWorker.KEY_ERROR) ?: "Falló la subida",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
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
