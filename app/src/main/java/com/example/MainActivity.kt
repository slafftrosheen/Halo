package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.RuntimeState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val runtime = ManualDependencyGraph.runtime
        val pipeline = ManualDependencyGraph.voicePipeline
        
        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val runtimeState by runtime.state.collectAsState()
                val pipelineState by pipeline.state.collectAsState()
                val pipelineHealth by pipeline.health.collectAsState()
                
                LaunchedEffect(Unit) {
                    runtime.boot()
                }
                
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Halo Platform",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Runtime Kernel: $runtimeState", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Voice Pipeline: $pipelineState", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pipeline Health: $pipelineHealth", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { 
                                scope.launch {
                                    if (runtimeState == RuntimeState.Running) {
                                        runtime.shutdown()
                                    } else if (runtimeState == RuntimeState.Offline) {
                                        runtime.boot()
                                    }
                                }
                            }
                        ) {
                            Text(if (runtimeState == RuntimeState.Running) "Shutdown" else "Boot Kernel")
                        }
                    }
                }
            }
        }
    }
}
