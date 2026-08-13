package com.esseanalytics.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Feature B (ver UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): cuando un refresh
// (pull-to-refresh, cambio de filtro, trigger externo) falla pero YA había
// datos en pantalla, esto se suma ARRIBA del contenido existente en vez de
// reemplazarlo -- a diferencia de un estado de error de pantalla completa,
// que solo se usa cuando no hay ningún dato previo que mostrar. Compartido
// por Dashboard/Calendar/Stats -- mismo criterio en los 3, y espejo de
// RefreshErrorBanner.swift en iOS.
private val warningColor = Color(0xFFF97316) // mismo naranja que UrgencyPill "hoy" (iOS/Android)

@Composable
fun RefreshErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(warningColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = warningColor, modifier = Modifier)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = warningColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
