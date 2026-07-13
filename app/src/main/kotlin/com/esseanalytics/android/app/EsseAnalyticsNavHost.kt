package com.esseanalytics.android.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.esseanalytics.android.core.datastore.AuthState
import com.esseanalytics.android.feature.auth.LoginScreen
import com.esseanalytics.android.feature.calendar.CalendarScreen
import com.esseanalytics.android.feature.gems.GemsScreen
import com.esseanalytics.android.feature.ingest.IngestScreen
import com.esseanalytics.android.feature.library.LibraryScreen
import com.esseanalytics.android.feature.settings.SettingsScreen
import com.esseanalytics.android.feature.stats.StatsScreen
import com.esseanalytics.android.feature.sync.SyncScreen
import com.esseanalytics.android.feature.upload.UploadScreen
import com.esseanalytics.android.feature.users.UsersScreen

private object Routes {
    const val LIBRARY = "library"
    const val CALENDAR = "calendar"
    const val UPLOAD = "upload"
    const val MORE = "more"
    const val SYNC = "sync"
    const val STATS = "stats"
    const val USERS = "users"
    const val GEMS = "gems"
    const val INGEST = "ingest"
    const val SETTINGS = "settings"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.LIBRARY, "Videos", Icons.Outlined.VideoLibrary),
    BottomDestination(Routes.CALENDAR, "Calendario", Icons.Outlined.CalendarMonth),
    BottomDestination(Routes.UPLOAD, "Subir", Icons.Outlined.CloudUpload),
    BottomDestination(Routes.MORE, "Más", Icons.Outlined.MoreHoriz),
)

@Composable
fun EsseAnalyticsNavHost(
    pendingImportUris: List<Uri> = emptyList(),
    onPendingImportUrisConsumed: () -> Unit = {},
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val authState by sessionViewModel.authState.collectAsState()
    val navController = rememberNavController()

    when (val current = authState) {
        is AuthState.LoggedOut -> LoginScreen(onLoggedIn = { /* authState cambia solo, ver TokenStore */ })
        is AuthState.LoggedIn -> MainAppScaffold(
            navController,
            pendingImportUris,
            onPendingImportUrisConsumed,
            isOwner = current.user.isOwner,
            username = current.user.username,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    navController: NavHostController,
    pendingImportUris: List<Uri>,
    onPendingImportUrisConsumed: () -> Unit,
    isOwner: Boolean,
    username: String,
) {
    // Un video compartido desde otra app (Galería, Archivos) llega acá vía
    // MainActivity — si la app estaba en cualquier otra pantalla, la manda a
    // Importar apenas hay algo pendiente, sin que el usuario tenga que buscarla.
    LaunchedEffect(pendingImportUris) {
        if (pendingImportUris.isNotEmpty()) {
            navController.navigate(Routes.INGEST)
        }
    }

    // enterAlwaysScrollBehavior: la barra entera se oculta apenas empezás a
    // bajar y reaparece apenas subís un poco (estilo apps de redes), a
    // diferencia de exitUntilCollapsed que la reduce pero nunca la esconde
    // del todo. El nestedScroll va en el Scaffold de acá afuera -- así
    // cualquier lista scrolleable de cualquier pantalla del bottom nav
    // (Biblioteca, Calendario, Subir) hace que colapse, sin repetir el cable
    // en cada una.
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                username = username,
                scrollBehavior = topBarScrollBehavior,
                onAvatarClick = { navController.navigate(Routes.SETTINGS) },
            )
        },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    // startsWith, no == : Subir ahora es una ruta con
                    // argumento opcional ("upload?fileId={fileId}"), no el
                    // literal "upload" -- ver la ruta de Routes.UPLOAD más
                    // abajo. Ninguna otra ruta de la app es prefijo de otra,
                    // así que esto no genera falsos positivos.
                    val selected = currentDestination?.hierarchy?.any { it.route?.startsWith(dest.route) == true } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onImportClick = { navController.navigate(Routes.INGEST) },
                    // Ya tiene alguna plataforma publicada -> Estadísticas (a
                    // eso fue, a ver cómo le fue); todavía nada publicado ->
                    // Subir, con ese archivo ya elegido.
                    onVideoClick = { file ->
                        if (file.platforms.isNotEmpty()) {
                            navController.navigate(Routes.STATS)
                        } else {
                            navController.navigate("${Routes.UPLOAD}?fileId=${file.id}")
                        }
                    },
                )
            }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(
                route = "${Routes.UPLOAD}?fileId={fileId}",
                arguments = listOf(navArgument("fileId") { type = NavType.LongType; defaultValue = -1L }),
            ) { backStackEntry ->
                val fileId = backStackEntry.arguments?.getLong("fileId")?.takeIf { it >= 0 }
                UploadScreen(initialFileId = fileId)
            }
            composable(Routes.MORE) { MoreScreen(navController, isOwner) }
            composable(Routes.INGEST) {
                DetailScaffold("Importar video", onBack = navController::popBackStack) {
                    IngestScreen(
                        pendingUris = pendingImportUris,
                        onPendingUrisConsumed = onPendingImportUrisConsumed,
                    )
                }
            }
            composable(Routes.SYNC) {
                DetailScaffold("Sincronización", onBack = navController::popBackStack) { SyncScreen() }
            }
            composable(Routes.STATS) {
                DetailScaffold("Estadísticas", onBack = navController::popBackStack) { StatsScreen() }
            }
            composable(Routes.USERS) {
                DetailScaffold("Usuarios", onBack = navController::popBackStack) { UsersScreen() }
            }
            composable(Routes.GEMS) {
                DetailScaffold("Gemas", onBack = navController::popBackStack) { GemsScreen() }
            }
            composable(Routes.SETTINGS) {
                DetailScaffold("Ajustes", onBack = navController::popBackStack) { SettingsScreen() }
            }
        }
    }
}

// Barra compartida por las 4 pestañas del bottom nav (Videos/Calendario/
// Subir/Más) -- un solo topBar en el Scaffold de MainAppScaffold, no uno por
// pantalla. Logo + nombre a la izquierda (mismo ícono que el launcher real,
// ver AndroidManifest/res/drawable-xxxhdpi), campana de notificaciones
// (TODAVÍA sin datos reales -- ver la nota de arriba, la app no tiene
// sistema de notificaciones, esto es el gancho visual para cuando exista) y
// avatar del usuario a la derecha, que lleva a Ajustes (ahí vive logout).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    username: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onAvatarClick: () -> Unit,
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ic_launcher_foreground.png tiene el padding de "safe zone"
                // de ícono adaptativo (el dibujo real ocupa ~60% del lienzo,
                // pensado para que el SO lo recorte con una máscara) -- acá
                // se muestra tal cual, sin ese recorte, así que hace falta
                // un tamaño bastante más grande que el de un ícono normal
                // para que el trazo se lea a simple vista.
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    "EsseAnalytics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        actions = {
            IconButton(onClick = { /* sin sistema de notificaciones todavía */ }) {
                BadgedBox(badge = { Badge() }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones")
                }
            }
            UserAvatar(username = username, onClick = onAvatarClick)
        },
    )
}

@Composable
private fun UserAvatar(username: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            username.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Agrupa las pantallas que no entran en la barra inferior — mismo criterio que
// el acordeón de Ajustes del frontend web (SettingsView.tsx): Sincronización,
// Estadísticas, Usuarios y Gemas viven acá, no en la nav principal. Usuarios
// es owner-only (la central igual 403-earía, pero no tiene sentido mostrar
// una entrada que va a fallar seguro).
//
// Antes era un ListItem con solo el nombre -- una lista de texto plano no se
// distingue en nada de cualquier otra pantalla genérica de Android. Ahora
// cada fila tiene ícono + descripción corta + flecha, agrupadas en una sola
// tarjeta (mismo patrón "settings list" que iOS/Android usan para esto,
// elevation=0.dp para que coincida con el resto de las Card() de la app).
@Composable
private fun MoreScreen(navController: NavHostController, isOwner: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            "Más",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                MoreItem(
                    icon = Icons.Outlined.Sync,
                    label = "Sincronización",
                    description = "Emparejar videos entre plataformas",
                    onClick = { navController.navigate(Routes.SYNC) },
                )
                HorizontalDivider()
                MoreItem(
                    icon = Icons.Outlined.QueryStats,
                    label = "Estadísticas",
                    description = "Vistas, likes y comentarios por red",
                    onClick = { navController.navigate(Routes.STATS) },
                )
                if (isOwner) {
                    HorizontalDivider()
                    MoreItem(
                        icon = Icons.Outlined.PeopleOutline,
                        label = "Usuarios",
                        description = "Administrar cuentas de la app",
                        onClick = { navController.navigate(Routes.USERS) },
                    )
                }
                HorizontalDivider()
                MoreItem(
                    icon = Icons.Outlined.Diamond,
                    label = "Gemas",
                    description = "Herramientas auxiliares (solo Windows)",
                    onClick = { navController.navigate(Routes.GEMS) },
                )
                HorizontalDivider()
                MoreItem(
                    icon = Icons.Outlined.Settings,
                    label = "Ajustes",
                    description = "Tema, flujo de trabajo, cuenta",
                    onClick = { navController.navigate(Routes.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

// Las 4 pantallas que cuelgan de "Más" no tienen bottom bar propia para
// volver — sin esto, la única forma de salir era el back del sistema
// (gesto/botón), nada dentro de la app. Un TopAppBar con flecha de volver es
// el patrón estándar de Android para una pantalla de "detalle" así.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
