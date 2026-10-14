package com.esseanalytics.android.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.esseanalytics.android.core.designsystem.component.CreatorLoadingBar
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.component.CreatorVideoStyle
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
    username: String = "",
    onProfileClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val publishedThumbnails by viewModel.publishedThumbnails.collectAsState()
    val thumbnailRevision by viewModel.publishedThumbnailRevision.collectAsState()
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val refresh = {
        today = LocalDate.now()
        viewModel.refresh()
    }
    CalendarStudioContent(
        state = state,
        username = username,
        onProfileClick = onProfileClick,
        onRefresh = refresh,
        onDiscard = viewModel::discardNext,
        publishedThumbnails = publishedThumbnails,
        thumbnailRevision = thumbnailRevision,
        onVisiblePublishedEvents = viewModel::updateVisiblePublishedEvents,
        onSelectedDateChanged = viewModel::selectPublishedDay,
        today = today,
        modifier = modifier,
    )
}

@Composable
private fun AgendaFloatingActions(
    username: String,
    onRefresh: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onRefresh,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Actualizar agenda" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            onClick = onProfileClick,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "Abrir perfil de " + username.ifBlank { "Perfil" }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        username.trim().firstOrNull()?.uppercase() ?: "E",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun CalendarStudioContent(
    state: CalendarUiState,
    username: String = "",
    onProfileClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDiscard: (CalendarSlot) -> Unit = {},
    today: LocalDate = LocalDate.now(),
    initialSelectedDate: LocalDate? = null,
    publishedThumbnails: Map<PublishedThumbnailKey, PublishedThumbnailSources> = emptyMap(),
    thumbnailRevision: Long = 0L,
    onVisiblePublishedEvents: (LocalDate, List<UploadHistoryItemDto>) -> Unit = { _, _ -> },
    onSelectedDateChanged: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var discardSlot by remember { mutableStateOf<CalendarSlot?>(null) }
    val success = state as? CalendarUiState.Success
    val week = remember(today) { weekDates(today) }
    val initialSelection = initialSelectedDate?.takeIf(week::contains) ?: today
    var selectedDateText by rememberSaveable {
        mutableStateOf(initialSelection.toString())
    }
    var followingToday by rememberSaveable { mutableStateOf(initialSelection == today) }
    val selectedDate = selectedDateText.let { runCatching { LocalDate.parse(it) }.getOrDefault(today) }
    val slots = success?.data?.slots.orEmpty()
    val eligibleSlots = remember(slots, today, selectedDate) {
        slots.filter { slot ->
            val nextDate = slot.nextDate ?: return@filter false
            if (slot.nextVideo == null) return@filter false
            when {
                selectedDate.isBefore(today) -> false
                selectedDate == today -> nextDate.isBefore(today)
                else -> nextDate == selectedDate
            }
        }
    }
    val weekCandidateSlots = remember(slots) { slots.filter { it.nextVideo != null && it.nextDate != null } }
    val groups = remember(eligibleSlots) { pendingGroups(eligibleSlots) }
    val history = success?.data?.history.orEmpty()
    val selectedHistory = remember(history, selectedDateText) {
        history.filter { it.publishedLocalDate() == selectedDate }.sortedByDescending { it.publishedAt }
    }
    val publishedGroups = remember(history, selectedDate) { groupPublishedHistory(history, selectedDate) }
    val previousToday = remember { mutableStateOf(today) }
    LaunchedEffect(today, week) {
        val oldToday = previousToday.value
        val currentSelection = selectedDateText.let { runCatching { LocalDate.parse(it) }.getOrDefault(oldToday) }
        if (oldToday != today) {
            if (followingToday || currentSelection == oldToday || !week.contains(currentSelection)) {
                selectedDateText = today.toString()
                followingToday = true
            }
            previousToday.value = today
        } else if (!week.contains(currentSelection)) {
            selectedDateText = today.toString()
            followingToday = true
        }
    }
    val showPendingSection = state is CalendarUiState.Success && groups.isNotEmpty()
    val publishedViewport = remember { mutableStateOf<Rect?>(null) }
    val visiblePublishedEvents = remember { mutableStateMapOf<PublishedThumbnailKey, UploadHistoryItemDto>() }
    val visiblePublishedBounds = remember { mutableStateMapOf<PublishedThumbnailKey, Pair<UploadHistoryItemDto, Rect>>() }
    val visiblePublishedSnapshot = visiblePublishedEvents.values.toList()

    LaunchedEffect(selectedDate) {
        visiblePublishedEvents.clear()
        visiblePublishedBounds.clear()
        onSelectedDateChanged(selectedDate)
        onVisiblePublishedEvents(selectedDate, emptyList())
    }
    LaunchedEffect(selectedDate, visiblePublishedSnapshot, thumbnailRevision) {
        onVisiblePublishedEvents(selectedDate, visiblePublishedSnapshot)
    }

    CreatorHomeTheme {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val pullState = rememberPullToRefreshState()
            val loading = state == CalendarUiState.Loading || success?.isRefreshing == true
            CreatorStudioBackdrop()
            PullToRefreshBox(
                isRefreshing = success?.isRefreshing == true,
                onRefresh = onRefresh,
                state = pullState,
                indicator = {},
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().onGloballyPositioned {
                        val newViewport = it.boundsInWindow()
                        publishedViewport.value = newViewport
                        visiblePublishedBounds.forEach { (eventKey, entry) ->
                            if (entry.second.overlaps(newViewport)) visiblePublishedEvents[eventKey] = entry.first
                            else visiblePublishedEvents.remove(eventKey)
                        }
                    },
                    contentPadding = PaddingValues(
                        start = CreatorVideoStyle.screenHorizontalPadding,
                        end = CreatorVideoStyle.screenHorizontalPadding,
                        top = 8.dp,
                        bottom = 124.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "agenda-date-week") {
                        Column(Modifier.fillMaxWidth()) {
                            BoxWithConstraints(
                                Modifier.fillMaxWidth().padding(start = 8.dp, end = 112.dp, top = 12.dp, bottom = 8.dp),
                            ) {
                                Text(
                                    headerDate(selectedDate),
                                    modifier = Modifier.width(maxWidth).semantics {
                                        contentDescription = fullDate(selectedDate)
                                    },
                                    fontFamily = CreatorSerif,
                                    fontSize = 27.sp,
                                    lineHeight = 33.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            CalendarWeek(
                                week = week,
                                today = today,
                                selectedDate = selectedDate,
                                slots = weekCandidateSlots,
                                history = history,
                                onDateSelected = {
                                    selectedDateText = it.toString()
                                    followingToday = it == today
                                    onSelectedDateChanged(it)
                                },
                            )
                        }
                    }

                    if (state is CalendarUiState.Error) {
                        item(key = "initial-error") {
                            CalendarStatusMessage(
                                title = "No se pudo cargar la agenda",
                                message = safeErrorMessage(state.message),
                                action = "Reintentar",
                                onAction = onRefresh,
                                icon = Icons.Outlined.ErrorOutline,
                            )
                        }
                    }
                    if (success?.refreshError != null) {
                        item(key = "refresh-error") {
                            CalendarStatusMessage(
                                title = "No se pudo actualizar",
                                message = safeErrorMessage(success.refreshError),
                                action = "Reintentar",
                                onAction = onRefresh,
                                icon = Icons.Outlined.ErrorOutline,
                            )
                        }
                    }

                    if (showPendingSection) item(key = "pending-section") {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SectionHeading("Por publicar")
                            RowsSurface {
                                groups.forEachIndexed { index, group ->
                                    if (index > 0) RowDivider()
                                    PendingVideoRow(group, today) { discardSlot = it }
                                }
                            }
                        }
                    }

                    item(key = "published-section") {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            SectionHeading("Publicados · " + shortDate(selectedDate))
                            RowsSurface {
                                when (state) {
                                    CalendarUiState.Loading -> EmptyRow("Cargando publicaciones…")
                                    is CalendarUiState.Error -> EmptyRow("No se pudieron cargar las publicaciones.")
                                    is CalendarUiState.Success -> if (selectedHistory.isEmpty()) {
                                        EmptyRow("No hay publicados disponibles para este día.")
                                    } else {
                                        publishedGroups.forEachIndexed { index, group ->
                                            if (index > 0) RowDivider()
                                            key(group.identity) {
                                                PublishedEventRow(
                                                    group = group,
                                                    sources = publishedThumbnails[publishedThumbnailKey(group.thumbnailEvent)],
                                                    onBoundsChanged = { rowBounds ->
                                                        group.events.forEach { event ->
                                                            val eventKey = publishedThumbnailKey(event)
                                                            if (rowBounds == null) {
                                                                visiblePublishedBounds.remove(eventKey)
                                                                visiblePublishedEvents.remove(eventKey)
                                                            } else {
                                                                visiblePublishedBounds[eventKey] = event to rowBounds
                                                                if (rowBounds.overlaps(publishedViewport.value ?: Rect.Zero)) {
                                                                    visiblePublishedEvents[eventKey] = event
                                                                } else {
                                                                    visiblePublishedEvents.remove(eventKey)
                                                                }
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            CreatorLoadingBar(
                isLoading = loading,
                pullProgress = pullState.distanceFraction,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth(),
            )
            AgendaFloatingActions(
                username = username,
                onRefresh = onRefresh,
                onProfileClick = onProfileClick,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp),
            )
        }

        discardSlot?.let { slot ->
            val video = slot.nextVideo
            val validIdentity = !video?.fileId.isNullOrBlank()
            AlertDialog(
                onDismissRequest = { discardSlot = null },
                title = { Text("¿Descartar este próximo video?") },
                text = {
                    Text("Se descartará " + (video?.title ?: "este video") + " solo para " + platformFullLabel(slot.platform) + ".")
                },
                confirmButton = {
                    TextButton(
                        enabled = validIdentity,
                        onClick = {
                            if (validIdentity) onDiscard(slot)
                            discardSlot = null
                        },
                    ) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { discardSlot = null }) { Text("Cancelar") } },
            )
        }
    }
}

private data class PendingVideoGroup(
    val identity: String,
    val slots: List<CalendarSlot>,
) {
    val firstSlot: CalendarSlot get() = slots.first()
    val video get() = firstSlot.nextVideo!!
    val sortDate: LocalDate? get() = slots.mapNotNull { it.nextDate }.minOrNull()
}

private fun pendingGroups(slots: List<CalendarSlot>): List<PendingVideoGroup> {
    val grouped = linkedMapOf<String, MutableList<CalendarSlot>>()
    slots.filter { it.nextVideo != null }
        .sortedBy { it.platform.lowercase() }
        .forEach { slot ->
            val fileId = slot.nextVideo?.fileId.orEmpty()
            val identity = if (fileId.isNotBlank()) "file:" + fileId else "platform:" + slot.platform.lowercase()
            grouped.getOrPut(identity) { mutableListOf() }.add(slot)
        }
    return grouped.map { (identity, members) -> PendingVideoGroup(identity, members.toList()) }
        .sortedWith(
            compareBy<PendingVideoGroup> { it.sortDate == null }
                .thenBy { it.sortDate }
                .thenBy { it.firstSlot.platform.lowercase() }
                .thenBy { it.identity },
        )
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp),
        fontFamily = CreatorSerif,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun RowsSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 88.dp, end = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
        thickness = 1.dp,
    )
}

@Composable
private fun EmptyRow(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlatformLabel(platform: String) {
    val icon = platformIcon(platform)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = platformColor(platform),
            )
        }
        Text(
            platformFullLabel(platform),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PendingVideoRow(group: PendingVideoGroup, today: LocalDate, onDiscard: (CalendarSlot) -> Unit) {
    var menuExpanded by rememberSaveable(group.identity) { mutableStateOf(false) }
    val title = group.video.title.ifBlank { "Título no disponible" }
    val localThumbnail = group.slots.firstOrNull { slot ->
        slot.localThumbnailPath?.let(::File)?.exists() == true
    }?.localThumbnailPath
    val remoteThumbnail = group.slots.firstOrNull { !it.remoteThumbnailUrl.isNullOrBlank() }?.remoteThumbnailUrl
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CalendarThumbnail(localThumbnail, remoteThumbnail, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                group.slots.forEach { slot ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        PlatformLabel(slot.platform)
                        Text(
                            candidateDateLabel(slot.nextDate, today),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = if (slot.nextDate?.isBefore(today) == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                title,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = "Más opciones de " + title
                },
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                group.slots.forEach { slot ->
                    val validIdentity = !slot.nextVideo?.fileId.isNullOrBlank()
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (validIdentity) {
                                    "Descartar próximo en " + platformFullLabel(slot.platform)
                                } else {
                                    "Descartar en " + platformFullLabel(slot.platform) + " · identidad no disponible"
                                },
                            )
                        },
                        enabled = validIdentity,
                        onClick = {
                            menuExpanded = false
                            if (validIdentity) onDiscard(slot)
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PublishedEventRow(
    group: PublishedHistoryGroup,
    sources: PublishedThumbnailSources?,
    onBoundsChanged: (Rect?) -> Unit,
) {
    DisposableEffect(group.identity) {
        onDispose { onBoundsChanged(null) }
    }
    val title = group.thumbnailEvent.title?.takeIf { it.isNotBlank() }
        ?: group.thumbnailEvent.fileName
        ?: "Título no disponible"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).onGloballyPositioned { coordinates ->
            val rowBounds = coordinates.boundsInWindow()
            onBoundsChanged(rowBounds)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PublishedThumbnail(publishedThumbnailKey(group.thumbnailEvent), sources)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.events.forEach { item -> PlatformLabel(item.platform) }
            }
            val times = group.events.map { it.publishedLocalTime() }
            val commonTime = times.distinct().singleOrNull()?.takeIf { times.all { time -> !time.isNullOrBlank() } }
            when {
                group.events.size == 1 -> {
                    Text(
                        times.singleOrNull()?.let { "Hora $it" } ?: "Hora no disponible",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                commonTime != null -> {
                    Text(
                        "Hora $commonTime",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        group.events.mapIndexed { index, item ->
                            platformFullLabel(item.platform) + " " + (times[index] ?: "hora no disponible")
                        }.joinToString(" · "),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                title,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PublishedThumbnail(eventKey: PublishedThumbnailKey, sources: PublishedThumbnailSources?) {
    val context = LocalContext.current
    val sourceModels = remember(sources) {
        buildList<Any> {
            sources?.localFilePath
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.let(::add)
            sources?.remoteLibraryUrl?.takeIf(String::isNotBlank)?.let(::add)
            sources?.platformThumbnailUrl?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
    var sourceIndex by rememberSaveable(eventKey, sources) { mutableStateOf(0) }
    val model = sourceModels.getOrNull(sourceIndex)
    if (model == null) {
        PlaceholderThumbnail()
        return
    }
    val sourceDescriptor = sources ?: return PlaceholderThumbnail()

    val opaqueEventId = remember(eventKey) {
        val identity = listOf(
            eventKey.eventId,
            eventKey.platform,
            eventKey.platformId,
            eventKey.fileName.orEmpty(),
        ).joinToString(separator = "\u0000")
        MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
    }
    val cacheKey = sourceDescriptor.cacheNamespace + ":" + opaqueEventId + ":" + sourceIndex
    val request = remember(context, model, cacheKey) {
        ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.size(CreatorVideoStyle.listThumbnailSize).clip(CreatorVideoStyle.listThumbnailShape),
        contentScale = ContentScale.Crop,
        onError = {
            if (sourceIndex < sourceModels.size) sourceIndex += 1
        },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CalendarWeek(
    week: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate,
    slots: List<CalendarSlot>,
    history: List<UploadHistoryItemDto>,
    onDateSelected: (LocalDate) -> Unit,
) {
    val scrollState = rememberSaveable(week.first().toString(), saver = ScrollState.Saver) { ScrollState(0) }
    val todayRequester = remember { BringIntoViewRequester() }
    var initialScrollHandled by rememberSaveable(week.first().toString()) { mutableStateOf(false) }
    LaunchedEffect(week.first()) {
        if (!initialScrollHandled) {
            withFrameNanos { }
            todayRequester.bringIntoView()
            initialScrollHandled = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
        if (maxWidth >= 336.dp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WeekStrip(week, today, selectedDate, slots, history, todayRequester, onDateSelected)
            }
        } else {
            Row(Modifier.horizontalScroll(scrollState).semantics { contentDescription = "Semana, lunes a domingo" }) {
                WeekStrip(week, today, selectedDate, slots, history, todayRequester, onDateSelected)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WeekStrip(
    week: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate,
    slots: List<CalendarSlot>,
    history: List<UploadHistoryItemDto>,
    todayRequester: BringIntoViewRequester,
    onDateSelected: (LocalDate) -> Unit,
) {
    Row(
        Modifier.requiredWidth(384.dp).semantics { contentDescription = "Semana, lunes a domingo" },
        horizontalArrangement = Arrangement.Start,
    ) {
        WeekEdgeDay(week.first().minusDays(1), isLeft = true)
        week.forEach { date ->
            val hasCandidate = slots.any { it.nextDate == date }
            val hasPublished = history.any { it.publishedLocalDate() == date }
            WeekDayCell(
                date = date,
                today = today,
                selected = selectedDate == date,
                hasCandidate = hasCandidate,
                hasPublished = hasPublished,
                modifier = if (date == today) Modifier.bringIntoViewRequester(todayRequester) else Modifier,
                onClick = { onDateSelected(date) },
            )
        }
        WeekEdgeDay(week.last().plusDays(1), isLeft = false)
    }
}

@Composable
private fun WeekEdgeDay(date: LocalDate, isLeft: Boolean) {
    val edgeMask = if (isLeft) {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.65f)))
    } else {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0f)))
    }
    Column(
        Modifier.width(24.dp).heightIn(min = 68.dp).clearAndSetSemantics { }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = edgeMask, blendMode = BlendMode.DstIn)
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            weekdayShort(date),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(Modifier.heightIn(min = 21.dp))
    }
}

@Composable
private fun WeekDayCell(
    date: LocalDate,
    today: LocalDate,
    selected: Boolean,
    hasCandidate: Boolean,
    hasPublished: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(48.dp).heightIn(min = 68.dp).semantics(mergeDescendants = true) {
            contentDescription = "Ver publicaciones del " + fullDate(date) + if (date == today) ", hoy" else ""
            this.selected = selected
            role = Role.RadioButton
        },
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(weekdayShort(date), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier.size(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                Modifier.heightIn(min = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasCandidate) ActivityDot(MaterialTheme.colorScheme.primary)
                if (hasPublished) ActivityDot(MaterialTheme.colorScheme.tertiary)
            }
            if (date == today) Text("Hoy", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActivityDot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun CalendarThumbnail(localPath: String?, remoteUrl: String?, description: String?) {
    val model = localPath?.let(::File)?.takeIf { it.exists() } ?: remoteUrl
    if (model == null) {
        PlaceholderThumbnail(description)
    } else {
        AsyncImage(
            model = model,
            contentDescription = description,
            modifier = Modifier.size(CreatorVideoStyle.listThumbnailSize).clip(CreatorVideoStyle.listThumbnailShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PlaceholderThumbnail(description: String? = null) {
    Box(
        Modifier.size(CreatorVideoStyle.listThumbnailSize).clip(CreatorVideoStyle.listThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CalendarStatusMessage(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    icon: ImageVector,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX"))
private val headerDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "MX"))
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX"))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("es", "MX"))
private val weekdayLabels = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private fun fullDate(date: LocalDate): String = fullDateFormatter.format(date).replaceFirstChar { it.uppercase(Locale("es", "MX")) }
private fun headerDate(date: LocalDate): String = headerDateFormatter.format(date).replaceFirstChar { it.uppercase(Locale("es", "MX")) }
private fun shortDate(date: LocalDate): String = shortDateFormatter.format(date).replaceFirstChar { it.uppercase(Locale("es", "MX")) }
private fun weekdayShort(date: LocalDate): String = weekdayLabels[date.dayOfWeek.value - 1]
private fun startOfWeek(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
private fun weekDates(today: LocalDate): List<LocalDate> = (0..6).map { startOfWeek(today).plusDays(it.toLong()) }
private fun candidateDateLabel(date: LocalDate?, today: LocalDate): String = when {
    date == null -> "Sin fecha"
    date == today -> "Hoy"
    date == today.plusDays(1) -> "Mañana"
    date.isBefore(today) -> "Atrasado, " + candidateShortDate(date, today)
    else -> candidateShortDate(date, today)
}
private fun candidateShortDate(date: LocalDate, today: LocalDate): String =
    shortDate(date) + if (date.year != today.year) " " + date.year else ""
private fun UploadHistoryItemDto.publishedLocalDate(): LocalDate? =
    runCatching { Instant.parse(publishedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
private fun UploadHistoryItemDto.publishedLocalTime(): String? =
    runCatching { Instant.parse(publishedAt).atZone(ZoneId.systemDefault()).format(timeFormatter) }.getOrNull()
private fun platformFullLabel(key: String): String = Platform.fromApiValue(key)?.displayName ?: key.replaceFirstChar { it.uppercase() }
private fun platformIcon(key: String): ImageVector? = when (key) {
    "youtube" -> PlatformIcons.YoutubeLogo
    "instagram" -> PlatformIcons.InstagramLogo
    "tiktok" -> PlatformIcons.TiktokLogo
    "facebook" -> PlatformIcons.FacebookLogo
    else -> null
}
private fun platformColor(key: String): Color = when (key) {
    "youtube" -> YoutubeRed
    "instagram" -> InstagramPurple
    "tiktok" -> TiktokPink
    "facebook" -> FacebookBlue
    else -> Color.Gray
}
private fun safeErrorMessage(message: String): String = message
    .replace(Regex("(?i)bearer\\s+[a-z0-9._~+/-]+=*"), "Bearer [oculto]")
    .replace(Regex("(?i)(token|authorization|secret)(\\s*[:=]\\s*)[^\\s,;]+")) { match ->
        match.groupValues[1] + match.groupValues[2] + "[oculto]"
    }
