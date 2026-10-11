package com.esseanalytics.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.theme.Swiss

/**
 * Bloque suizo: sin fondo — el aire y la hairline separan. Contenedor
 * neutro para el contenido de cada sección.
 */
@Composable
fun SwissCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Bloque suizo de acento: tarjeta tonal para el momento de acción.
 */
@Composable
fun SwissAccentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Swiss.card))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Hairline neutra — el separador suizo es gris, no de acento.
 */
@Composable
fun SwissHairline(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Swiss.hairline)
            .background(color),
    )
}

/**
 * Encabezado de pantalla suizo: titular semibold con tracking ajustado.
 */
@Composable
fun SwissScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = Swiss.titleTracking,
            ),
            color = scheme.onBackground,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        SwissHairline()
    }
}

/**
 * Título de sección suizo: número + titular semibold + hairline neutra.
 */
@Composable
fun SwissSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    index: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index != null) {
                Text(
                    index,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = Swiss.titleTracking,
                    ),
                    color = scheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) trailing()
        }
        Spacer(Modifier.height(8.dp))
        SwissHairline()
    }
}

/**
 * Métrica suiza: cifra semibold + etiqueta en mayúsculas con tracking.
 */
@Composable
fun SwissMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaUp: Boolean = true,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val valueStyle = if (value.length >= 5) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.headlineMedium
        }
        Text(
            value,
            style = valueStyle.copy(fontWeight = FontWeight.SemiBold),
            color = if (accent) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwissLabel(text = label, modifier = Modifier.weight(1f))
            if (delta != null) {
                SwissDeltaPill(delta = delta, up = deltaUp)
            }
        }
    }
}

/**
 * Píldora de delta suiza: nítida, 4dp.
 */
@Composable
fun SwissDeltaPill(
    delta: String,
    up: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (up) scheme.primary.copy(alpha = 0.14f) else scheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (if (up) "+" else "") + delta,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (up) scheme.primary else scheme.error,
            maxLines = 1,
        )
    }
}

/**
 * Botón suizo: nítido, semibold, sin cápsulas.
 */
@Composable
fun SwissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Swiss.card)
    val label = @Composable {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = Swiss.touchMin),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            content = { label() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = Swiss.touchMin),
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
            content = { label() },
        )
    }
}

/**
 * Chip suizo: texto sobrio con subrayado en el acento cuando está activo.
 */
@Composable
fun SwissChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = Swiss.touchMin)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(24.dp)
                .height(2.dp)
                .background(if (selected) scheme.primary else Color.Transparent),
        )
    }
}

/**
 * Botón de icono suizo: 48dp, sin adornos.
 */
@Composable
fun SwissIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(Swiss.touchMin)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * Etiqueta suiza: mayúsculas con tracking, para metadatos.
 */
@Composable
fun SwissLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = Swiss.labelTracking,
        ),
        color = color,
        modifier = modifier,
    )
}

/**
 * Fila de 2 bloques lado a lado.
 */
@Composable
fun SwissGridRow(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Swiss.gap),
    ) {
        Box(modifier = Modifier.weight(1f)) { left() }
        Box(modifier = Modifier.weight(1f)) { right() }
    }
}
