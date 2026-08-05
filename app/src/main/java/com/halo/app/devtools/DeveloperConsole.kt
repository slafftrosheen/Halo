package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.ui.theme.*
import com.halo.app.ui.viewmodel.DeveloperConsoleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperConsole(viewModel: DeveloperConsoleViewModel = viewModel()) {
    val snapshot by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf(
        "Runtime",
        "Engines",
        "Timeline",
        "Blackboard",
        "Memory",
        "Providers",
        "Performance",
        "Logs"
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Halo DevTools", color = HaloTextPrimary, fontFamily = FontFamily.Monospace) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HaloBackground)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = HaloBackground,
                    contentColor = HaloPrimary,
                    edgePadding = 0.dp,
                    divider = { HorizontalDivider(color = HaloDivider) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Text(
                                    title, 
                                    fontFamily = FontFamily.Monospace,
                                    color = if (selectedTab == index) HaloPrimary else HaloTextSecondary
                                ) 
                            }
                        )
                    }
                }
            }
        },
        containerColor = HaloBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> RuntimeScreen(snapshot)
                1 -> EngineScreen(snapshot)
                2 -> TimelineScreen()
                3 -> BlackboardScreen()
                4 -> Text("Memory Inspector (Coming Soon)", modifier = Modifier.padding(16.dp), color = HaloTextPrimary, fontFamily = FontFamily.Monospace)
                5 -> ProvidersScreen()
                6 -> Text("Performance (Coming Soon)", modifier = Modifier.padding(16.dp), color = HaloTextPrimary, fontFamily = FontFamily.Monospace)
                7 -> Text("Logs (Coming Soon)", modifier = Modifier.padding(16.dp), color = HaloTextPrimary, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

