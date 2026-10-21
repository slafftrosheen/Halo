package com.halo.app.assistant.ui

import com.halo.app.ui.fx.haloStateAura
import com.halo.app.ui.fx.haloAssistantEdge
import com.halo.app.ui.fx.light
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
            is InteractionTurnState.Transcribing -> HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.LISTEN, "Understanding", "Turning your voice into words.")
            is InteractionTurnState.Preparing, is InteractionTurnState.Generating -> HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.HALO, "Thinking", "A little clarity is on the way.")
            is InteractionTurnState.Speaking -> HaloSignalPresentation(HaloSignalState.THINKING, label = "Responding", artwork = com.halo.app.ui.presence.PresenceArtwork.STREAMING_TEXT)
            is InteractionTurnState.Failed -> HaloSignalPresentation(HaloSignalState.ERROR, HaloGlyph.OFFLINE, "Something went wrong", "Open halo to review this and try again.")
            is InteractionTurnState.Cancelled, is InteractionTurnState.Interrupted -> HaloSignalPresentation(HaloSignalState.STOPPED, HaloGlyph.HALO, "Stopped", "Ready whenever you need me.")
            is InteractionTurnState.Completed -> HaloSignalPresentation(HaloSignalState.SUCCESS, label = "All done", detail = "Your conversation continues in halo.")
            else -> HaloSignalPresentation(label = "Hello, I'm halo", detail = "What would you like to explore?")
        }.let { state ->
            if (interactionSurface?.inputRequestId != null) state.copy(state = HaloSignalState.WAITING, glyph = HaloGlyph.APPROVAL, artwork = null,
                label = "Your input is needed", detail = "Review the request below.") else state
        }
    }
    val busy = speaking || interactionState is InteractionTurnState.Listening || interactionState is InteractionTurnState.Transcribing ||
        interactionState is InteractionTurnState.Preparing || interactionState is InteractionTurnState.Generating || interactionState is InteractionTurnState.Speaking
    BoxWithConstraints(Modifier.fillMaxWidth().imePadding()
        .haloAssistantEdge(active = interactionState is InteractionTurnState.Listening || interactionSurface?.inputRequestId != null,
            attention = interactionSurface?.inputRequestId != null, accent = MaterialTheme.colorScheme.primaryContainer,
            apricot = androidx.compose.ui.graphics.Color(0xFFFF9F6B)), contentAlignment = Alignment.BottomCenter) {
        var invoke by remember { mutableStateOf(interactionState is InteractionTurnState.Listening) }
        LaunchedEffect(Unit) { if (invoke) { kotlinx.coroutines.delay(900); invoke = false } }
        if (invoke) com.halo.app.ui.fx.HaloPackageFx(com.halo.app.ui.fx.HaloPackageMoment.ASSIST_INVOKE,
            Modifier.matchParentSize())
        val panelHeight = maxHeight.coerceAtMost(560.dp)
        Surface(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(max = panelHeight),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.haloStateAura(signal.state.light(), MaterialTheme.colorScheme.primaryContainer,
                androidx.compose.ui.graphics.Color(0xFFFF9F6B), MaterialTheme.colorScheme.error).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HaloSignalFace(signal, size = 80.dp, contentDescription = uiText("halo assistant"))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("halo", style = MaterialTheme.typography.titleMedium)
                        currentApp?.let { Text(uiText("In %1\$s", it), style = MaterialTheme.typography.labelSmall) }
                        Text(uiText(signal.label), style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        signal.detail?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                // Scrolling requests stay bounded; dismissal never answers the durable request.
                interactionSurface?.let { surface ->
                    Column(Modifier.fillMaxWidth()) {
                        InteractionSurfaceRenderer(surface = surface, onRespond = onInteractionRespond, mode = SurfaceRenderMode.COMPACT)
                    }
                }
                if (screenContextAttached) {
                    Text(uiText("Current screen included"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { com.halo.app.ui.fx.HaloMotionEvents.stop(); onStopClicked() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(uiText(if (busy) "Stop" else "Close"))
                    }
                    com.halo.app.ui.components.HaloActionButton(onClick = onOpenHaloClicked, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Open halo")) }
                }
            }
        }
    }
}
