package com.halo.app.ui.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Composable
fun RuntimeInspectorScreen(viewModel: RuntimeInspectorViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    Column(Modifier.fillMaxSize().background(HaloGlassColors.Background)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Runtime Inspector", color = HaloGlassColors.TextPrimary, style = HaloTypography.displayMedium)
            Switch(checked = state.isRecording, onCheckedChange = { viewModel.setRecording(it) })
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Graph Section
        HaloGlassSection(title = "Cognitive Pipeline", modifier = Modifier.weight(1f)) {
            Text("Observation → Interpretation → Reasoning → Planning → Execution", color = HaloGlassColors.TextSecondary)
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Blackboard
        HaloGlassSection(title = "Blackboard Contents", modifier = Modifier.weight(1f)) {
            if (state.blackboard.isEmpty()) {
                Text("No active thoughts", color = HaloGlassColors.TextMuted)
            } else {
                state.blackboard.forEach { item ->
                    Text(item, color = HaloGlassColors.TextSecondary, style = HaloTypography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

data class InspectorState(val isRecording: Boolean = false, val blackboard: List<String> = emptyList())

class RuntimeInspectorViewModel : ViewModel() {
    private val _state = MutableStateFlow(InspectorState())
    val uiState: StateFlow<InspectorState> = _state.asStateFlow()
    fun setRecording(recording: Boolean) { 
        _state.update { it.copy(isRecording = recording) }
    }
}
