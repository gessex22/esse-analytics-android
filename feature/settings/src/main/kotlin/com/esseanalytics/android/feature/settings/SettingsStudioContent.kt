package com.esseanalytics.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.icon.InstagramLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.icon.TiktokLogo
import com.esseanalytics.android.core.designsystem.icon.YoutubeLogo
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.model.WorkflowMode

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsStudioContent(
    username: String,
    workflowMode: WorkflowMode,
    wifiOnly: Boolean,
    connections: Map<Platform, Boolean>,
    serverIsCustom: Boolean,
    connecting: Platform?,
    connectionError: String?,
    onBack: () -> Unit,
    onWorkflowMode: (WorkflowMode) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onConnection: (Platform) -> Unit,
    onLogout: () -> Unit,
    serverContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpandedSections: Set<String> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val serverStateHolder = rememberSaveableStateHolder()
    Box(modifier.fillMaxSize()) {
        CreatorStudioBackdrop()
        LazyColumn(
            modifier = Modifier.statusBarsPadding().widthIn(max = 640.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Ajustes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(username.ifBlank { "Tu cuenta" }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            item(key = "connections") {
                SettingsSection("Tus redes") {
                    SettingsSurface {
                        Platform.publishable.forEachIndexed { index, platform ->
                            if (index > 0) SettingsDivider()
                            ConnectionRow(platform, connections[platform], connecting, onConnection)
                        }
                    }
                    connectionError?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error,
                            modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
            item(key = "publishing") {
                SettingsSection("Publicación") {
                    SettingsSurface {
                        SettingsDisclosure(
                            title = "Modo de publicación",
                            summary = if (workflowMode == WorkflowMode.SIMPLE) "Simple · una red por video" else "Avanzado · cada red por separado",
                            initiallyExpanded = "workflow" in initiallyExpandedSections,
                        ) {
                            Column(Modifier.selectableGroup()) {
                                WorkflowOption("Simple", "Publicar en una red descarta las otras plataformas pendientes.", workflowMode == WorkflowMode.SIMPLE) { onWorkflowMode(WorkflowMode.SIMPLE) }
                                WorkflowOption("Avanzado", "Elegí qué publicar en cada plataforma por separado.", workflowMode == WorkflowMode.AVANZADO) { onWorkflowMode(WorkflowMode.AVANZADO) }
                            }
                        }
                        SettingsDivider()
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = wifiOnly, role = Role.Switch, onValueChange = onWifiOnly).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.Wifi, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Solo por WiFi", style = MaterialTheme.typography.titleSmall)
                                Text("Evitá usar datos móviles al subir.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Switch(checked = wifiOnly, onCheckedChange = null)
                        }
                    }
                }
            }
            item(key = "preferences") {
                SettingsSection("Preferencias") {
                    SettingsSurface {
                        SettingsDisclosure(
                            title = "Servidor",
                            summary = if (serverIsCustom) "Conexión personalizada" else "Central de EsseAnalytics",
                            stateHolder = serverStateHolder,
                            initiallyExpanded = "server" in initiallyExpandedSections,
                        ) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { serverContent() }
                        }
                    }
                }
            }
            item(key = "logout") {
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Cerrar sesión")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
        content()
    }
}

@Composable
private fun SettingsSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConnectionRow(platform: Platform, connected: Boolean?, connecting: Platform?, onConnection: (Platform) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f
        val identity: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                    Icon(when (platform) {
                        Platform.YOUTUBE -> PlatformIcons.YoutubeLogo
                        Platform.INSTAGRAM -> PlatformIcons.InstagramLogo
                        else -> PlatformIcons.TiktokLogo
                    }, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(platform.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(when (connected) { true -> "Conectada"; false -> "Sin conectar"; null -> "Consultando…" }, style = MaterialTheme.typography.bodySmall, color = if (connected == true) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
        val action: @Composable () -> Unit = {
            TextButton(
                onClick = { onConnection(platform) }, enabled = connected != null && connecting == null,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                if (connecting == platform) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (connecting == platform) "Abriendo…" else if (connected == true) "Desconectar" else "Conectar")
            }
        }
        if (stacked) {
            Column {
                identity()
                Box(Modifier.align(Alignment.End)) { action() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { identity() }
                action()
            }
        }
    }
}

@Composable
private fun SettingsDisclosure(
    title: String,
    summary: String,
    stateHolder: SaveableStateHolder? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "settingsChevron")
    Surface(
        onClick = { expanded = !expanded }, color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
    ) {
        Row(Modifier.defaultMinSize(minHeight = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(24.dp).rotate(rotation))
        }
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(tween(180)) + fadeIn(tween(120)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(100)),
    ) {
        if (stateHolder != null) stateHolder.SaveableStateProvider("server") { content() } else content()
    }
}

@Composable
private fun WorkflowOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccentOption(label: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerLow, tween(180), label = "settingsAccent")
    Surface(
        color = background, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(swatch))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
        }
    }
}
