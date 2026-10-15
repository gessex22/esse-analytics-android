package com.esseanalytics.android.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.CreatorChartBar
import com.esseanalytics.android.core.designsystem.component.CreatorCommentPlatform
import com.esseanalytics.android.core.designsystem.component.CreatorCommentsSummary
import com.esseanalytics.android.core.designsystem.component.CreatorHomeBrand
import com.esseanalytics.android.core.designsystem.component.CreatorFloatingHomeActions
import com.esseanalytics.android.core.designsystem.component.CreatorMetric
import com.esseanalytics.android.core.designsystem.component.CreatorLoadingBar
import com.esseanalytics.android.core.designsystem.component.CreatorMetricCard
import com.esseanalytics.android.core.designsystem.component.CreatorPlatformBadge
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.component.CreatorVideoListItem
import com.esseanalytics.android.core.designsystem.component.CreatorVideoStyle
import com.esseanalytics.android.core.designsystem.component.formatCreatorMetric
import com.esseanalytics.android.core.designsystem.component.RefreshErrorBanner
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.GroupStatsItemDto
import com.esseanalytics.android.core.network.dto.GroupStatsSlotDto
import java.text.NumberFormat
import java.util.Locale

// Home de estudio: acciones flotantes, métrica integrada y contenido identificable.
// Conserva las consultas, caché y refresh del ViewModel existente.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    username: String = "",
    onVideoClick: (GroupStatsItemDto) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSeeAllClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(CreatorMetric.VIEWS) }
    var selectedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    val success = state as? DashboardUiState.Success
    val pullState = rememberPullToRefreshState()
    val isLoading = state == DashboardUiState.Loading || success?.isRefreshing == true
    val recentVideos = success?.data?.items.orEmpty()
    val total = remember(recentVideos, metric) { recentVideos.sumOf { videoMetric(it, metric) } }
    val bars = remember(recentVideos, metric) {
        recentVideos.asReversed().map {
            CreatorChartBar(
                videoMetric(it, metric), videoTitle(it), homeVideoId(it), formatCreatorMetric(videoMetric(it, metric)),
                viewModel.thumbnailUrl(it) ?: it.platforms.values.firstOrNull { slot -> slot.thumbnail.isNotBlank() }?.thumbnail,
            )
        }
    }
    val selectedId = selectedVideoId?.takeIf { id -> recentVideos.any { homeVideoId(it) == id } }
        ?: recentVideos.firstOrNull()?.let(::homeVideoId)
    CreatorHomeTheme {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val showUsername = maxWidth >= 360.dp && (LocalDensity.current.fontScale <= 1.2f || maxWidth >= 400.dp)
            CreatorStudioBackdrop()
            PullToRefreshBox(
                isRefreshing = success?.isRefreshing == true,
                onRefresh = viewModel::refresh,
                state = pullState,
                indicator = {},
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = CreatorVideoStyle.screenHorizontalPadding, end = CreatorVideoStyle.screenHorizontalPadding, top = 8.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "brand") { CreatorHomeBrand(Modifier.padding(horizontal = 8.dp)) }
                    when (val current = state) {
                        DashboardUiState.Loading -> item(key = "loading") {
                            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                                Text("Cargando tus videos…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                        is DashboardUiState.Error -> item(key = "error") {
                            HomeMessage("No pudimos cargar tus videos", current.message, viewModel::refresh)
                        }
                        is DashboardUiState.Success -> {
                            current.refreshError?.let { message ->
                                item(key = "refreshError") { RefreshErrorBanner(message) }
                            }
                            item(key = "metrics") {
                                CreatorMetricCard(
                                    selectedMetric = metric,
                                    onMetricSelected = { metric = it },
                                    value = formatCreatorMetric(total),
                                    rangeLabel = when (recentVideos.size) {
                                        0 -> "Tus publicaciones"
                                        1 -> "Último video"
                                        else -> "Últimos ${recentVideos.size} videos"
                                    },
                                    bars = bars,
                                    selectedBarId = selectedId,
                                    onBarSelected = { selectedVideoId = it },
                                    thumbnail = { bar, thumbnailModifier -> HomeThumbnail(bar.thumbnailUrl, thumbnailModifier) },
                                )
                            }
                            item(key = "recentTitle") {
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Videos recientes", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = CreatorSerif, modifier = Modifier.weight(1f))
                                    TextButton(onClick = onSeeAllClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                        Text("Ver todos ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (recentVideos.isEmpty()) {
                                item(key = "empty") { HomeMessage("Tu próxima idea empieza aquí", "Cuando publiques un video, verás aquí sus métricas.") }
                            }
                            items(recentVideos, key = ::homeVideoId) { video ->
                                HomeVideoRow(
                                    video, metric, viewModel.thumbnailUrl(video),
                                    highlighted = homeVideoId(video) == selectedId,
                                    onClick = { onVideoClick(video) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "comments") {
                                val platformComments = remember(recentVideos) {
                                    Platform.entries.mapNotNull { platform ->
                                        val slots = recentVideos.mapNotNull { it.platforms[platform.apiValue] }
                                        if (slots.isEmpty()) null else platform to slots.sumOf { it.comments.toLong() }
                                    }
                                }
                                CreatorCommentsSummary(
                                    total = NumberFormat.getIntegerInstance(Locale("es")).format(platformComments.sumOf { it.second }),
                                    scope = when (recentVideos.size) {
                                        0 -> "Sin videos publicados"
                                        1 -> "En el video de esta lista"
                                        else -> "En los ${recentVideos.size} videos de esta lista"
                                    },
                                    platforms = platformComments.map { (platform, count) ->
                                        CreatorCommentPlatform(
                                            platform.displayName,
                                            NumberFormat.getIntegerInstance(Locale("es")).format(count),
                                            homePlatformIcon(platform), homePlatformColor(platform),
                                        )
                                    },
                                    modifier = Modifier.padding(top = 14.dp),
                                )
                            }
                        }
                    }
                }
            }
            CreatorFloatingHomeActions(
                username = username,
                onNotificationsClick = onNotificationClick,
                onProfileClick = onProfileClick,
                showUsername = showUsername,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 20.dp),
            )
            CreatorLoadingBar(
                isLoading = isLoading,
                pullProgress = pullState.distanceFraction,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeMessage(title: String, message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeVideoRow(
    video: GroupStatsItemDto,
    metric: CreatorMetric,
    remoteThumbnail: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val thumbnail = remoteThumbnail ?: video.platforms.values.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail
    val presentPlatforms = Platform.entries.filter { platform ->
        val slot = video.platforms[platform.apiValue]
        slot != null && (slot.platformId.isNotBlank() || slot.platformUrl.isNotBlank() || slot.thumbnail.isNotBlank())
    }
    CreatorVideoListItem(
        title = videoTitle(video),
        value = formatCreatorMetric(videoMetric(video, metric)),
        metricLabel = metric.unit,
        highlighted = highlighted,
        modifier = modifier,
        onClick = onClick,
        onMoreClick = { menuOpen = true },
        thumbnail = { thumbnailModifier ->
            HomeThumbnail(thumbnail, thumbnailModifier)
        },
        metadata = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Platform.entries.filter { it != Platform.FACEBOOK }.forEach { platform ->
                    if (platform in presentPlatforms) {
                        CreatorPlatformBadge(homePlatformIcon(platform), platform.displayName, homePlatformColor(platform))
                    } else {
                        Text("${platform.displayName} pendiente", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        },
        menu = {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Ver estadísticas") }, onClick = { menuOpen = false; onClick() })
            }
        },
    )
}

@Composable
private fun HomeThumbnail(imageUrl: String?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        if (imageUrl != null) AsyncImage(imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

private fun homeVideoId(video: GroupStatsItemDto): String = video.fileId.ifBlank { video.fileName }

private fun homePlatformIcon(platform: Platform): ImageVector = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> PlatformIcons.FacebookLogo
}

@Composable
private fun homePlatformColor(platform: Platform) = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> MaterialTheme.colorScheme.onSurface
    Platform.FACEBOOK -> FacebookBlue
}

private fun videoTitle(video: GroupStatsItemDto): String =
    video.platforms.values.firstOrNull { it.title.isNotBlank() }?.title
        ?: video.fileName.substringBeforeLast('.').ifBlank { "Video sin título" }

private fun slotValue(slot: GroupStatsSlotDto, metric: CreatorMetric): Long = when (metric) {
    CreatorMetric.VIEWS -> slot.views.toLong()
    CreatorMetric.LIKES -> slot.likes.toLong()
    CreatorMetric.COMMENTS -> slot.comments.toLong()
}

private fun videoMetric(video: GroupStatsItemDto, metric: CreatorMetric): Long =
    video.platforms.values.sumOf { slotValue(it, metric) }
