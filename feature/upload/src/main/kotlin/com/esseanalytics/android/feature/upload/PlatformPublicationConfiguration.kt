package com.esseanalytics.android.feature.upload

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.dto.TiktokCreatorInfoDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPublicationConfiguration(
    platform: Platform,
    options: PlatformPublicationOptions,
    onChange: (PlatformPublicationOptions) -> Unit,
    enabled: Boolean,
    creator: TiktokCreatorInfoDto? = null,
    creatorLoading: Boolean = false,
    creatorError: String? = null,
    onReloadCreator: () -> Unit = {},
) {
    var expanded by rememberSaveable(platform) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { expanded = true },
        enabled = enabled,
        color = scheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(publicationOptionsSummary(platform, options), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text("Ajustes", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "Configurar publicación de ${platform.displayName}", tint = scheme.primary, modifier = Modifier.size(18.dp))
        }
    }
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = scheme.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    platformIcon(platform)?.let { Icon(it, contentDescription = null, tint = platformColor(platform), modifier = Modifier.size(28.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(platform.displayName, fontFamily = CreatorSerif, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Opciones de publicación", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { expanded = false }) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar ajustes de ${platform.displayName}") }
                }
                PublicationSettingsContent(platform, options, onChange, enabled, creator, creatorLoading, creatorError, onReloadCreator)
                UploadStudioPrimaryButton("Listo", onClick = { expanded = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun PublicationSettingsContent(
    platform: Platform,
    options: PlatformPublicationOptions,
    onChange: (PlatformPublicationOptions) -> Unit,
    enabled: Boolean,
    creator: TiktokCreatorInfoDto? = null,
    creatorLoading: Boolean = false,
    creatorError: String? = null,
    onReloadCreator: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (platform) {
                Platform.YOUTUBE -> {
                    Text("Video · detalles y audiencia", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    PublicationChoice("Visibilidad", options.youtubePrivacy, listOf("public" to "Público", "unlisted" to "No listado", "private" to "Privado"), enabled) {
                        onChange(options.copy(youtubePrivacy = it))
                    }
                    PublicationChoice("Categoría de contenido", options.youtubeCategory, YOUTUBE_CATEGORIES, enabled) {
                        onChange(options.copy(youtubeCategory = it))
                    }
                    PublicationChoice("Audiencia", options.youtubeMadeForKids.toString(), listOf("false" to "No es contenido para niños", "true" to "Es contenido para niños"), enabled) {
                        onChange(options.copy(youtubeMadeForKids = it.toBoolean()))
                    }
                    PublicationCheck("Contenido generado o modificado con IA", options.youtubeSyntheticMedia, enabled) { onChange(options.copy(youtubeSyntheticMedia = it)) }
                    PublicationTextField("Etiquetas", "Separa las etiquetas con comas", options.youtubeTags, enabled) { onChange(options.copy(youtubeTags = it)) }
                }
                Platform.INSTAGRAM -> {
                    Text("Reel · opciones de contenido", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    PublicationTextField("Hashtags", "#contenido #creadores", options.instagramHashtags, enabled) { onChange(options.copy(instagramHashtags = it)) }
                    PublicationCheck("Mostrar también en el feed", options.instagramShareToFeed, enabled) { onChange(options.copy(instagramShareToFeed = it)) }
                }
                Platform.TIKTOK -> {
                    Text("Video · audiencia e interacciones", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (creatorLoading) Text("Cargando opciones de la cuenta…")
                    creatorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (creator == null && !creatorLoading) TextButton(onClick = onReloadCreator, enabled = enabled) { Text("Reintentar opciones de TikTok") }
                    creator?.let { Text("Cuenta: ${it.nickname.ifBlank { it.username }}", style = MaterialTheme.typography.bodySmall) }
                    PublicationChoice(
                        "Quién puede ver la publicación", options.tiktokPrivacy.orEmpty(),
                        creator?.privacyOptions.orEmpty().filter { !options.tiktokPaidBrand || it != "SELF_ONLY" }.map { it to tiktokPrivacyLabel(it) },
                        enabled && creator != null && !creatorLoading,
                    ) { onChange(options.copy(tiktokPrivacy = it)) }
                    PublicationCheck("Permitir comentarios", options.tiktokAllowComment && creator?.commentDisabled != true, enabled && creator != null && !creator.commentDisabled) { onChange(options.copy(tiktokAllowComment = it)) }
                    PublicationCheck("Permitir dúos", options.tiktokAllowDuet && creator?.duetDisabled != true, enabled && creator != null && !creator.duetDisabled) { onChange(options.copy(tiktokAllowDuet = it)) }
                    PublicationCheck("Permitir Stitch", options.tiktokAllowStitch && creator?.stitchDisabled != true, enabled && creator != null && !creator.stitchDisabled) { onChange(options.copy(tiktokAllowStitch = it)) }
                    Text("Tipo de contenido", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    PublicationCheck("Promociona mi marca o negocio", options.tiktokOwnBrand, enabled) { onChange(options.copy(tiktokOwnBrand = it)) }
                    PublicationCheck("Colaboración pagada con otra marca", options.tiktokPaidBrand, enabled) {
                        onChange(options.copy(tiktokPaidBrand = it, tiktokPrivacy = options.tiktokPrivacy.takeUnless { privacy -> it && privacy == "SELF_ONLY" }))
                    }
                    PublicationCheck("Contenido generado con IA", options.tiktokAiGenerated, enabled) { onChange(options.copy(tiktokAiGenerated = it)) }
                }
                Platform.FACEBOOK -> Unit
            }
    }
}

internal fun publicationOptionsSummary(platform: Platform, options: PlatformPublicationOptions): String = when (platform) {
    Platform.YOUTUBE -> listOf(
        when (options.youtubePrivacy) { "private" -> "Privado"; "unlisted" -> "No listado"; else -> "Público" },
        if (options.youtubeMadeForKids) "Para niños" else "No es para niños",
    ).joinToString(" · ")
    Platform.INSTAGRAM -> if (options.instagramShareToFeed) "Reel y feed" else "Solo Reels"
    Platform.TIKTOK -> options.tiktokPrivacy?.let(::tiktokPrivacyLabel) ?: "Elige quién puede verlo"
    Platform.FACEBOOK -> "A través de Instagram"
}

@Composable
private fun PublicationChoice(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
            Surface(
                onClick = { expanded = true }, enabled = enabled,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.defaultMinSize(minHeight = 64.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(choices.firstOrNull { it.first == value }?.second ?: "Seleccionar…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onChange(key); expanded = false }) }
            }
    }
}

@Composable
private fun PublicationCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
internal fun PublicationTextField(
    label: String,
    placeholder: String,
    value: String,
    enabled: Boolean,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, placeholder = { Text(placeholder) },
        enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun tiktokPrivacyLabel(value: String): String = when (value) {
    "PUBLIC_TO_EVERYONE" -> "Todos"
    "MUTUAL_FOLLOW_FRIENDS" -> "Amigos"
    "FOLLOWER_OF_CREATOR" -> "Seguidores"
    "SELF_ONLY" -> "Solo yo"
    else -> value
}

private val YOUTUBE_CATEGORIES = listOf(
    "1" to "Cine y animación", "2" to "Autos y vehículos", "10" to "Música", "15" to "Mascotas y animales",
    "17" to "Deportes", "19" to "Viajes y eventos", "20" to "Videojuegos", "22" to "Personas y blogs",
    "23" to "Comedia", "24" to "Entretenimiento", "25" to "Noticias y política", "26" to "Consejos y estilo",
    "27" to "Educación", "28" to "Ciencia y tecnología", "29" to "Activismo y organizaciones",
)
