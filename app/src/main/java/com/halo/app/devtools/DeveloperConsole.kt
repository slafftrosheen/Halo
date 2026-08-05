package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.ui.theme.HaloSurface
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.viewmodel.DeveloperConsoleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperConsole(viewModel: DeveloperConsoleViewModel = viewModel()) {
    val snapshot by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf(
        "Runtime",
        "Engines",
        "Services",
        "Plugins",
        "Timeline",
        "Settings",
        "Knowledge",
        "Interpretation",
        "Reasoning"
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Halo DevTools", color = HaloTextPrimary, fontFamily = FontFamily.Monospace) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontFamily = FontFamily.Monospace) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> RuntimeScreen(snapshot)
                1 -> EngineScreen(snapshot.engines)
                2 -> ServiceScreen(snapshot.services)
                3 -> PluginScreen(snapshot.plugins)
                4 -> TimelineScreen()
                5 -> SettingsScreen()
                6 -> KnowledgeScreen()
                7 -> InterpretationScreen()
                8 -> ReasoningScreen()
            }
        }
    }
}
