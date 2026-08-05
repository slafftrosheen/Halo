package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.BrainToolApplication
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryListScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SkillsScreen
import com.example.ui.theme.HaloAccent
import com.example.ui.theme.HaloSurface
import com.example.ui.theme.HaloTextPrimary
import com.example.ui.theme.HaloTextSecondary
import com.example.ui.viewmodel.MemoryViewModel
import com.example.ui.viewmodel.MemoryViewModelFactory

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Activity : Screen("activity", "Activity", Icons.Default.List)
    object Skills : Screen("skills", "Skills", Icons.Default.CheckCircle)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    
    // Sub-screens
    object Assistant : Screen("assistant", "Assistant", Icons.Default.Settings)
    object Privacy : Screen("privacy", "Privacy", Icons.Default.Settings)
    object Memory : Screen("memory", "Memory", Icons.Default.List)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Activity,
    Screen.Skills,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as BrainToolApplication
    val memoryViewModel: MemoryViewModel = viewModel(
        factory = MemoryViewModelFactory(app.memoryRepository)
    )

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
                            selectedIconColor = HaloAccent,
                            selectedTextColor = HaloAccent,
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
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Activity.route) { ActivityScreen() }
            composable(Screen.Skills.route) { 
                SkillsScreen(onNavigateToMemory = { navController.navigate(Screen.Memory.route) }) 
            }
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onNavigateToAssistant = { navController.navigate(Screen.Assistant.route) }
                ) 
            }
            composable(Screen.Assistant.route) { AssistantScreen() }
            composable(Screen.Privacy.route) { PrivacyScreen() }
            composable(Screen.Memory.route) { MemoryListScreen(viewModel = memoryViewModel) }
        }
    }
}
