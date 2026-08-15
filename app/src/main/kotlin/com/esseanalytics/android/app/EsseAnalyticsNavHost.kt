package com.esseanalytics.android.app

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
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
import com.esseanalytics.android.feature.stats.DashboardScreen
import com.esseanalytics.android.feature.stats.HistoryScreen
import com.esseanalytics.android.feature.remotelibrary.RemoteLibraryScreen
import com.esseanalytics.android.feature.settings.SettingsScreen
import com.esseanalytics.android.feature.stats.StatsScreen
import com.esseanalytics.android.feature.sync.SyncScreen
import com.esseanalytics.android.feature.upload.UploadScreen
import com.esseanalytics.android.feature.users.UsersScreen

private object Routes {
    const val DASHBOARD = "dashboard"
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
    const val REMOTE_LIBRARY = "remote_library"
    const val HISTORY = "history"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, "Inicio", Icons.Outlined.Dashboard),
    BottomDestination(Routes.CALENDAR, "Calendario", Icons.Outlined.CalendarMonth),
    BottomDestination(Routes.UPLOAD, "Subir", Icons.Outlined.CloudUpload),
    BottomDestination(Routes.LIBRARY, "Videos", Icons.Outlined.VideoLibrary),
    BottomDestination(Routes.STATS, "Estadísticas", Icons.Outlined.QueryStats),
    BottomDestination(Routes.MORE, "Más", Icons.Outlined.MoreHoriz),
)

private val floatingMainDestinations = bottomDestinations.filter { it.route != Routes.MORE }

// Diagnóstico real en dispositivo (OPPO CPH2565, ver
// UIEssePanel/PLAN_SWIPE_Y_CARGA_SUAVE.md): 220ms de fade+slide en TODA
// navegación compositaba dos pantallas completas a la vez en cada cambio de
// tab -- candidato principal al lag reportado "al intercambiar entre
// prácticamente todas las pestañas" (gfxinfo real: p95=61ms, p99=250ms,
// clúster de frames en la banda 150-250ms). Cambiar entre las 6 pestañas
// del bottom nav es un salto entre HERMANAS, no navegación jerárquica --
// mismo criterio que Instagram/Twitter (cambio de tab instantáneo, sin
// transición). El fade+slide se queda para las pantallas de detalle
// colgadas de "Más" (Sync/Users/Gems/Historial/Ajustes/Biblioteca remota),
// donde sí comunica "entrar en profundidad".
private fun isMainTabRoute(route: String?): Boolean =
    route != null && bottomDestinations.any { route.startsWith(it.route) }

// Navegación de la barra inferior: conserva el estado de cada pestaña.
private fun NavHostController.navigateToMainTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FloatingBottomNavigation(
    currentDestination: NavDestination?,
    onDestinationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Misma composición de iOS: cinco accesos en una cápsula centrada y
    // "Más" como acción circular separada. La separación deja ver el fondo
    // y hace que la navegación flote, en vez de formar una franja opaca.
    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .navigationBarsPadding()
            // Apenas separada del área de gestos: antes el margen vertical de
            // 10.dp la dejaba demasiado arriba para una barra flotante.
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(31.dp),
            // Material translúcido: deja percibir el fondo sin sacrificar la
            // legibilidad de los iconos, como la barra flotante de Telegram.
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                floatingMainDestinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route?.startsWith(dest.route) == true
                    } == true
                    Box(
                        modifier = Modifier
                            .size(54.dp, 50.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                            )
                            .clickable { onDestinationClick(dest.route) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            dest.icon,
                            contentDescription = dest.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.width(10.dp))
        val more = bottomDestinations.last { it.route == Routes.MORE }
        Surface(
            onClick = { onDestinationClick(more.route) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    more.icon,
                    contentDescription = more.label,
                    tint = if (currentDestination?.hierarchy?.any { it.route?.startsWith(more.route) == true } == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun EsseAnalyticsNavHost(
    pendingImportUris: List<Uri> = emptyList(),
    onPendingImportUrisConsumed: () -> Unit = {},
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val authState by sessionViewModel.authState.collectAsState()

    when (val current = authState) {
        is AuthState.LoggedOut -> LoginScreen(onLoggedIn = { /* authState cambia solo, ver TokenStore */ })
        // key(user.id): fuerza un NavHostController -- y con él, TODOS los
        // ViewModelStore de cada pantalla (Library, RemoteLibrary, Stats,
        // Sync, etc.) -- completamente nuevo cada vez que la cuenta logueada
        // cambia sin matar el proceso. Antes navController se remember-eaba
        // acá arriba, a nivel de un composable que nunca sale de composición
        // (solo alterna qué hijo dibuja) -- sobrevivía un logout/login con
        // otra cuenta, y los ViewModels ya creados (con datos cacheados en
        // memoria de la cuenta anterior -- ver RemoteLibraryViewModel._uiState,
        // que solo refresca en su init{}, una vez por instancia) no se
        // recreaban. Bug real reportado: loguearse con otra cuenta mostraba
        // la Biblioteca remota de la cuenta anterior.
        is AuthState.LoggedIn -> key(current.user.id) {
            val navController = rememberNavController()
            val isLabMode by sessionViewModel.isLabMode.collectAsState()
            MainAppScaffold(
                navController,
                pendingImportUris,
                onPendingImportUrisConsumed,
                isOwner = current.user.isOwner,
                canUseCloudStorage = current.user.canUseCloudStorage,
                username = current.user.username,
                isLabMode = isLabMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    navController: NavHostController,
    pendingImportUris: List<Uri>,
    onPendingImportUrisConsumed: () -> Unit,
    isOwner: Boolean,
    canUseCloudStorage: Boolean,
    username: String,
    isLabMode: Boolean,
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
    // Hoisteado acá (antes vivía solo dentro de bottomBar) -- lo necesita
    // también el pointerInput del swipe global (Feature A), para saber en
    // qué tab está parado el usuario cuando suelta el gesto.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Feature B (pedido del usuario): spinner de refresh en la barra
    // compartida, a la izquierda del avatar -- ver TopBarViewModel/
    // RefreshActivityTracker. AppTopBar es UNA sola instancia acá (a
    // diferencia de iOS, donde cada pantalla tiene la suya), así que no
    // puede leer isLoading directo de cada ViewModel de tab.
    val topBarViewModel: TopBarViewModel = hiltViewModel()
    val isAnyRefreshing by topBarViewModel.isAnyRefreshing.collectAsState()
    // Pedido del usuario (mismo criterio ya aplicado en iOS, ver AppTopBar.swift
    // allá): repetir el logo + "EsseAnalytics" en las 5 pestañas era redundante
    // -- acá AppTopBar es UNA sola instancia compartida (no una por pantalla
    // como en iOS), así que en vez de que cada pantalla decida su propio
    // título, se deriva acá de `currentDestination` -- null en Inicio (marca
    // completa), el label de bottomDestinations en el resto. Detail routes
    // (Sync/Users/Gems/Historial/Ajustes/Biblioteca remota, alcanzadas desde
    // "Más") no matchean ningún bottomDestination -- se quedan con la marca
    // por default, tienen su propio DetailScaffold con título+volver encima.
    val screenTitle = bottomDestinations
        .firstOrNull { dest ->
            currentDestination?.hierarchy?.any { it.route?.startsWith(dest.route) == true } == true
        }
        ?.takeIf { it.route != Routes.DASHBOARD }
        ?.label

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                username = username,
                screenTitle = screenTitle,
                scrollBehavior = topBarScrollBehavior,
                isRefreshing = isAnyRefreshing,
            ) { navController.navigate(Routes.SETTINGS) }
        },
    ) { padding ->
        // duration=220 + FastOutSlowInEasing == el cubic-bezier [0.4,0,0.2,1]
        // que motion/react usa en la web para transiciones de panel/vista
        // (App.tsx, Sidebar.tsx, player/VideoModal.tsx, todas en ~0.22s con
        // ese mismo easing). Se define acá una sola vez, a nivel de NavHost,
        // en vez de repetirlo en cada composable(...).
        val navAnimSpec = tween<Float>(220, easing = FastOutSlowInEasing)
        val navOffsetSpec = tween<IntOffset>(220, easing = FastOutSlowInEasing)
        // Las pestañas hermanas usan un fundido corto: evita el corte seco de
        // una transición de 1 ms sin volver al fade+slide de 220 ms que
        // duplicaba el trabajo de composición durante demasiado tiempo.
        val tabAnimSpec = tween<Float>(120, easing = FastOutSlowInEasing)
        // Column con TODO el padding del Scaffold de una sola vez -- el banner
        // (si está) y el NavHost quedan uno arriba del otro, sin repartir
        // PaddingValues a mano entre los dos.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (isLabMode) LabModeBanner()
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.weight(1f),
            // Ver isMainTabRoute -- sin animación entre pestañas hermanas del
            // bottom nav, fade+slide se conserva para todo lo demás (entrar/
            // salir de las pantallas de detalle de "Más").
            enterTransition = {
                if (isMainTabRoute(initialState.destination.route) && isMainTabRoute(targetState.destination.route)) {
                    fadeIn(tabAnimSpec)
                } else {
                    fadeIn(navAnimSpec) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navOffsetSpec) { it / 10 }
                }
            },
            exitTransition = {
                if (isMainTabRoute(initialState.destination.route) && isMainTabRoute(targetState.destination.route)) {
                    fadeOut(tabAnimSpec)
                } else {
                    fadeOut(navAnimSpec) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, navOffsetSpec) { it / 10 }
                }
            },
            popEnterTransition = {
                if (isMainTabRoute(initialState.destination.route) && isMainTabRoute(targetState.destination.route)) {
                    fadeIn(tabAnimSpec)
                } else {
                    fadeIn(navAnimSpec) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navOffsetSpec) { it / 10 }
                }
            },
            popExitTransition = {
                if (isMainTabRoute(initialState.destination.route) && isMainTabRoute(targetState.destination.route)) {
                    fadeOut(tabAnimSpec)
                } else {
                    fadeOut(navAnimSpec) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navOffsetSpec) { it / 10 }
                }
            },
            ) {
            composable(Routes.DASHBOARD) { DashboardScreen() }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onImportClick = { navController.navigate(Routes.INGEST) },
                    onOpenUpload = { fileId -> navController.navigate("${Routes.UPLOAD}?fileId=$fileId") },
                    // Ya tiene alguna plataforma publicada -> Estadísticas (a
                    // eso fue, a ver cómo le fue); todavía nada publicado ->
                    // Subir, con ese archivo ya elegido.
                    onLocalClick = { file ->
                        if (file.platforms.isNotEmpty()) {
                            navController.navigate(Routes.STATS)
                        } else {
                            navController.navigate("${Routes.UPLOAD}?fileId=${file.id}")
                        }
                    },
                    // Un ítem remoto va directo al formulario de publicar de
                    // esa cola (ver RemoteLibraryScreen.initialVideoId), no a
                    // Estadísticas -- la central no refleja el publish remoto
                    // ahí todavía (ver Parte C.1, fuera de alcance).
                    onRemoteClick = { video ->
                        navController.navigate("${Routes.REMOTE_LIBRARY}?videoId=${video._id}")
                    },
                )
            }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(
                route = "${Routes.UPLOAD}?fileId={fileId}",
                arguments = listOf(navArgument("fileId") { type = NavType.LongType; defaultValue = -1L }),
            ) { uploadEntry ->
                val fileId = uploadEntry.arguments?.getLong("fileId")?.takeIf { it >= 0 }
                UploadScreen(
                    initialFileId = fileId,
                    // Éxito total (Fase 2 del plan de estabilidad/UX): redirige
                    // a Inicio. inclusive = true (no saveState/restoreState,
                    // a diferencia del tap normal del bottom nav) a propósito --
                    // fuerza una instancia nueva de DashboardViewModel para que
                    // su init { refresh() } corra de nuevo y muestre la
                    // publicación recién hecha. Biblioteca no necesita esto:
                    // observa Room de forma reactiva (ver FileRepository.observeAll).
                    onPublishedAllSuccess = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.MORE) { MoreScreen(navController, isOwner, canUseCloudStorage) }
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
            // Estadísticas ya es una pestaña principal: usar directamente la
            // pantalla evita apilar otro TopAppBar con el mismo título.
            composable(Routes.STATS) { StatsScreen() }
            // Sin saveState/restoreState a propósito -- entrar de nuevo a
            // Historial crea una instancia nueva de HistoryViewModel (init
            // vuelve a cargar), así que siempre se ve al día tras publicar,
            // sin necesitar un trigger de refresh compartido como Dashboard.
            composable(Routes.HISTORY) {
                DetailScaffold("Historial", onBack = navController::popBackStack) { HistoryScreen() }
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
            composable(
                route = "${Routes.REMOTE_LIBRARY}?videoId={videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { remoteLibraryEntry ->
                val videoId = remoteLibraryEntry.arguments?.getString("videoId")
                DetailScaffold("Biblioteca remota", onBack = navController::popBackStack) {
                    RemoteLibraryScreen(initialVideoId = videoId)
                }
            }
            }
            // Overlay real, no bottomBar de Scaffold: el contenido queda por
            // debajo y los huecos alrededor de la cápsula son transparentes.
            FloatingBottomNavigation(
                currentDestination = currentDestination,
                onDestinationClick = navController::navigateToMainTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
    // null = Inicio (logo + "EsseAnalytics" completo). Con valor = el resto
    // de las pestañas, muestra su propio nombre en vez de repetir la marca
    // -- mismo criterio que AppTopBarScreenTitle en iOS.
    screenTitle: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    isRefreshing: Boolean,
    onAvatarClick: () -> Unit,
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        // Default de Material es containerColor = surface (--card), pero el
        // resto del contenido bajo la barra vive en Scaffold.containerColor =
        // background (--background) -- esa diferencia real (mismo criterio
        // que theme.css: página vs tarjeta) se notaba como una costura justo
        // donde termina la barra, porque acá no hay ninguna tarjeta pegada a
        // ese borde para justificarla. El header web (App.tsx) usa bg-background
        // por la misma razón -- se replica acá para que no haya costura.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        title = {
            if (screenTitle != null) {
                Text(
                    screenTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            } else {
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
                    // Split en dos Text, no un string único: la web (App.tsx,
                    // Sidebar.tsx, LoginPage.tsx, LandingPage.tsx) siempre pinta
                    // "Esse" en --foreground y "Analytics" en --primary (rojo o
                    // ámbar según el tema activo), nunca las dos palabras del
                    // mismo color.
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            "Esse",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { /* sin sistema de notificaciones todavía */ }) {
                BadgedBox(badge = { Badge() }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones")
                }
            }
            // Feature B (pedido del usuario): spinner de refresh acá, a la
            // izquierda del avatar -- señal compartida entre las 5 pestañas
            // (ver RefreshActivityTracker), no el estado de una pantalla
            // puntual.
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            UserAvatar(username = username, onClick = onAvatarClick)
        },
    )
}

// Persistente en las 4 pestañas del bottom nav (mismo Scaffold que AppTopBar,
// arriba) -- mirror de AppTopBarModifier.swift (iOS) y el banner de App.tsx
// (desktop). isLabMode viaja desde SessionViewModel, que ya lo confirmó en
// vivo contra GET /api/health (ver LabModeStatus.kt) -- nunca por heurística.
@Composable
private fun LabModeBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x267C3AED))
            .padding(vertical = 6.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🧪", modifier = Modifier.padding(end = 6.dp))
        Text(
            "Laboratorio · Datos simulados",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF7C3AED),
            fontWeight = FontWeight.Medium,
        )
    }
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
private fun MoreScreen(navController: NavHostController, isOwner: Boolean, canUseCloudStorage: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                MoreItem(
                    icon = Icons.Outlined.VideoLibrary,
                    label = "Videos",
                    description = "Biblioteca de videos locales y remotos",
                    onClick = { navController.navigate(Routes.LIBRARY) },
                )
                HorizontalDivider()
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
                HorizontalDivider()
                MoreItem(
                    icon = Icons.Outlined.History,
                    label = "Historial",
                    description = "Todas las subidas confirmadas, con filtro por plataforma",
                    onClick = { navController.navigate(Routes.HISTORY) },
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
                // Premium + entitlement de storage aparte (ver
                // requireCloudStorage/hasCloudStorage) -- generalizado desde
                // owner-only, ver Parte D del plan. El owner sigue viéndolo
                // porque canUseCloudStorage ya lo incluye (User.kt).
                if (canUseCloudStorage) {
                    HorizontalDivider()
                    MoreItem(
                        icon = Icons.Outlined.CloudQueue,
                        label = "Biblioteca remota",
                        description = "Cola de videos en la nube, publicable desde cualquier lado",
                        onClick = { navController.navigate(Routes.REMOTE_LIBRARY) },
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
