package com.halo.app.ui.developer.inspector

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

import androidx.lifecycle.viewModelScope
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.SpeechSessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.halo.app.ui.glass.components.HaloGlassDivider

@Composable
fun RuntimeInspectorScreen(viewModel: RuntimeInspectorViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    
    Column(Modifier.fillMaxSize().background(HaloGlassColors.Background)) {
        com.halo.app.ui.glass.components.HaloGlassBackHeader(
            title = "Runtime Inspector",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // Header
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recording", color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyLarge)
            Switch(checked = state.isRecording, onCheckedChange = { viewModel.setRecording(it) })
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Graph Section
        HaloGlassSection(title = "Cognitive Pipeline", modifier = Modifier.weight(1f)) {
            val stages = listOf("Observation", "Interpretation", "Reasoning", "Planning", "Execution")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stages.forEachIndexed { index, name ->
                    val isActive = state.activePipelineStage == name
                    Text(
                        text = name,
                        color = if (isActive) HaloGlassColors.Accent else HaloGlassColors.TextMuted,
                        style = if (isActive) HaloTypography.bodyLarge else HaloTypography.bodySmall,
                        fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else null
                    )
                    if (index < stages.size - 1) {
                        Text("→", color = HaloGlassColors.TextMuted)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Blackboard
        HaloGlassSection(title = "Blackboard Contents", modifier = Modifier.weight(1f)) {
            if (state.blackboard.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active thoughts", color = HaloGlassColors.TextMuted)
                }
            } else {
                LazyColumn {
                    items(state.blackboard.reversed()) { item ->
                        Text(item, color = HaloGlassColors.TextSecondary, style = HaloTypography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        HaloGlassDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

data class InspectorState(
    val isRecording: Boolean = false, 
    val blackboard: List<String> = emptyList(),
    val activePipelineStage: String = "Idle"
)

class RuntimeInspectorViewModel(
    private val blackboard: Blackboard,
    private val snapshotManager: RuntimeSnapshotSource
) : ViewModel() {
    private val _isRecording = MutableStateFlow(false)

    val uiState: StateFlow<InspectorState> = combine(
        _isRecording,
        blackboard.flow,
        snapshotManager.snapshot
    ) { recording, facts, snapshot ->
        val stage = when (snapshot.speech) {
            is SpeechSessionState.Capturing -> "Observation"
            is SpeechSessionState.Recognizing -> "Interpretation"
            is SpeechSessionState.Thinking -> "Reasoning"
            is SpeechSessionState.Processing -> "Planning"
            is SpeechSessionState.Speaking -> "Execution"
            else -> "Idle"
        }

        InspectorState(
            isRecording = recording,
            blackboard = facts.map { "[${it.source}] ${it.payload::class.simpleName}" },
            activePipelineStage = stage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InspectorState()
    )

    fun setRecording(recording: Boolean) { 
        _isRecording.update { recording }
    }
}
