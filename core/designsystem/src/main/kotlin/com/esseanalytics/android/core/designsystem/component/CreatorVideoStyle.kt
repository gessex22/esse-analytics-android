package com.esseanalytics.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** Geometría y cifras comunes a Inicio y Datos. */
object CreatorVideoStyle {
    val screenHorizontalPadding = 8.dp
    val cardStartPadding = 12.dp
    val cardEndPadding = 8.dp
    val cardVerticalPadding = 4.dp
    val listThumbnailSize = 64.dp
    val listThumbnailShape = RoundedCornerShape(13.dp)
    val chartThumbnailSize = 36.dp
    val chartThumbnailShape = RoundedCornerShape(10.dp)
}

fun formatCreatorMetric(value: Long): String {
    val decimal = DecimalFormat("0.#", DecimalFormatSymbols(Locale("es")))
    return when {
        value >= 1_000_000 -> "${decimal.format(value / 1_000_000.0)}M"
        value >= 1_000 -> "${decimal.format(value / 1_000.0)}k"
        else -> NumberFormat.getIntegerInstance(Locale("es")).format(value)
    }
}

@Composable
fun CreatorVideoSelectionMark(modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}
