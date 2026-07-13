package com.esseanalytics.android.app

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
        )
    }
}

@Composable
private fun MainAppScaffold(
    navController: NavHostController,
    pendingImportUris: List<Uri>,
    onPendingImportUrisConsumed: () -> Unit,
    isOwner: Boolean,
) {
    // Un video compartido desde otra app (Galería, Archivos) llega acá vía
    // MainActivity — si la app estaba en cualquier otra pantalla, la manda a
    // Importar apenas hay algo pendiente, sin que el usuario tenga que buscarla.
    LaunchedEffect(pendingImportUris) {
        if (pendingImportUris.isNotEmpty()) {
            navController.navigate(Routes.INGEST)
        }
    }

    Scaffold(
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

// Agrupa las pantallas que no entran en la barra inferior — mismo criterio que
// el acordeón de Ajustes del frontend web (SettingsView.tsx): Sincronización,
// Estadísticas, Usuarios y Gemas viven acá, no en la nav principal. Usuarios
// es owner-only (la central igual 403-earía, pero no tiene sentido mostrar
// una entrada que va a fallar seguro).
@Composable
private fun MoreScreen(navController: NavHostController, isOwner: Boolean) {
    Column {
        MoreItem("Sincronización") { navController.navigate(Routes.SYNC) }
        MoreItem("Estadísticas") { navController.navigate(Routes.STATS) }
        if (isOwner) {
            MoreItem("Usuarios") { navController.navigate(Routes.USERS) }
        }
        MoreItem("Gemas") { navController.navigate(Routes.GEMS) }
        MoreItem("Ajustes") { navController.navigate(Routes.SETTINGS) }
    }
}

@Composable
private fun MoreItem(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
