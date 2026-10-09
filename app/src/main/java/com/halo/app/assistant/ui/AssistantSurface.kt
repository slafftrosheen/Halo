package com.halo.app.assistant.ui

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.interaction.InteractionSurfaceRenderer
import com.halo.app.interaction.SurfaceRenderMode
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionSurface
import com.halo.sdk.runtime.InteractionTurnState

/** Native assistant host, using the same presence, theme and semantic interaction renderer. */
@Composable
fun AssistantSurface(
    interactionState: InteractionTurnState?,
    screenContextAttached: Boolean,
    onStopClicked: () -> Unit,
    onOpenHaloClicked: () -> Unit,
    interactionSurface: InteractionSurface? = null,
    currentApp: String? = null,
    onInteractionRespond: (InteractionResponse) -> Unit = {},
) {
    val speaking by com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.playbackActive.collectAsState()
    val signal = remember(interactionState, interactionSurface?.inputRequestId, speaking) {
        if (speaking) HaloSignalPresentation(HaloSignalState.SPEAKING, label = "Speaking", detail = "Here's what I found.") else
        when (interactionState) {
            is InteractionTurnState.Listening -> HaloSignalPresentation(HaloSignalState.LISTENING, label = "Listening", detail = "Go ahead, I'm listening.")
            is InteractionTurnState.Transcribing -> HaloSignalPresentation(HaloSignalState.CAPTURING, HaloGlyph.LISTEN, "Understanding", "Turning your voice into words.")
            is InteractionTurnState.Preparing, is InteractionTurnState.Generating -> HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.HALO, "Thinking", "A little clarity is on the way.")
            is InteractionTurnState.Speaking -> HaloSignalPresentation(HaloSignalState.SPEAKING, label = "Speaking", detail = "Here's what I found.")
            is InteractionTurnState.Failed -> HaloSignalPresentation(HaloSignalState.ERROR, HaloGlyph.OFFLINE, "Something went wrong", "Open Halo to review this and try again.")
            is InteractionTurnState.Cancelled, is InteractionTurnState.Interrupted -> HaloSignalPresentation(HaloSignalState.STOPPED, HaloGlyph.HALO, "Stopped", "Ready whenever you need me.")
            is InteractionTurnState.Completed -> HaloSignalPresentation(HaloSignalState.SUCCESS, label = "All done", detail = "Your conversation continues in Halo.")
            else -> HaloSignalPresentation(label = "Hello, I'm Halo", detail = "What would you like to explore?")
        }.let { state ->
            if (interactionSurface?.inputRequestId != null) state.copy(state = HaloSignalState.WAITING, glyph = HaloGlyph.APPROVAL,
                label = "Your input is needed", detail = "Review the request below.") else state
        }
    }
    val busy = speaking || interactionState is InteractionTurnState.Listening || interactionState is InteractionTurnState.Transcribing ||
        interactionState is InteractionTurnState.Preparing || interactionState is InteractionTurnState.Generating || interactionState is InteractionTurnState.Speaking
    BoxWithConstraints(Modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.BottomCenter) {
        val panelHeight = maxHeight.coerceAtMost(560.dp)
        Surface(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(max = panelHeight),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HaloSignalFace(signal, size = 80.dp, contentDescription = "Halo assistant")
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Halo", style = MaterialTheme.typography.titleMedium)
                        currentApp?.let { Text("In · $it", style = MaterialTheme.typography.labelSmall) }
                        Text(uiText(signal.label), style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        signal.detail?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                // Scrolling requests stay bounded; dismissal never answers the durable request.
                interactionSurface?.let { surface ->
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        InteractionSurfaceRenderer(surface = surface, onRespond = onInteractionRespond, mode = SurfaceRenderMode.COMPACT)
                    }
                }
                if (screenContextAttached) {
                    Text("Current screen included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onStopClicked, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (busy) "Stop" else "Close")
                    }
                    Button(onClick = onOpenHaloClicked, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Open Halo") }
                }
            }
        }
    }
}
