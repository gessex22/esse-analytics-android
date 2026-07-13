package com.esseanalytics.android.feature.upload

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile

// Fase 1: elegir un video ya importado, tildar a qué plataformas publicarlo,
// completar título/descripción, y encolar un UploadWorker por plataforma.
// Ver el plan, sección "Subidas directas".
@Composable
fun UploadScreen(modifier: Modifier = Modifier, viewModel: UploadViewModel = hiltViewModel()) {
    val files by viewModel.files.collectAsState()
    var selectedFile by remember { mutableStateOf<VideoFile?>(null) }

    if (files.isEmpty()) {
        PlaceholderScreen(
            title = "Nada para subir todavía",
            note = "Importá un video primero desde la pestaña Videos.",
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        val current = selectedFile
        if (current == null) {
            FileList(files = files, onSelect = { selectedFile = it })
        } else {
            PublishForm(
                file = current,
                viewModel = viewModel,
                onBackToList = { selectedFile = null },
            )
        }
    }
}

@Composable
private fun FileList(files: List<VideoFile>, onSelect: (VideoFile) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(files, key = { it.id }) { file ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onSelect(file) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(file.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Faltan: ${pendingPlatformsLabel(file)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun pendingPlatformsLabel(file: VideoFile): String =
    Platform.publishable
        .filter { it !in file.platforms && it !in file.platformsDiscarded }
        .joinToString(", ") { it.apiValue }

@Composable
private fun PublishForm(file: VideoFile, viewModel: UploadViewModel, onBackToList: () -> Unit) {
    var title by remember(file.id) { mutableStateOf(file.fileName.substringBeforeLast('.')) }
    var description by remember(file.id) { mutableStateOf("") }
    var selectedPlatforms by remember(file.id) {
        mutableStateOf(
            Platform.publishable.filter { it !in file.platforms && it !in file.platformsDiscarded }.toSet(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(file.fileName, style = MaterialTheme.typography.titleLarge)
        Text(
            "← Elegir otro video",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 16.dp)
                .clickable(onClick = onBackToList),
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
            val alreadyDone = platform in file.platforms
            PlatformRow(
                platform = platform,
                fileId = file.id,
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
            onClick = { viewModel.publish(file, selectedPlatforms, title, description) },
            enabled = selectedPlatforms.isNotEmpty() && title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) { Text("Publicar") }
    }
}

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked || alreadyDone, enabled = enabled, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(platform.apiValue, style = MaterialTheme.typography.bodyLarge)
            PlatformStatusLabel(alreadyDone = alreadyDone, workInfo = workInfo)
        }
    }
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
