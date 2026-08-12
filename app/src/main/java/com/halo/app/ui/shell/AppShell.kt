package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.*
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.glass.components.HaloGlassNavigation
import com.halo.app.ui.glass.components.NavigationItem
import com.halo.app.ui.screens.home.HomeScreen
import com.halo.app.ui.screens.activity.ActivityScreen
import com.halo.app.ui.screens.devices.DevicesScreen
import com.halo.app.ui.screens.settings.SettingsScreen
import com.halo.app.ui.developer.protocol.ProtocolLabScreen
import com.halo.app.ui.developer.inspector.RuntimeInspectorScreen
import com.halo.app.ui.developer.console.ConsoleScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * Navigation configuration for HALO Glass UI.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Activity : Screen("activity", "Activity", Icons.Default.Timer)
    data object Devices : Screen("devices", "Devices", Icons.Default.Devices)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object ProtocolLab : Screen("protocol_lab", "Protocol", Icons.Default.Settings) // Temporary
}

// Primary navigation items (bottom bar)
val bottomNavItems = listOf(Screen.Home, Screen.Activity, Screen.Devices, Screen.Settings)

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val settingsViewModel = ManualDependencyGraph.settingsViewModel
    val devicesViewModel = ManualDependencyGraph.devicesViewModel

    LaunchedEffect(Unit) {
        settingsViewModel.navigationEvents.collectLatest { route ->
            navController.navigate(route)
        }
    }
    
    LaunchedEffect(Unit) {
        devicesViewModel.navigationEvents.collectLatest { route ->
            navController.navigate(route)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = ManualDependencyGraph.homeViewModel)
            }
            composable(Screen.Activity.route) {
                ActivityScreen(viewModel = ManualDependencyGraph.activityViewModel)
            }
            composable(Screen.Devices.route) {
                DevicesScreen(viewModel = ManualDependencyGraph.devicesViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = ManualDependencyGraph.settingsViewModel)
            }
            composable("discovery") {
                com.halo.app.ui.screens.discovery.HeyCyanDiscoveryScreen(
                    context = androidx.compose.ui.platform.LocalContext.current
                )
            }
            composable(Screen.ProtocolLab.route) {
                ProtocolLabScreen(viewModel = ManualDependencyGraph.protocolLabViewModel)
            }
            composable("console") {
                ConsoleScreen(viewModel = ManualDependencyGraph.consoleViewModel)
            }
            composable("inspector") {
                RuntimeInspectorScreen(viewModel = ManualDependencyGraph.runtimeInspectorViewModel)
            }
        }
        
        // Navigation Pill Overlay
        HaloGlassNavigation(
            navController = navController,
            items = bottomNavItems.map { 
                NavigationItem(it.route, it.title, it.icon) 
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
