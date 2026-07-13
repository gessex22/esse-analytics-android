package com.esseanalytics.android.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.esseanalytics.android.core.datastore.AuthState
import com.esseanalytics.android.feature.auth.LoginScreen
import com.esseanalytics.android.feature.calendar.CalendarScreen
import com.esseanalytics.android.feature.gems.GemsScreen
import com.esseanalytics.android.feature.library.LibraryScreen
import com.esseanalytics.android.feature.stats.StatsScreen
import com.esseanalytics.android.feature.sync.SyncScreen
import com.esseanalytics.android.feature.upload.UploadScreen
import com.esseanalytics.android.feature.users.UsersScreen

private object Routes {
    const val LOGIN = "login"
    const val LIBRARY = "library"
    const val CALENDAR = "calendar"
    const val UPLOAD = "upload"
    const val MORE = "more"
    const val SYNC = "sync"
    const val STATS = "stats"
    const val USERS = "users"
    const val GEMS = "gems"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.LIBRARY, "Videos", Icons.Filled.VideoLibrary),
    BottomDestination(Routes.CALENDAR, "Calendario", Icons.Filled.CalendarMonth),
    BottomDestination(Routes.UPLOAD, "Subir", Icons.Filled.CloudUpload),
    BottomDestination(Routes.MORE, "Más", Icons.Filled.MoreHoriz),
)

@Composable
fun EsseAnalyticsNavHost(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val authState by sessionViewModel.authState.collectAsState()
    val navController = rememberNavController()

    when (authState) {
        is AuthState.LoggedOut -> LoginScreen(onLoggedIn = { /* authState cambia solo, ver TokenStore */ })
        is AuthState.LoggedIn -> MainAppScaffold(navController)
    }
}

@Composable
private fun MainAppScaffold(navController: NavHostController) {
    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
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
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.UPLOAD) { UploadScreen() }
            composable(Routes.MORE) { MoreScreen(navController) }
            composable(Routes.SYNC) { SyncScreen() }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.USERS) { UsersScreen() }
            composable(Routes.GEMS) { GemsScreen() }
        }
    }
}

// Agrupa las pantallas que no entran en la barra inferior — mismo criterio que
// el acordeón de Ajustes del frontend web (SettingsView.tsx): Sincronización,
// Estadísticas, Usuarios y Gemas viven acá, no en la nav principal.
@Composable
private fun MoreScreen(navController: NavHostController) {
    Column {
        MoreItem("Sincronización") { navController.navigate(Routes.SYNC) }
        MoreItem("Estadísticas") { navController.navigate(Routes.STATS) }
        MoreItem("Usuarios") { navController.navigate(Routes.USERS) }
        MoreItem("Gemas") { navController.navigate(Routes.GEMS) }
    }
}

@Composable
private fun MoreItem(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
