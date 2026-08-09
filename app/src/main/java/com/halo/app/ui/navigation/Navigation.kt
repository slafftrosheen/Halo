package com.halo.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.screens.home.HomeScreen
import com.halo.app.ui.screens.timeline.TimelineScreen
import com.halo.app.ui.screens.console.ConsoleScreen
import com.halo.app.ui.screens.discovery.HeyCyanDiscoveryScreen
import com.halo.app.ui.screens.settings.SettingsScreen
import com.halo.app.ui.theme.HaloPrimary
import com.halo.app.ui.theme.HaloSurface
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Timeline : Screen("timeline", "Timeline", Icons.Default.List)
    object Console : Screen("console", "Console", Icons.Default.Info)
    object Discovery : Screen("heycyan-discovery", "BLE Capture", Icons.Default.Bluetooth)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Timeline,
    Screen.Console,
    Screen.Discovery,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = HaloSurface,
                contentColor = HaloTextPrimary
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HaloPrimary,
                            selectedTextColor = HaloPrimary,
                            indicatorColor = HaloSurface,
                            unselectedIconColor = HaloTextSecondary,
                            unselectedTextColor = HaloTextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { 
                HomeScreen(viewModel = ManualDependencyGraph.homeViewModel) 
            }
            composable(Screen.Timeline.route) { 
                TimelineScreen(viewModel = ManualDependencyGraph.timelineViewModel) 
            }
            composable(Screen.Console.route) {
                ConsoleScreen(viewModel = ManualDependencyGraph.consoleViewModel)
            }
            composable(Screen.Discovery.route) {
                HeyCyanDiscoveryScreen(context = context)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = ManualDependencyGraph.settingsViewModel) 
            }
        }
    }
}
