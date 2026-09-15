package com.halo.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.developer.console.ConsoleScreen
import com.halo.app.ui.developer.inspector.RuntimeInspectorScreen
import com.halo.app.ui.developer.protocol.ProtocolLabScreen
import com.halo.app.ui.glass.components.HaloGlassNavigation
import com.halo.app.ui.glass.components.NavigationItem
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.screens.activity.ActivityScreen
import com.halo.app.ui.screens.devices.BuddyBotControlScreen
import com.halo.app.ui.screens.devices.DevicesScreen
import com.halo.app.ui.screens.devices.HeyCyanControlScreen
import com.halo.app.ui.screens.discovery.PairingScreen
import com.halo.app.ui.screens.home.HomeScreen
import com.halo.app.ui.screens.settings.IntelligenceScreen
import com.halo.app.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.remember
import com.halo.app.ui.screens.HaloConsoleScreen
import com.halo.app.ui.screens.EngineStatusUi
import com.halo.app.ui.screens.ProviderStatusUi
import com.halo.app.ui.screens.ConsoleEventUi
import com.halo.app.ui.components.HaloPresenceState
import com.halo.runtime.kernel.ProcessState

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Activity : Screen("activity", "Activity", Icons.Default.Timer)
    data object Devices : Screen("devices", "Devices", Icons.Default.Devices)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.Activity, Screen.Devices, Screen.Settings)

@Composable
fun AppShell() {
    val navController = rememberNavController()

    // Home is the start destination, so it is the only screen ViewModel that
    // should be materialized during first composition. The log showed settings,
    // intelligence, devices and pairing all being constructed before frame 1,
    // which also kicked provider discovery and hardware setup on startup.
    val homeViewModel = ManualDependencyGraph.homeViewModel

    fun navigateSafely(route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    // Home owns primary dashboard shortcuts, so its navigation stream must be
    // collected while the shell is alive. This was previously missing entirely.
    LaunchedEffect(homeViewModel) {
        homeViewModel.navigationEvents.collectLatest(::navigateSafely)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HaloGlassColors.Background,
        bottomBar = {
            HaloGlassNavigation(
                navController = navController,
                items = bottomNavItems.map { NavigationItem(it.route, it.title, it.icon) }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    val engines = remember {
                        listOf(
                            EngineStatusUi("Cognition", ProcessState.Running),
                            EngineStatusUi("Sensors", ProcessState.Running),
                            EngineStatusUi("Network", ProcessState.Paused),
                            EngineStatusUi("Embodiment", ProcessState.Running)
                        )
                    }

                    val providers = remember {
                        listOf(
                            ProviderStatusUi("MQTT Broker", "Connected", isOnline = true),
                            ProviderStatusUi("Lighting", "Synced", isOnline = true),
                            ProviderStatusUi("Cameras", "Offline", isOnline = false)
                        )
                    }

                    val events = remember {
                        listOf(
                            ConsoleEventUi("12:24", "Scene executed", "Evening lights routine completed."),
                            ConsoleEventUi("12:20", "Device joined", "New thermostat paired."),
                            ConsoleEventUi("12:16", "Provider warning", "Camera provider lost connection.")
                        )
                    }

                    HaloConsoleScreen(
                        engines = engines,
                        providers = providers,
                        events = events,
                        initialPresenceState = HaloPresenceState.Idle,
                        onOrbTap = { /* hook into voice / interaction */ },
                        onEngineTap = { engine -> /* open engine diagnostics */ },
                        onProviderTap = { provider -> /* open provider detail */ },
                        onEventTap = { event -> /* open log / detail */ }
                    )
                }

                composable(Screen.Activity.route) {
                    ActivityScreen(ManualDependencyGraph.activityViewModel)
                }

                composable(Screen.Devices.route) {
                    val devicesViewModel = ManualDependencyGraph.devicesViewModel
                    LaunchedEffect(devicesViewModel) {
                        devicesViewModel.navigationEvents.collectLatest(::navigateSafely)
                    }
                    DevicesScreen(devicesViewModel)
                }

                composable(
                    "device/{deviceId}",
                    arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
                ) { entry ->
                    val devicesViewModel = ManualDependencyGraph.devicesViewModel
                    val deviceId = entry.arguments?.getString("deviceId")
                    val devicesState by devicesViewModel.uiState.collectAsState()

                    LaunchedEffect(devicesViewModel) {
                        devicesViewModel.navigationEvents.collectLatest(::navigateSafely)
                    }

                    val deviceType = devicesState.devices
                        .firstOrNull { it.id == deviceId }
                        ?.type
                        .orEmpty()

                    when {
                        deviceId == null -> navController.popBackStack()
                        deviceType.contains("BuddyBot", ignoreCase = true) -> BuddyBotControlScreen(
                            deviceId = deviceId,
                            viewModel = devicesViewModel,
                            onBack = { navController.popBackStack() }
                        )
                        deviceType.contains("HeyCyan", ignoreCase = true) ||
                            deviceType.contains("Glasses", ignoreCase = true) -> HeyCyanControlScreen(
                            deviceId = deviceId,
                            viewModel = devicesViewModel,
                            onBack = { navController.popBackStack() }
                        )
                        else -> navController.popBackStack()
                    }
                }

                composable(Screen.Settings.route) {
                    val settingsViewModel = ManualDependencyGraph.settingsViewModel
                    LaunchedEffect(settingsViewModel) {
                        settingsViewModel.navigationEvents.collectLatest(::navigateSafely)
                    }
                    SettingsScreen(settingsViewModel)
                }

                composable("ai_settings") {
                    val intelligenceViewModel = ManualDependencyGraph.intelligenceViewModel
                    LaunchedEffect(intelligenceViewModel) {
                        intelligenceViewModel.navigationEvents.collectLatest(::navigateSafely)
                    }
                    IntelligenceScreen(
                        viewModel = intelligenceViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("permissions") {
                    PermissionsScreen(onBack = { navController.popBackStack() })
                }

                composable("discovery") {
                    val pairingViewModel = ManualDependencyGraph.pairingViewModel
                    LaunchedEffect(pairingViewModel) {
                        pairingViewModel.navigationEvents.collectLatest { event ->
                            if (event == "back") navController.popBackStack()
                        }
                    }
                    PairingScreen(pairingViewModel)
                }

                composable("console") {
                    ConsoleScreen(
                        ManualDependencyGraph.consoleViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("inspector") {
                    RuntimeInspectorScreen(
                        ManualDependencyGraph.runtimeInspectorViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("protocol_lab") {
                    ProtocolLabScreen(
                        ManualDependencyGraph.protocolLabViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
