package com.esseanalytics.android.app

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.component.CreatorNavItem
import com.esseanalytics.android.core.designsystem.theme.DashboardTheme

/** Blur the recorded page behind the bar, leaving the controls crisp. */
@Composable
internal fun AppleDashboardNavigation(
    items: List<CreatorNavItem>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    backdrop: GraphicsLayer,
    backdropOrigin: Offset,
    modifier: Modifier = Modifier,
) {
    DashboardTheme(apple = true) {
        val colors = MaterialTheme.colorScheme
        var origin by remember { mutableStateOf(Offset.Zero) }
        Box(modifier.fillMaxWidth().clipToBounds().onGloballyPositioned { origin = it.positionInRoot() }) {
            Canvas(Modifier.matchParentSize().graphicsLayer {
                if (Build.VERSION.SDK_INT >= 31) renderEffect = BlurEffect(24.dp.toPx(), 24.dp.toPx(), TileMode.Clamp)
            }) {
                translate(backdropOrigin.x - origin.x, backdropOrigin.y - origin.y) { drawLayer(backdrop) }
            }
            Row(
                Modifier.fillMaxWidth().background(colors.surface.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.86f else 0.97f))
                    .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    Box(Modifier.weight(1f).height(48.dp).selectable(item.route == selectedRoute, role = Role.Tab, onClick = { onNavigate(item.route) }), contentAlignment = Alignment.Center) {
                        Icon(item.icon, item.label, Modifier.size(25.dp), tint = if (item.route == selectedRoute) colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
