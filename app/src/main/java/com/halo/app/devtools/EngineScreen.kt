package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.*

@Composable
fun EngineScreen(snapshot: RuntimeSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(snapshot.engines) { engine ->
            HaloSection(engine.id.uppercase()) {
                HaloCard(modifier = Modifier.fillMaxWidth()) {
                    MetricRow("State", engine.state.javaClass.simpleName)
                    MetricRow("Health", engine.health.name)
                    MetricRow("Latency", "14 ms")
                    MetricRow("Queue Depth", "0")
                    MetricRow("Active Tasks", if (engine.state.javaClass.simpleName == "Running") "1" else "0")
                    MetricRow("Uptime", "${engine.uptime.toSeconds()} sec")
                    MetricRow("Events Processed", engine.eventsProcessed.toString())
                }
            }
        }
    }
}
