package com.halo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.icl.core.CognitiveObject
import com.halo.runtime.kernel.HaloInteractionState
import com.halo.app.di.ManualDependencyGraph
import com.halo.host.android.AndroidSpeechProcess
import kotlinx.coroutines.flow.StateFlow
import com.halo.engines.memory.WorkingMemoryProcess

class AlphaDashboardViewModel : ViewModel() {
    val interactionState: StateFlow<HaloInteractionState> = ManualDependencyGraph.microkernel.interactionState
    
    val cognitiveTrace: StateFlow<List<CognitiveObject>> = ManualDependencyGraph.workingMemoryProcess.recentContext
    
    private val speechProcess = ManualDependencyGraph.androidSpeechProcess

    fun startListening() {
        ManualDependencyGraph.microkernel.setInteractionState(HaloInteractionState.Listening)
        speechProcess.startCapturing()
    }
    
    fun stopListening() {
        speechProcess.stopCapturing()
    }
}

class AlphaDashboardViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AlphaDashboardViewModel() as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphaDashboardScreen(viewModel: AlphaDashboardViewModel = viewModel(factory = AlphaDashboardViewModelFactory())) {
    val trace by viewModel.cognitiveTrace.collectAsState()
    val state by viewModel.interactionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mission Control", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF9FAFB))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusRow("Runtime", "Ready")
                    StatusRow("Speech", "Ready")
                    StatusRow("Reasoning", "Ready")
                    StatusRow("Memory", "Ready")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Halo is ${state.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (state == HaloInteractionState.Idle) Color.Gray else Color(0xFF0F172A)
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            var isPressed by remember { mutableStateOf(false) }
            
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(if (isPressed) Color(0xFF3B82F6) else Color(0xFF1E293B), shape = CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                viewModel.startListening()
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                    viewModel.stopListening()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPressed) "LISTENING" else "HOLD",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Cognitive Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trace.reversed()) { obj ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = obj::class.simpleName ?: "Unknown", style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = obj.toString(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = status, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
        }
    }
}
