package com.esseanalytics.android.feature.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esseanalytics.android.core.designsystem.component.PlaceholderScreen
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.designsystem.theme.InstagramPurple
import com.esseanalytics.android.core.designsystem.theme.TiktokPink
import com.esseanalytics.android.core.designsystem.theme.UrgencyPast
import com.esseanalytics.android.core.designsystem.theme.UrgencySoon
import com.esseanalytics.android.core.designsystem.theme.UrgencyToday
import com.esseanalytics.android.core.designsystem.theme.YoutubeRed
import com.esseanalytics.android.core.model.Platform
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Fase 1 (básico): cadencia de publicación por plataforma -- último
// publicado, cada cuántos días toca, y qué archivo local sigue en la cola.
// Mismo dato que ya expone la central (GET /api/sync/calendar-config), que
// alimenta el Calendario real de desktop (PublishingQueue.tsx) -- acá se
// muestra en modo lectura, sin drag-drop ni edición todavía (Fase 2).
@Composable
fun CalendarScreen(modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

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

        is CalendarUiState.Success -> if (current.slots.isEmpty()) {
            PlaceholderScreen(
                title = "Todavía no hay nada programado",
                note = "Publicá al menos un video en cada plataforma para que arranque la cadencia.",
                icon = Icons.Outlined.CalendarMonth,
                modifier = modifier,
            )
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current.slots, key = { it.platform }) { slot -> CalendarSlotCard(slot) }
            }
        }
    }
}

// Mismo lenguaje visual que ya usan los badges circulares de Biblioteca/
// Estadísticas (PlatformBadge/PlatformStatsRow) -- iniciales de color sobre
// fondo al 15% de alpha, para que la identidad de cada red se vea igual en
// toda la app. slot.platform es el String crudo que devuelve la central
// (GET /api/sync/calendar-config); si no matchea ningún Platform conocido,
// se cae a texto plano en vez de romper la tarjeta.
@Composable
private fun CalendarSlotCard(slot: CalendarSlot) {
    val platform = Platform.fromApiValue(slot.platform)
    val color = platform?.let(::platformColor) ?: MaterialTheme.colorScheme.primary

    // elevation = 0.dp a propósito: la elevación por defecto de Card mezcla
    // un poco del color primario (rojo/ámbar) sobre la superficie -- se ve
    // como una tarjeta más clara y tibia de lo que pide el tema (--card
    // plano en theme.css, sin ese tinte). Mismo fix en todas las Card() de
    // la app (Biblioteca, Estadísticas, Sync, Usuarios, Subir).
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = platform?.let(::platformIcon)
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = platformShortLabel(platform),
                            tint = color,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            platform?.let(::platformShortLabel) ?: "?",
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                ) {
                    Text(
                        platform?.let(::platformFullLabel) ?: slot.platform,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Cada ${slot.intervalDays} días",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Último publicado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${slot.lastPublishedTitle} · ${slot.lastPublishedDate}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }

            // "Cada N días" (arriba) es la cadencia, no una fecha -- acá se
            // resuelve cuándo le toca en concreto (lastPublishedDate +
            // intervalDays), mismo cálculo y mismas 3 etiquetas relativas que
            // daysLabel() en PublishingQueue.tsx (desktop).
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Próxima publicación",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (slot.nextDate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UrgencyPill(slot.nextDate)
                        Text(
                            formatNextDate(slot.nextDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else {
                    Text(
                        "Sin fecha base todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sigue",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    slot.nextFileName ?: "Sin definir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

// Mismo criterio que UrgencyPill en PublishingQueue.tsx (desktop): rojo si ya
// venció, naranja si es hoy, ámbar si es mañana, neutro (sin pill) si falta
// más de un día -- así la urgencia se lee de un vistazo, no solo con la fecha.
@Composable
private fun UrgencyPill(nextDate: LocalDate) {
    val days = relDays(nextDate)
    val color = when {
        days < 0 -> UrgencyPast
        days == 0L -> UrgencyToday
        days == 1L -> UrgencySoon
        else -> null
    }
    if (color == null) {
        Text(
            daysLabel(days),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (days < 0) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            daysLabel(days),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun relDays(next: LocalDate): Long = ChronoUnit.DAYS.between(LocalDate.now(), next)

private fun daysLabel(days: Long): String = when {
    days < 0 -> "Venció hace ${-days} día${if (-days != 1L) "s" else ""}"
    days == 0L -> "Hoy"
    days == 1L -> "Mañana"
    else -> "En $days días"
}

private val nextDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun formatNextDate(date: LocalDate): String = nextDateFormatter.format(date)

private fun platformColor(platform: Platform): Color = when (platform) {
    Platform.YOUTUBE -> YoutubeRed
    Platform.INSTAGRAM -> InstagramPurple
    Platform.TIKTOK -> TiktokPink
    Platform.FACEBOOK -> Color.Gray
}

private fun platformShortLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
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
