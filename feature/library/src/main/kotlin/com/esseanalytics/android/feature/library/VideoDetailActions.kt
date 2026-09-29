package com.esseanalytics.android.feature.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailPrimaryAction(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun DetailSaveAction(
    idleText: String,
    subtitle: String,
    savedText: String,
    icon: ImageVector,
    status: VideoSaveStatus,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRun = enabled && status !is VideoSaveStatus.Running && status !is VideoSaveStatus.Saved
    OutlinedButton(
        onClick = onClick,
        enabled = canRun,
        modifier = modifier.heightIn(min = 68.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                text = if (status is VideoSaveStatus.Saved) savedText else idleText,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        when (status) {
            VideoSaveStatus.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            VideoSaveStatus.Saved -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Guardado",
                tint = MaterialTheme.colorScheme.primary,
            )
            is VideoSaveStatus.Failed -> Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = "Error al guardar",
                tint = MaterialTheme.colorScheme.error,
            )
            VideoSaveStatus.Idle -> Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
