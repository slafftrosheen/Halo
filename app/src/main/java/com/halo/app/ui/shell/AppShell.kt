package com.halo.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.glass.components.HaloGlassNavigation
import com.halo.app.ui.glass.components.NavigationItem
import androidx.compose.material3.MaterialTheme
import com.halo.app.ui.screens.activity.ActivityScreen
import com.halo.app.ui.screens.devices.BuddyBotControlScreen
import com.halo.app.ui.screens.devices.DevicesScreen
import com.halo.app.ui.screens.devices.HeyCyanControlScreen
import com.halo.app.ui.screens.discovery.PairingScreen
import com.halo.app.ui.screens.home.HomeScreen
import com.halo.app.ui.screens.notebook.NoteEditorScreen
import com.halo.app.ui.screens.notebook.NoteEditorViewModel
import com.halo.app.ui.screens.notebook.NotebookNavigation
import com.halo.app.ui.screens.notebook.NotebookScreen
import com.halo.app.ui.screens.settings.IntelligenceScreen
import com.halo.app.ui.screens.settings.SettingsScreen
import com.halo.app.ui.screens.settings.VoiceSettingsScreen
import kotlinx.coroutines.flow.collectLatest

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Activity : Screen("activity", "Activity", Icons.Default.Timer)
    data object Notebook : Screen("notebook", "Notebook", Icons.Default.Book)
    data object Devices : Screen("devices", "Devices", Icons.Default.Devices)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.Activity, Screen.Notebook, Screen.Devices, Screen.Settings)

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val homeViewModel = ManualDependencyGraph.homeViewModel

    fun navigateSafely(route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.navigationEvents.collectLatest(::navigateSafely)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                composable(Screen.Home.route) { HomeScreen(homeViewModel) }
                composable(Screen.Activity.route) { ActivityScreen(ManualDependencyGraph.activityViewModel) }

                composable(Screen.Notebook.route) {
                    val notebookViewModel = ManualDependencyGraph.notebookViewModel
                    LaunchedEffect(notebookViewModel) {
                        notebookViewModel.navigationEvents.collectLatest { event ->
                            when (event) {
                                is NotebookNavigation.OpenNote -> navigateSafely("notebook/note/${event.id}")
                            }
                        }
                    }
                    NotebookScreen(notebookViewModel)
                }

                composable(
                    "notebook/note/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                ) { entry ->
                    val noteId = entry.arguments?.getString("noteId")
                    if (noteId == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val editorViewModel: NoteEditorViewModel = viewModel(
                            key = "note-editor-$noteId",
                            factory = NoteEditorViewModel.Factory(noteId, ManualDependencyGraph.notebookUseCases)
                        )
                        NoteEditorScreen(editorViewModel) { navController.popBackStack() }
                    }
                }

                composable(Screen.Devices.route) {
                    val viewModel = ManualDependencyGraph.devicesViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest(::navigateSafely)
                    }
                    DevicesScreen(viewModel)
                }

                composable("device/{deviceId}", arguments = listOf(navArgument("deviceId") { type = NavType.StringType })) { entry ->
                    val viewModel = ManualDependencyGraph.devicesViewModel
                    val deviceId = entry.arguments?.getString("deviceId")
                    val state by viewModel.uiState.collectAsState()
                    val deviceKind = state.devices.firstOrNull { it.id == deviceId }?.kind
                    when {
                        deviceId == null -> navController.popBackStack()
                        deviceKind == com.halo.sdk.runtime.embodiment.DeviceKind.BUDDYBOT -> BuddyBotControlScreen(deviceId, viewModel) { navController.popBackStack() }
                        deviceKind == com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES -> HeyCyanControlScreen(deviceId, viewModel) { navController.popBackStack() }
                        else -> navController.popBackStack()
                    }
                }

                composable(Screen.Settings.route) {
                    val viewModel = ManualDependencyGraph.settingsViewModel
                    LaunchedEffect(viewModel) { viewModel.navigationEvents.collectLatest(::navigateSafely) }
                    SettingsScreen(viewModel)
                }

                composable("ai_settings") {
                    val viewModel = ManualDependencyGraph.intelligenceViewModel
                    LaunchedEffect(viewModel) { viewModel.navigationEvents.collectLatest(::navigateSafely) }
                    IntelligenceScreen(viewModel) { navController.popBackStack() }
                }

                composable("voice_settings") {
                    val viewModel = ManualDependencyGraph.voiceSettingsViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest { if (it == "back") navController.popBackStack() }
                    }
                    VoiceSettingsScreen(viewModel) { navController.popBackStack() }
                }

                composable("permissions") { PermissionsScreen { navController.popBackStack() } }
                composable("discovery") {
                    val viewModel = ManualDependencyGraph.pairingViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest { if (it == "back") navController.popBackStack() }
                    }
                    PairingScreen(viewModel)
                }
            }
        }
    }
}
