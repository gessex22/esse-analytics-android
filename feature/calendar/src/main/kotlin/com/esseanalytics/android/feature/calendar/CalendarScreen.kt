package com.esseanalytics.android.feature.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.SwissCard
import com.esseanalytics.android.core.designsystem.component.SwissLabel
import com.esseanalytics.android.core.designsystem.component.SwissSectionTitle
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.component.RefreshErrorBanner
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
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// REDISEÑO BENTO: Calendario como retícula de tarjetas — la semana en foco
// con celdas-tarjeta (hoy = tarjeta en el acento), Hoy y Mañana como
// tarjetas de acento, y cada slot como tarjeta propia. Toda la funcionalidad
// intacta: selección de día con su panel, descarte del próximo con
// confirmación, último publicado expandible, estados de carga/error/vacío y
// banner de error de refresh.
@Composable
fun CalendarScreen(modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var pendingDiscard by remember { mutableStateOf<CalendarSlot?>(null) }

    when (val current = state) {
        is CalendarUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        is CalendarUiState.Error -> PlaceholderScreen(
            title = "No se pudo cargar",
            note = current.message,
            icon = Icons.Outlined.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )

        is CalendarUiState.Success -> if (current.data.slots.isEmpty()) {
            PlaceholderScreen(
                title = "Todavía no hay nada programado",
                note = "Publicá al menos un video en cada plataforma para que arranque la cadencia.",
                icon = Icons.Outlined.CalendarMonth,
                modifier = modifier,
            )
        } else {
            val slots = current.data.slots
            val history = current.data.history
            val overdueSlots = slots.filter { it.nextDate != null && daysFromToday(it.nextDate) < 0 }
            val todaySlots = slots.filter { it.nextDate != null && daysFromToday(it.nextDate) == 0L }
            val tomorrowSlots = slots.filter { it.nextDate != null && daysFromToday(it.nextDate) == 1L }
            val todayPublished = history.filter { it.publishedLocalDate()?.let(::daysFromToday) == 0L }
            val tomorrowPublished = history.filter { it.publishedLocalDate()?.let(::daysFromToday) == 1L }
            val hasToday = todaySlots.isNotEmpty() || todayPublished.isNotEmpty()
            val hasTomorrow = tomorrowSlots.isNotEmpty() || tomorrowPublished.isNotEmpty()

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                // bottom extra: FloatingBottomNavigation (EsseAnalyticsNavHost) es
                // un overlay real, no un bottomBar de Scaffold -- no reserva
                // espacio propio, así que sin este margen extra el último bloque
                // queda scrolleable hasta quedar tapado detrás de la cápsula
                // flotante.
                contentPadding = PaddingValues(
                    start = Swiss.contentHorizontal,
                    top = 16.dp,
                    end = Swiss.contentHorizontal,
                    bottom = 16.dp + 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Spinner de refresh: en AppTopBar (izquierda del avatar),
                // no acá -- ver RefreshActivityTracker.
                current.refreshError?.let { message ->
                    item(key = "refreshError") { RefreshErrorBanner(message) }
                }

                if (overdueSlots.isNotEmpty()) {
                    item(key = "overdueHeader") {
                        DayBanner(
                            title = "Atrasadas",
                                                        count = overdueSlots.size,
                            urgent = true,
                        )
                    }
                    items(overdueSlots, key = { "slot-overdue-${it.platform}" }) { slot ->
                        CalendarSlotRow(slot = slot, onDiscardNext = { pendingDiscard = slot })
                    }
                }

                if (!hasToday && !hasTomorrow && overdueSlots.isEmpty()) {
                    item(key = "emptyKinds") {
                        SwissCard {
                            Text(
                                "No hay publicaciones pendientes para hoy o mañana.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (hasToday) {
                    item(key = "todayHeader") {
                        DayBanner(
                            title = "Hoy",
                                                        count = todaySlots.size + todayPublished.size,
                            highlighted = true,
                        )
                    }
                    items(todayPublished, key = { "pub-today-${it.id}" }) { item -> CalendarPublishedRow(item) }
                    items(todaySlots, key = { "slot-today-${it.platform}" }) { slot ->
                        CalendarSlotRow(slot = slot, onDiscardNext = { pendingDiscard = slot })
                    }
                }

                if (hasTomorrow) {
                    item(key = "tomorrowHeader") {
                        DayBanner(
                            title = "Mañana",
                                                        count = tomorrowSlots.size + tomorrowPublished.size,
                        )
                    }
                    items(tomorrowPublished, key = { "pub-tomorrow-${it.id}" }) { item -> CalendarPublishedRow(item) }
                    items(tomorrowSlots, key = { "slot-tomorrow-${it.platform}" }) { slot ->
                        CalendarSlotRow(slot = slot, onDiscardNext = { pendingDiscard = slot })
                    }
                }

                item(key = "weekFocus") { CalendarWeekFocus(slots = slots, history = history) }
            }
        }
    }

    pendingDiscard?.let { slot ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("¿Descartar este próximo video?") },
            text = {
                Text("Se descartará ${slot.nextVideo?.title ?: "este video"} solo para ${platformFullLabel(slot.platform)}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardNext(slot)
                    pendingDiscard = null
                }) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text("Cancelar") }
            },
        )
    }
}

/** Encabezado compacto de día; el acento se reserva para urgencias. */
@Composable
private fun DayBanner(
    title: String,
    count: Int,
    subtitle: String? = null,
    urgent: Boolean = false,
    highlighted: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val labelColor = when {
        urgent -> scheme.error
        highlighted -> scheme.primary
        else -> scheme.onSurface
    }
    val badgeColor = when {
        urgent -> scheme.error.copy(alpha = 0.14f)
        highlighted -> scheme.primary.copy(alpha = 0.14f)
        else -> scheme.surfaceContainerHigh
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = labelColor,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = labelColor,
            )
        }
    }
}

// ── Fecha ────────────────────────────────────────────────────────────────

private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX"))
private val shortRangeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX"))
private val apiDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun daysFromToday(date: LocalDate): Long = ChronoUnit.DAYS.between(LocalDate.now(), date)

private fun fullDate(date: LocalDate): String {
    val text = fullDateFormatter.format(date)
    return text.replaceFirstChar { it.uppercase(Locale("es", "MX")) }
}

private fun publishedLabel(dateString: String): String? {
    if (dateString.isBlank()) return null
    val date = runCatching { LocalDate.parse(dateString, apiDateFormatter) }.getOrNull() ?: return null
    return "Publicado el ${fullDateFormatter.format(date)}"
}

// java.time.DayOfWeek ya es lunes=1..domingo=7 (ISO-8601) -- mismo firstWeekday
// que CalendarDateSupport.calendar en iOS (firstWeekday = 2, Gregoriano
// domingo=1), no hace falta configurar nada aparte.
private fun startOfWeek(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

private fun weekDates(): List<LocalDate> {
    val start = startOfWeek(LocalDate.now())
    return (0..6).map { start.plusDays(it.toLong()) }
}

private fun rangeLabel(week: List<LocalDate>): String {
    val first = week.firstOrNull() ?: return ""
    val last = week.lastOrNull() ?: return ""
    return if (first.month == last.month) {
        "${first.dayOfMonth}–${shortRangeFormatter.format(last)}"
    } else {
        "${shortRangeFormatter.format(first)}–${shortRangeFormatter.format(last)}"
    }
}

private fun UploadHistoryItemDto.publishedLocalDate(): LocalDate? =
    runCatching { Instant.parse(publishedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

// ── Plataforma (incluye "facebook", crosspost de Instagram) ────────────────

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> FacebookBlue
}

private fun platformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}

private fun platformFullLabel(key: String): String = Platform.fromApiValue(key)?.displayName ?: key.replaceFirstChar { it.uppercase() }

private fun platformIcon(platform: Platform): ImageVector = when (platform) {
    Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
    Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
    Platform.TIKTOK -> PlatformIcons.TiktokLogo
    Platform.FACEBOOK -> PlatformIcons.FacebookLogo
}

// Tarjeta para un video ya publicado hoy/mañana — acento verde a la
// izquierda: lo publicado manda.
@Composable
private fun CalendarPublishedRow(item: UploadHistoryItemDto) {
    val title = item.title?.takeIf { it.isNotBlank() } ?: item.fileName ?: "—"
    SwissCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF22C55E)),
            )
            Spacer(Modifier.width(12.dp))
            val platform = Platform.fromApiValue(item.platform)
            if (platform != null) {
                Icon(platformIcon(platform), contentDescription = null, tint = platformColor(platform), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Semana en foco ──────────────────────────────────────────────────────────

@Composable
private fun CalendarWeekFocus(slots: List<CalendarSlot>, history: List<UploadHistoryItemDto>) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val currentWeek = remember { weekDates() }

    fun dotPlatformsOn(date: LocalDate): List<String> {
        val scheduled = slots.mapNotNull { if (it.nextDate == date) it.platform else null }
        val published = history.mapNotNull { if (it.publishedLocalDate() == date) it.platform else null }
        val seen = LinkedHashSet<String>()
        (scheduled + published).forEach { seen.add(it) }
        return seen.toList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Swiss.gap)) {
        SwissCard {
            SwissSectionTitle(
                index = "01",
                title = rangeLabel(currentWeek),
                subtitle = "Toca un día para ver lo publicado y lo pendiente.",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currentWeek.forEach { date ->
                    CalendarDayCell(
                        date = date,
                        platforms = dotPlatformsOn(date),
                        isSelected = selectedDate == date,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedDate = if (selectedDate == date) null else date
                            },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedDate != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            selectedDate?.let { date ->
                CalendarDaySelectionPanel(
                    date = date,
                    scheduledSlots = slots.filter { it.nextDate == date },
                    publishedItems = history
                        .filter { it.publishedLocalDate() == date }
                        .sortedByDescending { it.publishedAt },
                )
            }
        }
    }
}

// Celda de día bento: tarjeta pequeña redondeada; hoy = tarjeta en el acento,
// seleccionado = tarjeta tonal.
@Composable
private fun CalendarDayCell(date: LocalDate, platforms: List<String>, isSelected: Boolean, modifier: Modifier = Modifier) {
    val days = daysFromToday(date)
    val isToday = days == 0L
    val shortWeekday = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "MX")).uppercase(Locale("es", "MX"))
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isToday -> scheme.primary
                    isSelected -> scheme.primaryContainer
                    else -> scheme.surfaceVariant.copy(alpha = 0.5f)
                },
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val onAccent = isToday
        Text(
            shortWeekday,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
            color = if (onAccent) scheme.onPrimary else scheme.onSurfaceVariant,
        )
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = when {
                onAccent -> scheme.onPrimary
                isSelected -> scheme.onPrimaryContainer
                else -> scheme.onSurface
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            platforms.forEach { key ->
                val color = when {
                    onAccent -> scheme.onPrimary
                    isSelected -> scheme.primary
                    else -> Platform.fromApiValue(key)?.let(::platformColor) ?: scheme.onSurfaceVariant
                }
                Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(4.dp)).background(color))
            }
        }
    }
}

// Detalle del día tocado en tarjeta — separa lo YA publicado (historial real)
// de lo programado para ese día (todavía no pasó).
@Composable
private fun CalendarDaySelectionPanel(date: LocalDate, scheduledSlots: List<CalendarSlot>, publishedItems: List<UploadHistoryItemDto>) {
    SwissCard {
        Text(fullDate(date), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))

        if (publishedItems.isEmpty() && scheduledSlots.isEmpty()) {
            Text(
                "Nada programado ni publicado este día.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                publishedItems.forEach { item ->
                    DaySelectionRow(
                        platformKey = item.platform,
                        title = item.title?.takeIf { it.isNotBlank() } ?: item.fileName ?: "—",
                    ) {
                                }
                }
                scheduledSlots.forEach { slot ->
                    DaySelectionRow(
                        platformKey = slot.platform,
                        title = slot.nextVideo?.title ?: "—",
                    ) {
                        val duration = slot.nextVideo?.duration
                        if (!duration.isNullOrBlank()) {
                            Text(duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySelectionRow(platformKey: String, title: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val platform = Platform.fromApiValue(platformKey)
        if (platform != null) {
            Icon(platformIcon(platform), contentDescription = null, tint = platformColor(platform), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

// ── Slot por plataforma ───────────────────────────────────────────────────

// Tarjeta por plataforma: cadencia como encabezado, siguiente video con
// miniatura, descarte como acción de texto en rojo, último publicado
// expandible.
@Composable
private fun CalendarSlotRow(slot: CalendarSlot, onDiscardNext: () -> Unit) {
    var showingLastPublished by remember(slot.platform) { mutableStateOf(false) }
    val platform = Platform.fromApiValue(slot.platform)
    val color = platform?.let(::platformColor) ?: MaterialTheme.colorScheme.primary
    val scheme = MaterialTheme.colorScheme

    SwissCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (platform != null) {
                Icon(platformIcon(platform), contentDescription = platformShortLabel(platform), tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    platformFullLabel(slot.platform),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
            }
            SwissLabel("Cada ${slot.intervalDays} días")
        }

        slot.nextVideo?.let { next ->
            SwissLabel("Siguiente video", color = color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NextVideoThumbnail(slot)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(next.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                    if (next.duration.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.PlayCircleOutline,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                next.duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                TextButton(onClick = onDiscardNext) {
                    Text("Descartar", color = scheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showingLastPublished = !showingLastPublished }
                .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                if (showingLastPublished) "Ocultar último publicado" else "Ver último publicado",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(
                if (showingLastPublished) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        AnimatedVisibility(
            visible = showingLastPublished,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LastPublishedThumbnail(slot)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        slot.lastPublishedTitle.ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                    )
                    publishedLabel(slot.lastPublishedDate)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// Local primero (más barato, ya en disco); Biblioteca remota si el archivo
// solo existe en otro dispositivo; ícono genérico si no hay ninguna de las dos.
@Composable
private fun NextVideoThumbnail(slot: CalendarSlot) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 64.dp)
            .clip(RoundedCornerShape(Swiss.cardSmall))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val model = slot.localThumbnailPath?.let { File(it) } ?: slot.remoteThumbnailUrl
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// A diferencia de nextVideo, la central no manda remoteLibraryVideoId para el
// último publicado -- si ya no vive en este dispositivo, no hay miniatura
// posible todavía (mismo límite que lastPublishedThumbnail en iOS).
@Composable
private fun LastPublishedThumbnail(slot: CalendarSlot) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 60.dp)
            .clip(RoundedCornerShape(Swiss.cardSmall))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val path = slot.lastPublishedLocalThumbnailPath
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
