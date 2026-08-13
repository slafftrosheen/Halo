package com.halo.app.ui.shell

import android.util.Log
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
import com.halo.app.ui.screens.settings.IntelligenceScreen
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
    val intelligenceViewModel = ManualDependencyGraph.intelligenceViewModel
    val settingsViewModel = ManualDependencyGraph.settingsViewModel
    val devicesViewModel = ManualDependencyGraph.devicesViewModel
    val pairingViewModel = ManualDependencyGraph.pairingViewModel
    val protocolLabViewModel = ManualDependencyGraph.protocolLabViewModel

    fun navigateSafely(route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.navigationEvents.collectLatest { route ->
            Log.d("AppShell", "Navigation from settings: $route")
            navigateSafely(route)
        }
    }

    LaunchedEffect(Unit) {
        pairingViewModel.navigationEvents.collectLatest { event ->
            Log.d("AppShell", "Navigation from pairing: $event")
            if (event == "back") navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        ManualDependencyGraph.homeViewModel.navigationEvents.collectLatest { route ->
            Log.d("AppShell", "Navigation from home: $route")
            navigateSafely(route)
        }
    }

    LaunchedEffect(Unit) {
        intelligenceViewModel.navigationEvents.collectLatest { route ->
            Log.d("AppShell", "Navigation from intelligence: $route")
            navigateSafely(route)
        }
    }
    
    LaunchedEffect(Unit) {
        devicesViewModel.navigationEvents.collectLatest { route ->
            navigateSafely(route)
        }
    }

    LaunchedEffect(Unit) {
        protocolLabViewModel.navigationEvents.collectLatest { route ->
            navigateSafely(route)
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = com.halo.app.ui.glass.material.HaloGlassColors.Background,
        bottomBar = {
            HaloGlassNavigation(
                navController = navController,
                items = bottomNavItems.map { 
                    NavigationItem(it.route, it.title, it.icon) 
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                composable("ai_settings") {
                    IntelligenceScreen(
                        viewModel = ManualDependencyGraph.intelligenceViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("permissions") {
                    com.halo.app.ui.screens.settings.PermissionsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("discovery") {
                    com.halo.app.ui.screens.discovery.PairingScreen(viewModel = ManualDependencyGraph.pairingViewModel)
                }
                composable(Screen.ProtocolLab.route) {
                    ProtocolLabScreen(
                        viewModel = ManualDependencyGraph.protocolLabViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("console") {
                    ConsoleScreen(
                        viewModel = ManualDependencyGraph.consoleViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("inspector") {
                    RuntimeInspectorScreen(
                        viewModel = ManualDependencyGraph.runtimeInspectorViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
