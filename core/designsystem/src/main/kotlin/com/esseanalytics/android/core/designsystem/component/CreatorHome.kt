package com.esseanalytics.android.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esseanalytics.android.core.designsystem.theme.CreatorDisplayFont

enum class CreatorMetric(val label: String, val unit: String, val icon: ImageVector) {
    VIEWS("Vistas", "vistas", Icons.Outlined.Visibility),
    LIKES("Likes", "likes", Icons.Outlined.FavoriteBorder),
    COMMENTS("Comentarios", "comentarios", Icons.Outlined.ChatBubbleOutline),
}

val CreatorSerif: FontFamily = com.esseanalytics.android.core.designsystem.theme.CreatorBodyFont

private val creatorKeyShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 11.dp, bottomEnd = 11.dp)

/** Valores reales por video, ordenados del más antiguo al más reciente. */
data class CreatorChartBar(
    val value: Long,
    val label: String,
    val id: String = label,
    val valueLabel: String = value.toString(),
    val thumbnailUrl: String? = null,
    val dateLabel: String? = null,
)

@Composable
fun CreatorHomeBrand(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.rotate(-6f).size(36.dp).clip(creatorKeyShape).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("e", color = colors.onPrimary, fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontFamily = CreatorSerif)
        }
        Spacer(Modifier.width(9.dp))
        Text("esse", color = colors.onBackground, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, fontFamily = CreatorSerif)
        Text(".", color = colors.primary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, fontFamily = CreatorSerif)
    }
}

@Composable
fun CreatorFloatingHomeActions(
    username: String,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    showUsername: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val name = username.trim().ifBlank { "Perfil" }
    val floatingSurface = colors.surface.copy(alpha = 0.96f)
    val floatingBorder = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.9f))
    val notificationInteractions = remember { MutableInteractionSource() }
    val profileInteractions = remember { MutableInteractionSource() }
    val notificationPressed by notificationInteractions.collectIsPressedAsState()
    val profilePressed by profileInteractions.collectIsPressedAsState()
    val notificationScale by animateFloatAsState(if (notificationPressed) 0.94f else 1f, label = "floatingNotificationPress")
    val profileScale by animateFloatAsState(if (profilePressed) 0.96f else 1f, label = "floatingProfilePress")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onNotificationsClick,
            interactionSource = notificationInteractions,
            modifier = Modifier.size(48.dp).graphicsLayer { scaleX = notificationScale; scaleY = notificationScale },
            shape = CircleShape,
            color = floatingSurface,
            border = floatingBorder,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.NotificationsNone, "Notificaciones", Modifier.size(21.dp), tint = colors.onSurfaceVariant)
            }
        }
        Surface(
            onClick = onProfileClick,
            interactionSource = profileInteractions,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                .graphicsLayer { scaleX = profileScale; scaleY = profileScale }
                .semantics { contentDescription = "Abrir perfil de $name" },
            shape = CircleShape,
            color = floatingSurface,
            contentColor = colors.onSurface,
            border = floatingBorder,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = if (showUsername) 14.dp else 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(username.trim().firstOrNull()?.uppercase() ?: "E", color = colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (showUsername) {
                    Spacer(Modifier.width(8.dp))
                    Text(name, Modifier.widthIn(max = 72.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun CreatorIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "iconPress")
    Surface(
        onClick = onClick,
        interactionSource = interactions,
        modifier = modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = creatorKeyShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
fun CreatorMetricCard(
    selectedMetric: CreatorMetric,
    onMetricSelected: (CreatorMetric) -> Unit,
    value: String,
    rangeLabel: String,
    bars: List<CreatorChartBar>,
    selectedBarId: String? = null,
    onBarSelected: (String) -> Unit = {},
    thumbnail: @Composable (CreatorChartBar, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val cardGradient = listOf(colors.surfaceVariant, colors.surface)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 28.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.22f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.background(Brush.linearGradient(cardGradient))
                .drawWithCache {
                    onDrawBehind {
                        drawCircle(colors.primary.copy(alpha = 0.06f), radius = 100.dp.toPx(), center = Offset(size.width + 10.dp.toPx(), 10.dp.toPx()))
                        drawCircle(colors.primary.copy(alpha = 0.04f), radius = 135.dp.toPx(), center = Offset(size.width + 10.dp.toPx(), 10.dp.toPx()))
                    }
                }.padding(start = CreatorVideoStyle.cardStartPadding, end = CreatorVideoStyle.cardEndPadding, top = CreatorVideoStyle.cardVerticalPadding, bottom = CreatorVideoStyle.cardVerticalPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rangeLabel, modifier = Modifier.weight(1f).padding(end = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                CreatorMetricSegmentedControl(selectedMetric, onMetricSelected)
            }
            Spacer(Modifier.height(4.dp))
            Crossfade(targetState = selectedMetric to value, animationSpec = tween(180), label = "metricSummary") { (metric, displayedValue) ->
                Column {
                    Text(displayedValue, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, lineHeight = 48.sp, fontFamily = CreatorSerif)
                    Text(metric.label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            CreatorVideoMetricChart(bars, selectedMetric, selectedBarId, onBarSelected, thumbnail, Modifier.padding(top = 4.dp), plotHeight = 80.dp)
        }
    }
}

@Composable
fun CreatorMetricSegmentedControl(
    selected: CreatorMetric,
    onSelect: (CreatorMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.selectableGroup().drawWithCache {
            val railHeight = 38.dp.toPx()
            onDrawBehind {
                drawRoundRect(
                    colors.surface.copy(alpha = 0.5f),
                    topLeft = Offset(0f, (size.height - railHeight) / 2f),
                    size = Size(size.width, railHeight), cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
        },
    ) {
        CreatorMetric.entries.forEach { metric ->
            val active = metric == selected
            val background by animateColorAsState(if (active) colors.primary else Color.Transparent, label = "metricBackground")
            val foreground by animateColorAsState(if (active) colors.onPrimary else colors.onSurfaceVariant, label = "metricForeground")
            // El control visible es pequeño; el área de pulsación sigue siendo de 48 dp.
            Box(
                modifier = Modifier.size(48.dp)
                    .selectable(selected = active, onClick = { onSelect(metric) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = background,
                    contentColor = foreground,
                    shadowElevation = if (active) 2.dp else 0.dp,
                    border = if (active) BorderStroke(1.dp, colors.primary.copy(alpha = 0.2f)) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(metric.icon, metric.label, Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun CreatorVideoListItem(
    title: String,
    value: String,
    metricLabel: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    thumbnail: @Composable (Modifier) -> Unit,
    metadata: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "videoPress")
    val background by animateColorAsState(if (highlighted) colors.primaryContainer.copy(alpha = 0.75f) else colors.surface, label = "videoBackground")
    val border by animateColorAsState(if (highlighted) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.7f), label = "videoBorder")
    Surface(
        onClick = onClick,
        interactionSource = interactions,
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { selected = highlighted },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 20.dp),
        color = background,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, border),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(CreatorVideoStyle.listThumbnailSize).clip(CreatorVideoStyle.listThumbnailShape)) {
                thumbnail(Modifier.fillMaxSize())
                if (highlighted) CreatorVideoSelectionMark(Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                metadata()
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(metricLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Box {
                IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.MoreVert, "Más opciones de $title", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                menu()
            }
        }
    }
}

@Composable
fun CreatorPlatformBadge(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(14.dp), tint = color) }
}

data class CreatorNavItem(val route: String, val label: String, val icon: ImageVector)

data class CreatorCommentPlatform(val label: String, val value: String, val icon: ImageVector, val color: Color)

@Composable
fun CreatorCommentsSummary(
    total: String,
    scope: String,
    platforms: List<CreatorCommentPlatform>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comentarios", Modifier.weight(1f), fontFamily = CreatorSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.background(colors.primaryContainer, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = colors.onPrimaryContainer)
                Text(total, color = colors.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.15f)),
        ) {
            Column(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha = 0.3f), colors.surface)))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(scope, color = colors.onSurfaceVariant, fontSize = 12.sp)
                if (platforms.isEmpty()) {
                    Text("Los comentarios de tus videos aparecerán en este resumen.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                }
                platforms.forEach { platform ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        CreatorPlatformBadge(platform.icon, platform.label, platform.color)
                        Text(platform.label, Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp)
                        Text(platform.value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorBottomNav(
    items: List<CreatorNavItem>,
    selectedRoute: String?,
    createRoute: String,
    moreRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val shellGradient = listOf(colors.surface.copy(alpha = .94f), colors.surfaceVariant.copy(alpha = .90f))
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(26.dp),
            color = colors.surface.copy(alpha = .92f),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)),
        ) {
            Row(Modifier.background(Brush.verticalGradient(shellGradient)).padding(vertical = 5.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { item ->
                    CreatorNavigationKey(
                        item, item.route == selectedRoute, item.route == createRoute,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                    )
                }
            }
        }
        CreatorNavigationKey(
            CreatorNavItem(moreRoute, "Más", Icons.Outlined.MoreHoriz), selectedRoute == moreRoute, false,
            onClick = { onNavigate(moreRoute) }, modifier = Modifier.width(48.dp), standalone = true,
        )
    }
}

@Composable
private fun CreatorNavigationKey(
    item: CreatorNavItem,
    selected: Boolean,
    isCreate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    standalone: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "navigationPress")
    val foreground by animateColorAsState(
        when { isCreate -> colors.onPrimary; selected -> colors.onPrimaryContainer; else -> colors.onSurfaceVariant },
        label = "navigationLight",
    )
    val start by animateColorAsState(
        when { isCreate -> colors.primary
            selected -> colors.primaryContainer; standalone -> colors.surface; else -> Color.Transparent },
        label = "navigationGradientStart",
    )
    val end by animateColorAsState(
        when { isCreate -> colors.primary
            selected -> colors.primary.copy(alpha = 0.22f); standalone -> colors.surfaceVariant; else -> Color.Transparent },
        label = "navigationGradientEnd",
    )
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .selectable(selected, interactions, indication = null, role = Role.Tab, onClick = onClick),
        shape = creatorKeyShape,
        color = Color.Transparent,
        shadowElevation = if (isCreate) 4.dp else if (selected || standalone) 2.dp else 0.dp,
        border = if (isCreate || selected || standalone) BorderStroke(1.dp, colors.primary.copy(alpha = 0.2f)) else null,
    ) {
        Box(
            Modifier.height(if (isCreate) 56.dp else 48.dp).fillMaxWidth()
                .background(Brush.linearGradient(listOf(start, end)))
                .drawWithCache {
                    onDrawBehind {
                        if (isCreate || selected) drawCircle(
                            Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.09f else 0.2f), Color.Transparent),
                                center = Offset(size.width * 0.5f, 0f), radius = size.width.coerceAtLeast(1f)),
                            radius = size.width, center = Offset(size.width * 0.5f, 0f),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isCreate) Icons.Outlined.Add else item.icon, item.label, Modifier.size(if (isCreate) 29.dp else 23.dp), tint = foreground)
        }
    }
}
