package dev.kosslan.compass.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.kosslan.compass.AppContainer
import dev.kosslan.compass.R
import dev.kosslan.compass.ui.home.HomeScreen
import dev.kosslan.compass.ui.peers.PeersScreen
import dev.kosslan.compass.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

val LocalAppContainer =
    staticCompositionLocalOf<AppContainer> { error("no AppContainer was provided") }

@Serializable
data object HomeRoute

@Serializable
data object PeersRoute

@Serializable
data object SettingsRoute

private enum class Destination(
    val route: Any,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
) {
    Home(HomeRoute, R.drawable.ic_home, R.string.nav_home),
    Peers(PeersRoute, R.drawable.ic_peers, R.string.nav_peers),
    Settings(SettingsRoute, R.drawable.ic_settings, R.string.nav_settings),
}

@Composable
fun CompassApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val snackbar = remember { SnackbarHostState() }
    val tunnel = LocalAppContainer.current.tunnel

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(tunnel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tunnel.errors.collect { snackbar.showSnackbar(it) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected =
                            current?.hierarchy?.any { it.hasRoute(destination.route::class) } ==
                                true,
                        onClick = { navController.open(destination.route) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = stringResource(destination.label),
                            )
                        },
                        label = null,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(durationMillis = 220)) },
            exitTransition = { fadeOut(tween(durationMillis = 160)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenSettings = { navController.open(SettingsRoute) },
                )
            }
            composable<PeersRoute> { PeersScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

private fun NavHostController.open(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
