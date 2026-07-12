package com.esseanalytics.android.feature.gems

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Las gemas (esse_transcrip, esse_remote, esse_maiden) son binarios nativos de
// Windows que la PC lanza como proceso hijo — no pueden correr en Android bajo
// ningún escenario por el sandboxing del SO (decisión ya tomada, no es un TODO).
// Se muestran así en vez de ocultarse, para dejar clara la expectativa.
private data class GemInfo(val name: String, val description: String)

private val gems = listOf(
    GemInfo("esse_transcrip", "Transcripción automática de tus videos."),
    GemInfo("esse_remote", "Acceso remoto a tu PC desde otros dispositivos."),
    GemInfo("esse_maiden", "Herramientas auxiliares de mantenimiento."),
)

@Composable
fun GemsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(gems) { gem ->
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(gem.name, style = MaterialTheme.typography.titleMedium)
                    Text(gem.description, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            "Disponible en la versión de Windows — próximamente en Android",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
