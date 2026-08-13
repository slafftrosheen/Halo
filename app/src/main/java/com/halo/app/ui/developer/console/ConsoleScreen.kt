package com.halo.app.ui.developer.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
    ) {
        HaloGlassBackHeader(
            title = "Console",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // Top Tabs
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConsoleTab.values().forEachIndexed { index, tab ->
                val selected = uiState.selectedTab == index
                HaloGlassButton(
                    text = tab.label,
                    onClick = { viewModel.selectTab(index) },
                    modifier = Modifier.weight(1f),
                    material = if (selected) HaloGlassMaterials.Interactive.copy(
                        containerColor = HaloGlassColors.Accent.copy(alpha = 0.3f)
                    ) else HaloGlassMaterials.Interactive
                )
            }
        }

        // Content Area
        Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (ConsoleTab.values()[uiState.selectedTab]) {
                ConsoleTab.Runtime -> RuntimeTabContent(uiState)
                ConsoleTab.Engines -> EnginesTabContent(uiState)
                ConsoleTab.Logs -> LogsTabContent(uiState.logs)
                ConsoleTab.Blackboard -> BlackboardTabContent(uiState.blackboardFacts)
                else -> PlaceholderContent(ConsoleTab.values()[uiState.selectedTab].label)
            }
        }
    }
}

@Composable
private fun RuntimeTabContent(state: ConsoleUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HaloGlassSection(title = "Health") {
            Column(Modifier.padding(8.dp)) {
                HaloGlassMetric(label = "Uptime", value = state.uptime)
                HaloGlassMetric(label = "Memory", value = state.memoryUsage)
                HaloGlassMetric(label = "Threads", value = state.threadCount.toString())
            }
        }
    }
}

@Composable
private fun EnginesTabContent(state: ConsoleUiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.engines) { engine ->
            HaloGlassCard {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = engine.name, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                        if (engine.isHealthy) {
                            HaloGlassChip.Success(text = "Healthy")
                        } else {
                            HaloGlassChip.Danger(text = "Failing")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Latency: ${engine.latencyMs?.let { "${it}ms" } ?: "--"}", 
                        style = HaloTypography.labelMedium,
                        color = HaloGlassColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsTabContent(logs: List<String>) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No logs yet.", color = HaloGlassColors.TextSecondary, style = HaloTypography.monoMedium)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs.reversed()) { log ->
                Text(log, color = HaloGlassColors.TextPrimary, style = HaloTypography.monoSmall)
            }
        }
    }
}

@Composable
private fun BlackboardTabContent(facts: List<String>) {
    if (facts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Blackboard is empty.", color = HaloGlassColors.TextSecondary, style = HaloTypography.monoMedium)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(facts.reversed()) { fact ->
                HaloGlassCard {
                    Text(fact, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodySmall, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("$title view coming soon", color = HaloGlassColors.TextSecondary)
    }
}

enum class ConsoleTab(val label: String) {
    Runtime("System"),
    Engines("Engines"),
    Logs("Logs"),
    Blackboard("Board"),
    Performance("Perf")
}
