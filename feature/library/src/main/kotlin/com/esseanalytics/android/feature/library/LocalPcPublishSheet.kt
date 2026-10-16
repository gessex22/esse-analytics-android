package com.esseanalytics.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.designsystem.component.CreatorStudioBackdrop
import com.esseanalytics.android.core.designsystem.theme.CreatorHomeTheme
import com.esseanalytics.android.core.designsystem.component.CreatorSerif
import com.esseanalytics.android.core.designsystem.icon.FacebookLogo
import com.esseanalytics.android.core.designsystem.icon.PlatformIcons
import com.esseanalytics.android.core.designsystem.theme.FacebookBlue

// Publicar desde una PC de la LAN es server-side y sin bytes -- la PC lee su
// propio archivo del disco y lo sube ella misma (ver
// LibraryViewModel.publishLan), el celular solo manda un comando JSON por
// plataforma. Por eso esta pantalla NO reusa UploadScreen/WorkManager
// (feature:upload -- que además feature:library no puede depender, feature→
// feature está prohibido en esta arquitectura): no hay fileId: Long de Room,
// no hay progreso local de bytes que trackear, es 3 POSTs simples con la
// respuesta esperando en memoria. Mismo criterio de UX que PCLocalPublishView
// en iOS ("toca la fila -> mismo lugar donde se ve Y se publica"), pero
// construido desde cero -- Android no tenía ningún equivalente.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPcPublishSheet(
    item: LibraryListItem.LanVideo,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    CreatorHomeTheme {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Publicar desde esta PC") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Cerrar")
                            }
                        },
                    )
                },
            ) { padding ->
                LocalPcPublishContent(
                    item = item,
                    viewModel = viewModel,
                    onChooseAnother = onDismiss,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

// El mismo contenido se usa dentro de la pestaña Subir y desde Biblioteca.
// El contenedor (pantalla normal o diálogo de detalle) deja de decidir qué
// formulario se muestra; solamente aporta cómo volver al selector.
@Composable
fun LocalPcPublishContent(
    item: LibraryListItem.LanVideo,
    viewModel: LibraryViewModel,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier,
    username: String? = null,
    onProfileClick: (() -> Unit)? = null,
    floatingNavOverlay: Boolean = false,
) {
    CreatorHomeTheme {
        Box(modifier.fillMaxSize()) {
            CreatorStudioBackdrop()
            LocalPcPublishContentBody(
                item = item,
                viewModel = viewModel,
                onChooseAnother = onChooseAnother,
                modifier = Modifier.fillMaxSize(),
                username = username,
                onProfileClick = onProfileClick,
                floatingNavOverlay = floatingNavOverlay,
            )
        }
    }
}

@Composable
private fun LocalPcPublishContentBody(
    item: LibraryListItem.LanVideo,
    viewModel: LibraryViewModel,
    onChooseAnother: () -> Unit,
    modifier: Modifier,
    username: String?,
    onProfileClick: (() -> Unit)?,
    floatingNavOverlay: Boolean,
) {
    val publishState by viewModel.lanPublishState.collectAsState()
    // Preselecciona lo que todavía no está publicado ni descartado en esa
    // plataforma -- mismo criterio que isLanVideoPending (LibraryViewModel),
    // para no ofrecer por default re-publicar algo que ya salió.
    var selectedPlatforms by remember(item.video._id) {
        mutableStateOf(
            Platform.publishable.filter {
                it.apiValue !in item.video.platforms && it.apiValue !in item.video.platforms_discarded
            }.toSet(),
        )
    }
    var title by remember(item.video._id) { mutableStateOf(item.video.fileName.substringBeforeLast('.')) }
    var description by remember(item.video._id) { mutableStateOf("") }
    var tiktokPublic by remember { mutableStateOf(false) }
    // Paridad con UploadScreen.kt (FacebookCrossPostRow) -- no se puede
    // reusar ese composable directo, feature:library no puede depender de
    // feature:upload (feature→feature prohibido, ver comentario de arriba).
    // El contrato ya lo soporta (LocalPcInstagramUploadRequest.crossPostFacebook,
    // local-backend/src/controllers/instagram-upload.controller.ts), solo
    // faltaba el toggle en esta hoja.
    var crossPostFacebook by remember(item.video._id) { mutableStateOf(false) }
    // FIX 2026-08-18 (ver UIEssePanel/PLAN_LAN_PICKER_Y_REPRODUCTOR-2026-08-18.md):
    // antes esta hoja no tenía forma de VER el video antes de publicar, solo
    // nombre+checkboxes. RemoteVideoPlayerDialog ya es genérico (title +
    // streamUrl + onDismiss, sin nada específico de la cola remota -- mismo
    // componente que usa LibraryScreen para playingLan), se reusa tal cual.
    var showPlayer by remember { mutableStateOf(false) }

    val isSubmitting = publishState.values.any { it is LanPublishResult.Pending || it is LanPublishResult.InProgress }

    if (showPlayer) {
        RemoteVideoPlayerDialog(
            title = item.video.fileName,
            streamUrl = viewModel.lanStreamUrl(item),
            onDismiss = { showPlayer = false },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            // bottom extra: mismo motivo que PublishForm en feature:upload --
            // cuando esta hoja se abre desde la tab "Subir" (puente del
            // NavHost) la cápsula flotante es un overlay, no reserva espacio,
            // y el botón "Publicar" quedaba tapable al final del scroll.
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (floatingNavOverlay) 124.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
                if (username != null && onProfileClick != null) {
                    LocalPcCreatorHeader(username, onProfileClick)
                }
                LocalPcStudioCard {
                    Text(item.video.fileName, style = MaterialTheme.typography.titleLarge, fontFamily = CreatorSerif, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = {
                        viewModel.resetLanPublishState()
                        onChooseAnother()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Elegir otro video") }
                }
                // FIX 2026-08-18: miniatura tocable con ícono play, antes de
                // los campos del formulario (mismo lugar que PublishForm en
                // feature:upload) -- ver showPlayer/RemoteVideoPlayerDialog
                // arriba.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPlayer = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val thumbnailUrl = viewModel.lanThumbnailUrl(item)
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }

                LocalPcStudioCard {
                    Text(
                        "Esta PC sube el video ella misma -- el teléfono solo manda el pedido. No se puede pausar ni reanudar la subida desde acá.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LocalPcStudioDetailsFields(title, { title = it }, description, { description = it }, !isSubmitting)
                }
                LocalPcStudioCard(contentPadding = 10.dp, contentSpacing = 4.dp) {
                    Text("Plataformas", style = MaterialTheme.typography.titleMedium, fontFamily = CreatorSerif, fontWeight = FontWeight.Bold)
                    Platform.publishable.forEach { platform ->
                        LanPublishPlatformRow(
                            platform = platform,
                            selected = platform in selectedPlatforms,
                            result = publishState[platform],
                            alreadyPublished = platform.apiValue in item.video.platforms,
                            discarded = platform.apiValue in item.video.platforms_discarded,
                            enabled = !isSubmitting,
                            onToggle = { checked ->
                                selectedPlatforms = if (checked) selectedPlatforms + platform else selectedPlatforms - platform
                            },
                        )
                        // Mismo criterio que PublishForm en feature:upload:
                        // Facebook no es una plataforma publicable aparte, es
                        // un crosspost del mismo archivo que Instagram acepta
                        // (ver instagram-upload.controller.ts, local-backend).
                        if (platform == Platform.INSTAGRAM) {
                            LanFacebookCrossPostRow(
                                checked = crossPostFacebook,
                                enabled = Platform.INSTAGRAM in selectedPlatforms && !isSubmitting,
                                onCheckedChange = { crossPostFacebook = it },
                                alreadyPublished = Platform.FACEBOOK.apiValue in item.video.platforms,
                            )
                        }
                    }
                }
                if (Platform.TIKTOK in selectedPlatforms) {
                    LocalPcTikTokAudienceRow(tiktokPublic, !isSubmitting) { tiktokPublic = it }
                }

                LocalPcPublishButton(
                    isSubmitting = isSubmitting,
                    enabled = selectedPlatforms.isNotEmpty(),
                    onClick = {
                        viewModel.publishLan(
                            item,
                            selectedPlatforms,
                            title,
                            description,
                            tiktokPublic,
                            crossPostFacebook = crossPostFacebook && Platform.INSTAGRAM in selectedPlatforms,
                        )
                    },
                )
    }
}

// Mirror de FacebookCrossPostRow (feature:upload/UploadScreen.kt) -- no se
// puede importar directo por la prohibición feature→feature de esta
// arquitectura (ver comentario grande al inicio del archivo), así que queda
// duplicado a propósito, con el mismo texto y layout.
@Composable
private fun LanFacebookCrossPostRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    alreadyPublished: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked && enabled,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics {
                contentDescription = "Facebook, ${if (checked && enabled) "seleccionado" else "no seleccionado"}${if (alreadyPublished) ", ya publicado" else ""}${if (enabled) "" else ", deshabilitado"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                PlatformIcons.FacebookLogo,
                contentDescription = null,
                tint = if (alreadyPublished) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (alreadyPublished) {
            Text("Ya publicado", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Spacer(Modifier.weight(1f))
        Checkbox(checked = checked && enabled, enabled = enabled, onCheckedChange = null, modifier = Modifier.size(48.dp).clearAndSetSemantics {})
    }
}

@Composable
private fun LanPublishPlatformRow(
    platform: Platform,
    selected: Boolean,
    result: LanPublishResult?,
    alreadyPublished: Boolean,
    discarded: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onToggle,
            ).semantics {
                contentDescription = buildString {
                    append(platform.displayName)
                    append(if (selected) ", seleccionado" else ", no seleccionado")
                    when (result) {
                        LanPublishResult.Pending -> append(", en espera")
                        LanPublishResult.InProgress -> append(", publicando")
                        LanPublishResult.Success -> append(", publicado")
                        null -> if (alreadyPublished) append(", publicado")
                        is LanPublishResult.Failure -> append(", error: ${result.message}")
                    }
                    if (discarded && !alreadyPublished && result !is LanPublishResult.Success) append(", descartado")
                    if (!enabled) append(", deshabilitado")
                }
            },
        ) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(48.dp).clearAndSetSemantics {})
            val icon = platformIcon(platform)
            if (icon != null) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (alreadyPublished || result is LanPublishResult.Success) platformColor(platform) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    if (discarded && !alreadyPublished && result !is LanPublishResult.Success) {
                        Text("╱", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            when (result) {
                is LanPublishResult.Pending -> Text("En espera", style = MaterialTheme.typography.labelSmall)
                is LanPublishResult.InProgress -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                is LanPublishResult.Success -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                is LanPublishResult.Failure -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                null -> Unit
            }
        }
        if (result is LanPublishResult.Failure) {
            Text(
                result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}

@Composable
private fun LocalPcStudioCard(
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    contentSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = { content() },
        )
    }
}

@Composable
private fun LocalPcStudioDetailsFields(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Título") },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Descripción") },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocalPcTikTokAudienceRow(isPublic: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = isPublic, onCheckedChange = onChange, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isPublic) "TikTok: público" else "TikTok: solo yo (privado)")
    }
}

@Composable
private fun LocalPcPublishButton(isSubmitting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isSubmitting && enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Publicar")
    }
}

@Composable
private fun LocalPcCreatorHeader(username: String, onProfileClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Publicar desde la PC", Modifier.weight(1f), fontFamily = CreatorSerif, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onProfileClick)
                .semantics { contentDescription = "Abrir perfil de $username" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(username.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(name = "Publicar PC · claro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LocalPcPublishStudioLightPreview() = LocalPcPublishStudioPreview(dark = false)

@Preview(name = "Publicar PC · oscuro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LocalPcPublishStudioDarkPreview() = LocalPcPublishStudioPreview(dark = true)

@Composable
private fun LocalPcPublishStudioPreview(dark: Boolean) {
    CreatorHomeTheme(darkTheme = dark) {
        Box {
            CreatorStudioBackdrop()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LocalPcCreatorHeader("Luna", onProfileClick = {})
                LocalPcStudioCard {
                    Text("ensayo-general.mp4", fontFamily = CreatorSerif, style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Elegir otro video")
                    }
                }
                LocalPcStudioCard {
                    Text("Esta PC sube el video ella misma. El teléfono solo manda el pedido.", style = MaterialTheme.typography.bodySmall)
                    LocalPcStudioDetailsFields("Resumen del evento", {}, "Descripción", {}, enabled = true)
                }
                LocalPcStudioCard(contentPadding = 10.dp, contentSpacing = 4.dp) {
                    Text("Plataformas", fontFamily = CreatorSerif, style = MaterialTheme.typography.titleMedium)
                    LanPublishPlatformRow(Platform.YOUTUBE, true, LanPublishResult.InProgress, false, false, false) {}
                    LanPublishPlatformRow(Platform.INSTAGRAM, true, LanPublishResult.Success, false, false, false) {}
                    LanFacebookCrossPostRow(checked = true, enabled = true, onCheckedChange = {})
                    LanPublishPlatformRow(Platform.TIKTOK, true, LanPublishResult.Failure("No se pudo completar la publicación."), false, false, false) {}
                    LocalPcTikTokAudienceRow(isPublic = false, enabled = true, onChange = {})
                }
                LocalPcPublishButton(isSubmitting = false, enabled = true, onClick = {})
            }
        }
    }
}
