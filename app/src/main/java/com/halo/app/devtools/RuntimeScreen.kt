package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.HaloSurface
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary

@Composable
fun RuntimeScreen(snapshot: RuntimeSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ConsoleSection("Runtime State") {
                ConsoleRow("Status", snapshot.runtimeState.name)
                ConsoleRow("Uptime", "${snapshot.uptime.toSeconds()} sec")
            }
        }
        item {
            ConsoleSection("System Health") {
                ConsoleRow("Engines", "${snapshot.engines.count { it.state.javaClass.simpleName == "Running" }}/${snapshot.engines.size} Running")
                ConsoleRow("Active Tasks", snapshot.activeTasks.toString())
                ConsoleRow("Queue Depth", snapshot.queueDepth.toString())
            }
        }
        item {
            ConsoleSection("Runtime Graph") {
                Text(
                    text = """
                        Runtime
                        ├── EventBus
                        ├── CommandBus
                        ├── Scheduler
                        ├── Voice
                        │   └── AI
                        │       └── Planner
                        │           └── Execution
                        │               └── Camera Plugin
                    """.trimIndent(),
                    color = HaloTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ConsoleSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HaloSurface, shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = HaloTextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
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
