package com.halo.app.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

enum class ConsoleTab(val label: String) {
    Logs("Logs"),
    Engines("Engines"),
    Blackboard("Blackboard"),
    Providers("Providers"),
    Performance("Performance")
}

data class ConsoleUiState(
    val selectedTab: ConsoleTab = ConsoleTab.Logs,
    val logs: List<String> = emptyList(),
    val engines: List<String> = emptyList(),
    val blackboard: List<String> = emptyList(),
    val providers: List<String> = emptyList()
)

@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
    ) {
        // Top Tabs
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConsoleTab.values().forEach { tab ->
                HaloGlassChip(
                    text = tab.label,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.selectTab(tab) }
                ).also {
                    if (uiState.selectedTab == tab) {
                        // Active tab
                    }
                }
            }
        }

        // Content Area
        Spacer(modifier = Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (uiState.selectedTab) {
                ConsoleTab.Logs -> ConsoleLogs(uiState.logs)
                ConsoleTab.Engines -> ConsoleEngines(uiState.engines)
                ConsoleTab.Blackboard -> ConsoleBlackboard(uiState.blackboard)
                ConsoleTab.Providers -> ConsoleProviders(uiState.providers)
                ConsoleTab.Performance -> ConsolePerformance()
            }
        }
    }
}