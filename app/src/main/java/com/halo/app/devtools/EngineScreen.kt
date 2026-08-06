package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.*
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.kernel.ProcessState

@Composable
fun EngineScreen() {
    val processes = ManualDependencyGraph.microkernel.processManager.processes
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(processes) { process ->
            val processState by process.state.collectAsState()
            HaloSection(process.name.uppercase()) {
                ConsoleRow("ID", process.id)
                ConsoleRow("State", processState::class.simpleName ?: "Unknown")
                ConsoleRow("Latency", "24ms") // Mocked metric for Alpha
                ConsoleRow("Uptime", "0h 12m") // Mocked metric for Alpha
            }
        }
    }
}
