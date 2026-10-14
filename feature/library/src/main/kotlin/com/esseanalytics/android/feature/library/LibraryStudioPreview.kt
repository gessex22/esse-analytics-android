package com.esseanalytics.android.feature.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.model.ContentStatus
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.VideoFile
import com.esseanalytics.android.core.network.dto.LocalPcFileIdDto
import com.esseanalytics.android.core.network.dto.LocalPcVideoDto
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import java.time.Instant

private val previewDate = Instant.parse("2026-10-13T15:00:00Z")

private val studioPreviewItems = listOf(
    LibraryListItem.Local(
        VideoFile(
            id = 1,
            fileName = "Receta de otoño · edición final.mp4",
            filePath = "/preview/receta.mp4",
            status = FileStatus.PENDIENTE,
            contentStatus = ContentStatus.BORRADOR,
            platforms = listOf(Platform.INSTAGRAM, Platform.FACEBOOK),
            platformsDiscarded = listOf(Platform.TIKTOK),
            duracionSegundos = 42,
            resolucion = "1080p",
            thumbnailPath = null,
            createdAt = previewDate,
        ),
    ),
    LibraryListItem.Remote(
        RemoteLibraryVideoDto(
            _id = "preview-remote-1",
            fileName = "Colaboración: café, cocina y un nombre largo para probar el truncado",
            sizeBytes = 18_000_000,
            durationSeconds = 90.8,
            resolution = "1080p",
            platforms = listOf("instagram", "facebook"),
            createdAt = "2026-10-12T15:00:00Z",
        ),
    ),
    LibraryListItem.LanVideo(
        LocalPcVideoDto(
            _id = "preview-lan-1",
            file_id = LocalPcFileIdDto("Grabación del estudio en la PC.mp4"),
            platforms = listOf("youtube"),
            platforms_discarded = listOf("tiktok"),
            duracion_segundos = 3_661.9,
            resolucion = "4K",
            fecha_creacion = "2026-10-11 10:20:00",
        ),
        baseUrl = "http://preview.invalid",
    ),
)

private val studioDurationPreviewItems = listOf(
    studioPreviewItems[0],
    studioPreviewItems[1],
    studioPreviewItems[2],
    (studioPreviewItems[1] as LibraryListItem.Remote).copy(
        video = (studioPreviewItems[1] as LibraryListItem.Remote).video.copy(
            _id = "preview-remote-nan",
            fileName = "Duración no finita",
            durationSeconds = Double.NaN,
        ),
    ),
    (studioPreviewItems[1] as LibraryListItem.Remote).copy(
        video = (studioPreviewItems[1] as LibraryListItem.Remote).video.copy(
            _id = "preview-remote-overflow",
            fileName = "Duración fuera de rango",
            durationSeconds = Double.MAX_VALUE,
        ),
    ),
    (studioPreviewItems[2] as LibraryListItem.LanVideo).copy(
        video = (studioPreviewItems[2] as LibraryListItem.LanVideo).video.copy(
            _id = "preview-lan-null-duration",
            file_id = LocalPcFileIdDto("Duración ausente"),
            duracion_segundos = null,
        ),
    ),
    (studioPreviewItems[0] as LibraryListItem.Local).copy(
        file = (studioPreviewItems[0] as LibraryListItem.Local).file.copy(
            id = 4,
            fileName = "Duración negativa",
            duracionSegundos = -1,
        ),
    ),
)

private val studioReferencePreviewItem = LibraryListItem.Local(
    (studioPreviewItems.first() as LibraryListItem.Local).file.copy(
        id = 2,
        fileName = "Referencia importada desde Archivos.mp4",
        filePath = "content://media/external/video/media/123",
        thumbnailPath = null,
    ),
)

@Composable
private fun LibraryStudioPreviewContent(
    filter: LibraryFilter = LibraryFilter.ALL,
    items: List<LibraryListItem> = studioPreviewItems,
    canUseCloudStorage: Boolean = true,
    canSeeLanLibrary: Boolean = true,
    lanLoadError: String? = null,
) {
    val selectedFilter = remember { mutableStateOf(filter) }
    val deleteTarget = remember { mutableStateOf<LibraryListItem?>(null) }
    CreatorHomeTheme {
        LibraryStudioContent(
            items = items,
            filter = selectedFilter.value,
            canUseCloudStorage = canUseCloudStorage,
            canSeeLanLibrary = canSeeLanLibrary,
            nextUploads = mapOf(Platform.INSTAGRAM to "Colaboración: café, cocina y un nombre largo para probar el truncado"),
            lanLoadError = lanLoadError,
            isRefreshing = false,
            username = "Elena",
            onProfileClick = {},
            onRefresh = {},
            onImportClick = {},
            thumbnailUrl = { item ->
                when (item) {
                    is LibraryListItem.Local -> null
                    is LibraryListItem.Remote -> "file:///videos-studio-preview-thumbnail.jpg"
                    is LibraryListItem.LanVideo -> null
                }
            },
            onFilterChange = { selectedFilter.value = it },
            onOpenDetails = {},
            onPlay = {},
            onEdit = {},
            deleteTarget = deleteTarget.value,
            onDeleteRequested = { deleteTarget.value = it },
            onDeleteConfirmed = { deleteTarget.value = null },
            onDeleteDismissed = { deleteTarget.value = null },
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Videos · Todos · claro", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioLightPreview() = LibraryStudioPreviewContent()

@Preview(name = "Videos · Todos · oscuro", showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryStudioDarkPreview() = LibraryStudioPreviewContent()

@Preview(name = "Videos · ancho", showBackground = true, widthDp = 480, heightDp = 900)
@Composable
private fun LibraryStudioWidePreview() = LibraryStudioPreviewContent()

@Preview(name = "Videos · una fila", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioSingleRowPreview() = LibraryStudioPreviewContent(
    items = listOf(studioPreviewItems.first()),
    canUseCloudStorage = false,
    canSeeLanLibrary = false,
)

@Preview(name = "Videos · duraciones", showBackground = true, widthDp = 393, heightDp = 900)
@Composable
private fun LibraryStudioDurationsPreview() = LibraryStudioPreviewContent(items = studioDurationPreviewItems)

@Preview(name = "Videos · 320 dp", showBackground = true, widthDp = 320, heightDp = 780)
@Composable
private fun LibraryStudioNarrowPreview() = LibraryStudioPreviewContent()

@Preview(name = "Videos · texto grande", showBackground = true, widthDp = 320, heightDp = 780, fontScale = 1.3f)
@Composable
private fun LibraryStudioLargeTextPreview() = LibraryStudioPreviewContent()

@Preview(name = "Videos · solo Local", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioLocalOnlyPreview() = LibraryStudioPreviewContent(
    filter = LibraryFilter.LOCAL,
    items = listOf(studioPreviewItems.first()),
    canUseCloudStorage = false,
    canSeeLanLibrary = false,
)

@Preview(name = "Videos · Nube vacía", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioRemoteEmptyPreview() = LibraryStudioPreviewContent(
    filter = LibraryFilter.REMOTE,
    items = emptyList(),
    canUseCloudStorage = true,
    canSeeLanLibrary = false,
)

@Preview(name = "Videos · PC vacía", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioLanEmptyPreview() = LibraryStudioPreviewContent(
    filter = LibraryFilter.LAN,
    items = emptyList(),
    canUseCloudStorage = false,
    canSeeLanLibrary = true,
)

@Preview(name = "Videos · error LAN real", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioLanErrorPreview() = LibraryStudioPreviewContent(
    filter = LibraryFilter.ALL,
    items = studioPreviewItems,
    lanLoadError = "No se pudo conectar con Equipo de cocina",
)

@Preview(name = "Videos · confirmar eliminación", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioDeleteLocalPreview() = LibraryStudioDeletePreview(studioPreviewItems.first())

@Preview(name = "Videos · confirmar referencia", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioDeleteReferencePreview() = LibraryStudioDeletePreview(studioReferencePreviewItem)

@Preview(name = "Videos · confirmar Nube", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LibraryStudioDeleteRemotePreview() = LibraryStudioDeletePreview(studioPreviewItems[1])

@Composable
private fun LibraryStudioDeletePreview(item: LibraryListItem) {
    val deleteTarget = remember(item) { mutableStateOf<LibraryListItem?>(item) }
    CreatorHomeTheme {
        LibraryStudioContent(
            items = studioPreviewItems,
            filter = LibraryFilter.ALL,
            canUseCloudStorage = true,
            canSeeLanLibrary = true,
            nextUploads = emptyMap(),
            lanLoadError = null,
            isRefreshing = false,
            username = "Elena",
            onProfileClick = {},
            onRefresh = {},
            onImportClick = {},
            thumbnailUrl = { null },
            onFilterChange = {},
            onOpenDetails = {},
            onPlay = {},
            onEdit = {},
            deleteTarget = deleteTarget.value,
            onDeleteRequested = { deleteTarget.value = it },
            onDeleteConfirmed = { deleteTarget.value = null },
            onDeleteDismissed = { deleteTarget.value = null },
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
