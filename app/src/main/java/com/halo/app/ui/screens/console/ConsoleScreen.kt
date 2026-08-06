package com.halo.app.ui.screens.console

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.components.*
import com.halo.app.ui.theme.*

@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "CONSOLE",
            color = HaloTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val tabs = listOf("Runtime", "Engines", "Blackboard", "Providers", "Logs", "Performance")
        
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTab,
            containerColor = HaloBackground,
            contentColor = HaloTextPrimary,
            edgePadding = 0.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.selectedTab == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(title, color = if (uiState.selectedTab == index) HaloPrimary else HaloTextSecondary) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        when (uiState.selectedTab) {
            0 -> RuntimeTab(uiState)
            1 -> EnginesTab(uiState)
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Not yet implemented for Alpha", color = HaloTextSecondary)
            }
        }
    }
}

@Composable
fun RuntimeTab(uiState: ConsoleUiState) {
    HaloCard {
        HaloSection("System") {
            HaloMetric("Uptime", uiState.uptime)
            Spacer(modifier = Modifier.height(8.dp))
            HaloMetric("Memory Usage", uiState.memoryUsage)
            Spacer(modifier = Modifier.height(8.dp))
            HaloMetric("Thread Count", uiState.threadCount.toString())
        }
    }
}

@Composable
fun EnginesTab(uiState: ConsoleUiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(uiState.engines) { engine ->
            HaloCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = engine.name, color = HaloPrimary, fontWeight = FontWeight.Bold)
                    HaloStatus("", if (engine.isHealthy) "Healthy" else "Offline", engine.isHealthy)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HaloMetric("Latency", "${engine.latencyMs} ms")
                    HaloMetric("Queue", "${engine.queueSize}")
                }
            }
        }
    }
}
