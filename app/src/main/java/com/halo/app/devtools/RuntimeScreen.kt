package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.*
import com.halo.app.ui.components.*

@Composable
fun RuntimeScreen(snapshot: RuntimeSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HaloSection("RUNTIME STATE") {
                HaloCard(modifier = Modifier.fillMaxWidth()) {
                    MetricRow("Status", snapshot.runtimeState.name)
                    MetricRow("Uptime", "${snapshot.uptime.toSeconds()} sec")
                }
            }
        }
        item {
            HaloSection("SYSTEM HEALTH") {
                HaloCard(modifier = Modifier.fillMaxWidth()) {
                    MetricRow("Engines", "${snapshot.engines.count { it.state.javaClass.simpleName == "Running" }}/${snapshot.engines.size} Running")
                    MetricRow("Active Tasks", snapshot.activeTasks.toString())
                    MetricRow("Queue Depth", snapshot.queueDepth.toString())
                }
            }
        }
        item {
            HaloSection("INTERACTIVE RUNTIME GRAPH") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GraphNode("Runtime", "Active", "0ms", "0", 0)
                    GraphNode("EventBus", "Routing", "1ms", "124", 1)
                    GraphNode("CommandBus", "Idle", "0ms", "12", 1)
                    GraphNode("Scheduler", "Tick", "4ms", "1024", 1)
                    snapshot.engines.forEach { engine ->
                        GraphNode(
                            name = engine.id, 
                            status = engine.state.javaClass.simpleName, 
                            latency = "14ms", 
                            events = engine.eventsProcessed.toString(), 
                            depth = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GraphNode(name: String, status: String, latency: String, events: String, depth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 24).dp, top = 8.dp, bottom = 8.dp)
            .background(HaloSurface, shape = RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(name, color = HaloTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Text(status, color = HaloPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(latency, color = HaloTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("$events events", color = HaloTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ConsoleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = HaloTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(value, color = HaloTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}
