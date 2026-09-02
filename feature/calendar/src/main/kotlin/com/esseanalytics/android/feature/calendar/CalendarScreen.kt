package com.esseanalytics.android.feature.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.component.RefreshErrorBanner
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.UrgencySoon
import com.esseanalytics.android.core.designsystem.theme.UrgencyToday
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

// Rediseño 2026-09-01 (paridad visual con CalendarView.swift, iOS): agenda
// semanal enfocada -- strip de "esta semana" con selección de día, secciones
// Hoy/Mañana (agrupando lo ya publicado + lo que sigue en cola por
// plataforma), descartar el próximo de una plataforma puntual. Antes Android
// era una lista plana de tarjetas por plataforma, sin agrupar por día ni
// poder descartar (Fase 1 del plan original, solo lectura). El dato
// (GET /api/sync/calendar-config + historial) es el mismo de siempre --
// esto es enteramente un cambio de presentación + la acción de descarte que
// faltaba.
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
            val todaySlots = slots.filter { it.nextDate != null && daysFromToday(it.nextDate) <= 0 }
            val tomorrowSlots = slots.filter { it.nextDate != null && daysFromToday(it.nextDate) == 1L }
            val todayPublished = history.filter { it.publishedLocalDate()?.let(::daysFromToday) == 0L }
            val tomorrowPublished = history.filter { it.publishedLocalDate()?.let(::daysFromToday) == 1L }
            val hasToday = todaySlots.isNotEmpty() || todayPublished.isNotEmpty()
            val hasTomorrow = tomorrowSlots.isNotEmpty() || tomorrowPublished.isNotEmpty()

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                // bottom extra: FloatingBottomNavigation (EsseAnalyticsNavHost) es
                // un overlay real, no un bottomBar de Scaffold -- no reserva
                // espacio propio, así que sin este margen extra la última tarjeta
                // queda scrolleable hasta quedar tapada detrás de la cápsula
                // flotante. Mismo criterio que PublishForm en UploadScreen.kt.
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + 80.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Spinner de refresh: en AppTopBar (izquierda del avatar),
                // no acá -- ver RefreshActivityTracker.
                current.refreshError?.let { message ->
                    item(key = "refreshError") { RefreshErrorBanner(message) }
                }

                item(key = "weekFocus") { CalendarWeekFocusCard(slots = slots, history = history) }

                if (!hasToday && !hasTomorrow) {
                    item(key = "emptyKinds") {
                        Text(
                            "Nada programado para hoy o mañana.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (hasToday) {
                    item(key = "todayHeader") {
                        CalendarSectionHeader(
                            title = "Hoy",
                            subtitle = fullDate(LocalDate.now()),
                            icon = Icons.Outlined.WbSunny,
                            tint = UrgencyToday,
                            count = todaySlots.size + todayPublished.size,
                        )
                    }
                    items(todayPublished, key = { "pub-today-${it.id}" }) { item -> CalendarPublishedRow(item) }
                    items(todaySlots, key = { "slot-today-${it.platform}" }) { slot ->
                        CalendarSlotCard(slot = slot, onDiscardNext = { pendingDiscard = slot })
                    }
                }

                if (hasTomorrow) {
                    item(key = "tomorrowHeader") {
                        CalendarSectionHeader(
                            title = "Mañana",
                            subtitle = fullDate(LocalDate.now().plusDays(1)),
                            icon = Icons.Outlined.WbTwilight,
                            tint = UrgencySoon,
                            count = tomorrowSlots.size + tomorrowPublished.size,
                        )
                    }
                    items(tomorrowPublished, key = { "pub-tomorrow-${it.id}" }) { item -> CalendarPublishedRow(item) }
                    items(tomorrowSlots, key = { "slot-tomorrow-${it.platform}" }) { slot ->
                        CalendarSlotCard(slot = slot, onDiscardNext = { pendingDiscard = slot })
                    }
                }
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

// ── Fecha ────────────────────────────────────────────────────────────────

private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX"))
private val rangeEndFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("es", "MX"))
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

private fun weekDates(offsetWeeks: Long = 0): List<LocalDate> {
    val start = startOfWeek(LocalDate.now()).plusWeeks(offsetWeeks)
    return (0..6).map { start.plusDays(it.toLong()) }
}

private fun rangeLabel(week: List<LocalDate>): String {
    val first = week.firstOrNull() ?: return ""
    val last = week.lastOrNull() ?: return ""
    return "${first.dayOfMonth}–${rangeEndFormatter.format(last)}"
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

// ── Cabecera de sección (Hoy/Mañana) ────────────────────────────────────────

@Composable
private fun CalendarSectionHeader(title: String, subtitle: String, icon: ImageVector, tint: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

// Fila compacta para un video ya publicado hoy/mañana -- mismo lenguaje
// visual que la fila de selección de día (ícono + título + check verde).
@Composable
private fun CalendarPublishedRow(item: UploadHistoryItemDto) {
    val title = item.title?.takeIf { it.isNotBlank() } ?: item.fileName ?: "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val platform = Platform.fromApiValue(item.platform)
        if (platform != null) {
            Icon(platformIcon(platform), contentDescription = null, tint = platformColor(platform), modifier = Modifier.size(16.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Icon(Icons.Filled.CheckCircle, contentDescription = "Publicado", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
    }
}

// ── Semana en foco ──────────────────────────────────────────────────────────

@Composable
private fun CalendarWeekFocusCard(slots: List<CalendarSlot>, history: List<UploadHistoryItemDto>) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val previousWeek = remember { weekDates(-1) }
    val currentWeek = remember { weekDates(0) }
    val nextWeek = remember { weekDates(1) }

    fun dotPlatformsOn(date: LocalDate): List<String> {
        val scheduled = slots.mapNotNull { if (it.nextDate == date) it.platform else null }
        val published = history.mapNotNull { if (it.publishedLocalDate() == date) it.platform else null }
        val seen = LinkedHashSet<String>()
        (scheduled + published).forEach { seen.add(it) }
        return seen.toList()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Esta semana", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "En foco · ${rangeLabel(currentWeek)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            CalendarGhostWeekRow(previousWeek)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            CalendarGhostWeekRow(nextWeek)

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
}

@Composable
private fun CalendarGhostWeekRow(dates: List<LocalDate>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dates.forEach { date ->
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(date: LocalDate, platforms: List<String>, isSelected: Boolean, modifier: Modifier = Modifier) {
    val days = daysFromToday(date)
    val isToday = days == 0L
    val isTomorrow = days == 1L
    val shortWeekday = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "MX")).uppercase(Locale("es", "MX"))

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    isTomorrow -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            shortWeekday,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            platforms.forEach { key ->
                val color = Platform.fromApiValue(key)?.let(::platformColor) ?: MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Text(
            if (isToday) "HOY" else if (isTomorrow) "MAÑ" else " ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// Detalle del día tocado en el strip -- separa lo YA publicado (historial
// real, con caption real) de lo programado para ese día (todavía no pasó).
@Composable
private fun CalendarDaySelectionPanel(date: LocalDate, scheduledSlots: List<CalendarSlot>, publishedItems: List<UploadHistoryItemDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(modifier = Modifier.fillMaxWidth())
        Text(fullDate(date), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

        if (publishedItems.isEmpty() && scheduledSlots.isEmpty()) {
            Text(
                "Nada programado ni publicado este día.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            publishedItems.forEach { item ->
                DaySelectionRow(
                    platformKey = item.platform,
                    title = item.title?.takeIf { it.isNotBlank() } ?: item.fileName ?: "—",
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Publicado", tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
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

@Composable
private fun DaySelectionRow(platformKey: String, title: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val platform = Platform.fromApiValue(platformKey)
        if (platform != null) {
            Icon(platformIcon(platform), contentDescription = null, tint = platformColor(platform), modifier = Modifier.size(16.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        trailing()
    }
}

// ── Tarjeta por plataforma (dentro de Hoy/Mañana) ───────────────────────────

// Mismo lenguaje visual que ya usan los badges circulares de Biblioteca/
// Estadísticas -- iniciales de color sobre fondo al 15% de alpha. Colapsado
// por default el "último publicado" -- el Hoy/Mañana ya lo dice el header de
// afuera (CalendarSectionHeader), acá adentro no hace falta repetir fecha,
// la tarjeta es sobre QUÉ se publica, no CUÁNDO.
@Composable
private fun CalendarSlotCard(slot: CalendarSlot, onDiscardNext: () -> Unit) {
    var showingLastPublished by remember(slot.platform) { mutableStateOf(false) }
    val platform = Platform.fromApiValue(slot.platform)
    val color = platform?.let(::platformColor) ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Cada ${slot.intervalDays} días",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (platform != null) {
                        Icon(platformIcon(platform), contentDescription = platformShortLabel(platform), tint = color, modifier = Modifier.size(20.dp))
                    }
                }

                slot.nextVideo?.let { next ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NextVideoThumbnail(slot)
                        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                "SIGUIENTE VIDEO",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(next.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                            if (next.duration.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        next.duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                        // Al lado del video que afecta, no como botón aparte al
                        // final de la tarjeta -- ahí quedaba sin relación visual.
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = "Descartar este próximo video",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(onClick = onDiscardNext),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showingLastPublished = !showingLastPublished },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Icon(
                        if (showingLastPublished) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (showingLastPublished) "Ocultar último publicado" else "Mostrar último publicado",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }

                AnimatedVisibility(
                    visible = showingLastPublished,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LastPublishedThumbnail(slot)
                        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                slot.lastPublishedTitle.ifBlank { "—" },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                            )
                            publishedLabel(slot.lastPublishedDate)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
            .size(width = 44.dp, height = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val model = slot.localThumbnailPath?.let { File(it) } ?: slot.remoteThumbnailUrl
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
            .size(width = 40.dp, height = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val path = slot.lastPublishedLocalThumbnailPath
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}
