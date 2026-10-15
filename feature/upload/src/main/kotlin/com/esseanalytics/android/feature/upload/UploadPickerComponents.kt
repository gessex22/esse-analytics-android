package com.esseanalytics.android.feature.upload

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.esseanalytics.android.core.model.Platform

internal enum class UploadPickerSource(val label: String) {
    CLOUD("Nube"), PC("PC local"), IMPORTED("Importados"),
}

@Composable
internal fun UploadPickerSources(
    sources: List<UploadPickerSource>,
    selected: UploadPickerSource,
    enabled: Boolean,
    onSelect: (UploadPickerSource) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (sources.size == 1) {
        Text("Tus videos", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
        return
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(scheme.surface.copy(alpha = .7f)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sources.forEach { source ->
            val active = selected == source
            val background by animateColorAsState(
                targetValue = if (active) scheme.primaryContainer else Color.Transparent,
                animationSpec = tween(180),
                label = "pickerSourceBackground",
            )
            val foreground by animateColorAsState(
                targetValue = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "pickerSourceForeground",
            )
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = { onSelect(source) }),
                shape = RoundedCornerShape(14.dp),
                color = background,
                contentColor = foreground,
            ) {
                Box(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(source.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** One quiet status line; the complete platform state remains available to accessibility. */
@Composable
internal fun UploadPickerVideoRow(
    fileName: String,
    thumbnail: Any?,
    published: List<Platform>,
    discarded: List<Platform>,
    nextFor: List<Platform>,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    importing: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val pending = Platform.publishable.filter { it !in published && it !in discarded }
    val nextPending = nextFor.filter { it in pending }
    val status = when {
        importing -> "Importando video…"
        nextPending.isNotEmpty() -> "Próximo · ${nextPending.joinToString(" · ") { pickerPlatformLabel(it) }}"
        pending.isNotEmpty() -> "Pendiente · ${pending.joinToString(" · ") { pickerPlatformLabel(it) }}"
        else -> "Sin destinos pendientes"
    }
    val platformDescription = (Platform.publishable + published).distinct().joinToString { platform ->
        "${platform.displayName}: ${when (platform) {
            in published -> "publicado"
            in discarded -> "descartado"
            else -> "pendiente"
        }}"
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                if (importing) {
                    Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = scheme.onPrimary, strokeWidth = 2.dp)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    fileName.replace(Regex("\\.(mp4|mov|m4v|mkv|webm|avi)$", RegexOption.IGNORE_CASE), ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nextPending.isNotEmpty()) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "$status. $platformDescription" },
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant.copy(alpha = .6f), modifier = Modifier.size(20.dp))
        }
    }
}

private fun pickerPlatformLabel(platform: Platform): String = when (platform) {
    Platform.YOUTUBE -> "YT"
    Platform.INSTAGRAM -> "IG"
    Platform.TIKTOK -> "TT"
    Platform.FACEBOOK -> "FB"
}
