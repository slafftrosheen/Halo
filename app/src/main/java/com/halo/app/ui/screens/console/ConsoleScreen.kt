package com.halo.app.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

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
                val selected = uiState.selectedTab == tab.ordinal
                HaloGlassChip(
                    text = tab.label,
                    modifier = Modifier.weight(1f),
                    material = if (selected) com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                        containerColor = HaloGlassColors.Accent
                    ) else com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive
                )
            }
        }

        // Content Area
        Spacer(modifier = Modifier.height(8.dp))
        Box(Modifier.weight(1f).padding(16.dp)) {
            Column {
                Text("Uptime: ${uiState.uptime}", color = HaloGlassColors.TextPrimary)
                Text("Memory: ${uiState.memoryUsage}", color = HaloGlassColors.TextPrimary)
                Text("Threads: ${uiState.threadCount}", color = HaloGlassColors.TextPrimary)
                
                Spacer(Modifier.height(16.dp))
                
                Text("Engines:", style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                uiState.engines.forEach { engine ->
                    Text("${engine.name}: ${if (engine.isHealthy) "Healthy" else "Failing"}", color = HaloGlassColors.TextSecondary)
                }
            }
        }
    }
}

enum class ConsoleTab(val label: String) {
    Logs("Logs"),
    Engines("Engines"),
    Blackboard("Blackboard"),
    Providers("Providers"),
    Performance("Performance")
}
