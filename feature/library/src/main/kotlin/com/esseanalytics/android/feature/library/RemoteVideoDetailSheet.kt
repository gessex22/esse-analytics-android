package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto

// Mirror de RemoteVideoDetailView (iOS) para un video que todavía vive SOLO
// en Nube -- ver RemoteVideoEditViewModel para por qué esto no puede
// reusar VideoDetailSheet (que opera sobre Room).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteVideoDetailSheet(
    video: RemoteLibraryVideoDto,
    onDismiss: () -> Unit,
    viewModel: RemoteVideoEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(video._id) { viewModel.setInitial(video) }

    val current by viewModel.video.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    var linkEditorPlatform by remember { mutableStateOf<Platform?>(null) }
    var linkEditorText by remember { mutableStateOf("") }

    val shownVideo = current ?: video

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(shownVideo.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                "Marcar publicado a mano y cargar el link real de cada plataforma.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )

            Platform.publishable.forEach { platform ->
                VideoDetailPlatformRow(
                    platform = platform,
                    status = when {
                        platform.apiValue in shownVideo.platforms -> PlatformBadgeState.PUBLISHED
                        platform.apiValue in shownVideo.platformsDiscarded -> PlatformBadgeState.DISCARDED
                        else -> PlatformBadgeState.PENDING
                    },
                    hasLink = shownVideo.platformLinks.any { it.platform == platform.apiValue && it.platformUrl != null },
                    onToggleStatus = { viewModel.togglePlatform(platform) },
                    onEditLink = {
                        linkEditorText = viewModel.existingLink(platform) ?: ""
                        linkEditorPlatform = platform
                    },
                )
            }

            errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    linkEditorPlatform?.let { platform ->
        LinkEditorDialog(
            platform = platform,
            text = linkEditorText,
            onTextChange = { linkEditorText = it },
            isSaving = isSaving,
            onDismiss = { linkEditorPlatform = null },
            onSave = {
                viewModel.saveLink(platform, linkEditorText)
                linkEditorPlatform = null
            },
        )
    }
}
