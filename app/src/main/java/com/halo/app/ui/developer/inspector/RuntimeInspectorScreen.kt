package com.halo.app.ui.developer.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.provider.AiProviderDiagnostic
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.SpeechSessionState
import com.halo.runtime.observer.SpeechTurnMetrics
import com.halo.runtime.observer.SpeechTurnTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun RuntimeInspectorScreen(viewModel: RuntimeInspectorViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(HaloGlassColors.Background)) {
        HaloGlassBackHeader(
            title = "Runtime Inspector",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HaloGlassSection(title = "Cognitive Pipeline") {
                val stages = listOf("Observation", "Interpretation", "Reasoning", "Planning", "Execution")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
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

            HaloGlassSection(
                title = "PROVIDER DIAGNOSTICS",
                trailing = {
                    HaloGlassButton.Secondary(
                        text = "Test",
                        onClick = viewModel::testActiveConnection
                    )
                }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.diagnostics.isEmpty()) {
                        Text("No providers registered", style = HaloTypography.bodySmall, color = HaloGlassColors.TextMuted)
                    } else {
                        state.diagnostics.forEach { diag ->
                            ProviderDiagnosticRow(diag)
                        }
                    }
                }
            }

            if (state.nativeResources.isNotEmpty()) {
                HaloGlassSection(title = "NATIVE RESOURCES") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.nativeResources.forEach { res ->
                            Text(res, style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
                        }
                    }
                }
            }

            HaloGlassSection(title = "SPEECH TURN METRICS") {
                if (state.speechTurns.isEmpty()) {
                    Text(
                        "No turns recorded",
                        style = HaloTypography.bodySmall,
                        color = HaloGlassColors.TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val recentTurns = state.speechTurns.reversed().take(5)
                        recentTurns.forEachIndexed { index, turn ->
                            SpeechTurnRow(turn)
                            if (index < recentTurns.lastIndex) {
                                HaloGlassDivider()
                            }
                        }
                    }
                }
            }

            HaloGlassSection(title = "Blackboard", modifier = Modifier.weight(1f)) {
                if (state.blackboard.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No active thoughts", color = HaloGlassColors.TextMuted, style = HaloTypography.bodySmall)
                    }
                } else {
                    LazyColumn {
                        items(state.blackboard.reversed()) { item ->
                            Text(item, color = HaloGlassColors.TextSecondary, style = HaloTypography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            HaloGlassDivider()
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechTurnRow(turn: SpeechTurnMetrics) {
    val totalColor = when {
        turn.failed -> HaloGlassColors.Danger
        turn.totalMs < 3000 -> HaloGlassColors.Success
        turn.totalMs < 6000 -> HaloGlassColors.Warning
        else -> HaloGlassColors.Danger
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${turn.turnIndex}",
                style = HaloTypography.labelSmall,
                color = HaloGlassColors.TextMuted
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "\"${turn.recognizedText}\"",
                style = HaloTypography.bodySmall,
                color = HaloGlassColors.TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${turn.totalMs}ms",
                style = HaloTypography.bodySmall,
                color = totalColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        if (turn.failed) {
            Text(
                "Failed: ${turn.failureReason ?: "unknown"}",
                style = HaloTypography.labelSmall,
                color = HaloGlassColors.Danger
            )
        } else {
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TurnMetric("STT→Cog", turn.sttToCognitiveMs)
                TurnMetric("Cognitive", turn.cognitiveMs)
                TurnMetric("Cog→TTS", turn.cognitiveToTtsMs)
                TurnMetric("TTS", turn.ttsMs)
            }
        }
    }
}

@Composable
private fun TurnMetric(label: String, ms: Long) {
    val color = when {
        ms < 0 -> HaloGlassColors.TextMuted
        ms < 1000 -> HaloGlassColors.Success
        ms < 3000 -> HaloGlassColors.Warning
        else -> HaloGlassColors.Danger
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        Text(
            if (ms < 0) "—" else "${ms}ms",
            style = HaloTypography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ProviderDiagnosticRow(diag: AiProviderDiagnostic) {
    val statusColor = when (diag.state) {
        ProviderStatus.State.READY -> HaloGlassColors.Success
        ProviderStatus.State.STANDBY -> HaloGlassColors.Accent
        ProviderStatus.State.LOADING -> HaloGlassColors.Info
        ProviderStatus.State.MODEL_MISSING, ProviderStatus.State.ERROR -> HaloGlassColors.Danger
        else -> HaloGlassColors.TextMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).background(statusColor, HaloGlassTokens.circleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    diag.displayName,
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (diag.selected) {
                    Spacer(Modifier.width(6.dp))
                    HaloGlassChip(
                        text = "ACTIVE",
                        material = com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Accent.copy(alpha = 0.12f),
                            contentColor = HaloGlassColors.Accent
                        )
                    )
                }
            }
            val caps = diag.capabilities.joinToString(", ") { it.name }
            if (caps.isNotBlank()) {
                Text(caps, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
            }
            diag.message?.let {
                Text(it, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
            }
        }
    }
}

data class InspectorState(
    val isRecording: Boolean = false,
    val blackboard: List<String> = emptyList(),
    val activePipelineStage: String = "Idle",
    val diagnostics: List<AiProviderDiagnostic> = emptyList(),
    val nativeResources: List<String> = emptyList(),
    val speechTurns: List<SpeechTurnMetrics> = emptyList()
)

class RuntimeInspectorViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter? = null,
    private val speechTurnTracker: SpeechTurnTracker? = null
) : ViewModel() {

    val uiState: StateFlow<InspectorState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter?.diagnosticsFlow ?: MutableStateFlow(emptyList<AiProviderDiagnostic>()),
        speechTurnTracker?.turns ?: MutableStateFlow(emptyList<SpeechTurnMetrics>())
    ) { snapshot, diagnostics, turns ->
        val stage = when (snapshot.speech) {
            is SpeechSessionState.Capturing -> "Observation"
            is SpeechSessionState.Recognizing -> "Interpretation"
            is SpeechSessionState.Thinking -> "Reasoning"
            is SpeechSessionState.Processing -> "Planning"
            is SpeechSessionState.Speaking -> "Execution"
            else -> "Idle"
        }

        InspectorState(
            blackboard = snapshot.blackboard.recentFacts.map { "[${it.type}] ${it.id}" },
            activePipelineStage = stage,
            diagnostics = diagnostics,
            nativeResources = snapshot.nativeResources.map { "${it.kind.name}: ${it.state.name}" },
            speechTurns = turns
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InspectorState()
    )

    fun testActiveConnection() {
        viewModelScope.launch {
            val providerId = aiProviderRouter?.selectedProviderId?.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == providerId } ?: return@launch
            runCatching { provider.testConnection() }
        }
    }
}
