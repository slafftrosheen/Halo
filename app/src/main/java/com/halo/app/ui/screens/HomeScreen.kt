package com.halo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.LocalHaloHost
import com.halo.app.ui.components.HaloDividerLine
import com.halo.app.ui.components.StateTile
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.RuntimeState
import com.halo.sdk.runtime.EngineState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val runtime = ManualDependencyGraph.runtime
    val aiEngine = ManualDependencyGraph.aiEngine
    val audioPerceptionEngine = ManualDependencyGraph.audioPerceptionEngine
    val reasoningEngine = ManualDependencyGraph.reasoningEngine
    val scope = rememberCoroutineScope()
    
    val haloHost = LocalHaloHost.current
    
    val runtimeState by runtime.state.collectAsState()
    val aiState by aiEngine.state.collectAsState()
    val audioPerceptionState by audioPerceptionEngine.state.collectAsState()
    val aiHealth by aiEngine.health.collectAsState()
    
    var simText by remember { mutableStateOf("What's the weather?") }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Text(text = "Halo Platform", style = MaterialTheme.typography.displayLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Kernel: $runtimeState", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Text(text = "AI: $aiState", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                Text(text = "Audio Perception: $audioPerceptionState", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                Text(text = "AI Health: $aiHealth", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                HaloDividerLine()
            }
            
            item {
                Text(text = "Controls", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            when (runtimeState) {
                                RuntimeState.Running -> runtime.shutdown()
                                RuntimeState.Offline -> runtime.boot()
                                else -> Unit
                            }
                        }
                    }
                ) {
                    Text(
                        when (runtimeState) {
                            RuntimeState.Running -> "Shutdown Kernel"
                            RuntimeState.Offline -> "Boot Kernel"
                            else -> "Working..."
                        }
                    )
                }
                HaloDividerLine()
            }
            
            item {
                Text(text = "Simulate Speech Pipeline", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = simText,
                    onValueChange = { simText = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        haloHost?.microphone?.simulateSpeech(simText)
                        simText = ""
                    }
                ) {
                    Text("Send to AI Engine")
                }
                HaloDividerLine()
            }
            
            item {
                Text(text = "Today", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                StateTile(title = "Meeting", value = "10:30")
                Spacer(modifier = Modifier.height(8.dp))
                StateTile(title = "Reminders", value = "2 pending")
            }
        }
    }
}
