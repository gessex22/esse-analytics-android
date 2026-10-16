package com.esseanalytics.android.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop

internal data class MoreStudioItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun MoreStudioContent(items: List<MoreStudioItem>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize()) {
        CreatorStudioBackdrop()
        LazyColumn(
            modifier = Modifier.statusBarsPadding().fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                Text(
                    "Más",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
                )
            }
            item(key = "destinations") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column {
                        items.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 64.dp)
                                    .clickable(role = Role.Button, onClick = item.onClick)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(item.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                                Text(item.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun moreStudioPreviewItems(isOwner: Boolean, canUseCloudStorage: Boolean): List<MoreStudioItem> = buildList {
    add(MoreStudioItem("Sincronización", Icons.Outlined.Sync) {})
    add(MoreStudioItem("Estadísticas", Icons.Outlined.QueryStats) {})
    add(MoreStudioItem("Historial", Icons.Outlined.History) {})
    if (isOwner) add(MoreStudioItem("Usuarios", Icons.Outlined.PeopleOutline) {})
    if (canUseCloudStorage) add(MoreStudioItem("Biblioteca remota", Icons.Outlined.CloudQueue) {})
    add(MoreStudioItem("Gemas", Icons.Outlined.Diamond) {})
    add(MoreStudioItem("Ajustes", Icons.Outlined.Settings) {})
}
