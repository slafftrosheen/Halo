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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.interaction.InteractionSurfaceRenderer
import com.halo.app.interaction.SurfaceRenderMode
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.screens.home.PresentedAssistantResponse
import com.halo.app.ui.screens.home.ResponsePresentationSource
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.brandAccent
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionSurface
import com.halo.sdk.runtime.InteractionTurnState

data class AssistantIslandPrompt(
    val title: String,
    val options: List<String>,
    val confirm: Boolean,
    val onChoose: (Int) -> Unit,
    val onDismiss: () -> Unit,
)

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
    nativePrompt: AssistantIslandPrompt? = null,
) {
    var numberCapture by remember(nativePrompt?.title, nativePrompt?.options) { mutableStateOf(false) }
    val speaking by com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.playbackActive.collectAsState()
    val signal = remember(interactionState, interactionSurface?.inputRequestId, speaking, nativePrompt) {
        if (nativePrompt != null) HaloSignalPresentation(
            HaloSignalState.WAITING, HaloGlyph.APPROVAL, "Waiting for you",
        ) else if (speaking) HaloSignalPresentation(HaloSignalState.SPEAKING, label = "Speaking", detail = "Here's what I found.") else
        when (interactionState) {
            is InteractionTurnState.Listening -> HaloSignalPresentation(HaloSignalState.LISTENING, label = "Listening", detail = "Go ahead, I'm listening.")
            is InteractionTurnState.Transcribing -> HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.LISTEN, "Understanding", "Turning your voice into words.")
            is InteractionTurnState.Preparing, is InteractionTurnState.Generating -> HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.HALO, "Thinking", "A little clarity is on the way.")
            is InteractionTurnState.Speaking -> HaloSignalPresentation(HaloSignalState.THINKING, label = "Responding", artwork = com.halo.app.ui.presence.PresenceArtwork.STREAMING_TEXT)
            is InteractionTurnState.Failed -> HaloSignalPresentation(HaloSignalState.ERROR, HaloGlyph.OFFLINE, "Something went wrong", "Open halo to review this and try again.")
            is InteractionTurnState.Cancelled, is InteractionTurnState.Interrupted -> HaloSignalPresentation(HaloSignalState.STOPPED, HaloGlyph.HALO, "Stopped", "Ready whenever you need me.")
            is InteractionTurnState.Completed -> HaloSignalPresentation(HaloSignalState.SUCCESS, label = "Reply ready", detail = "Open halo to continue this conversation.")
            else -> HaloSignalPresentation(label = "Hello, I'm halo", detail = "What would you like to explore?")
        }.let { state ->
            if (interactionSurface?.inputRequestId != null) state.copy(state = HaloSignalState.WAITING, glyph = HaloGlyph.APPROVAL, artwork = null,
                label = "Your input is needed", detail = "Review the request below.") else state
        }
    }
    val busy = speaking || interactionState is InteractionTurnState.Listening || interactionState is InteractionTurnState.Transcribing ||
        interactionState is InteractionTurnState.Preparing || interactionState is InteractionTurnState.Generating || interactionState is InteractionTurnState.Speaking
    val easy = rememberVisualPreferences().viewMode == HaloViewMode.EASY
    val actionHeight = if (easy) 64.dp else 48.dp
    val committed = if (easy && nativePrompt == null && interactionState?.interactionId != null) {
        val home by com.halo.app.di.ManualDependencyGraph.homeViewModel.uiState.collectAsState()
        home.presentedIdentity?.takeIf {
            it.interactionId == interactionState.interactionId && it.source == ResponsePresentationSource.COMMITTED_CONVERSATION &&
                it.text.isNotBlank()
        }
    } else null
    val reduced by com.halo.app.ui.theme.rememberReducedMotion()
    val colors = MaterialTheme.colorScheme
    val needsYou = signal.state == HaloSignalState.WAITING
    val listening = signal.state == HaloSignalState.LISTENING
    val tone = when {
        needsYou -> colors.tertiary
        signal.state == HaloSignalState.ERROR -> colors.error
        else -> colors.primary
    }
    val caption = uiText(when (signal.state) {
        HaloSignalState.WAITING -> "Waiting for you"
        HaloSignalState.LISTENING -> "Listening"
        HaloSignalState.THINKING -> "Working"
        HaloSignalState.SPEAKING -> "Tap to stop"
        else -> "Talk"
    })
    BoxWithConstraints(Modifier.fillMaxWidth().imePadding()
        .haloAssistantEdge(active = listening || needsYou, attention = needsYou, accent = tone, apricot = colors.tertiary),
        contentAlignment = Alignment.BottomCenter) {
        var invoke by remember { mutableStateOf(listening && !reduced) }
        LaunchedEffect(Unit) { if (invoke) { kotlinx.coroutines.delay(900); invoke = false } }
        if (invoke && !reduced) com.halo.app.ui.fx.HaloPackageFx(com.halo.app.ui.fx.HaloPackageMoment.ASSIST_INVOKE,
            Modifier.matchParentSize())
        val panelHeight = maxHeight.coerceAtMost(420.dp)
        Surface(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().navigationBarsPadding().padding(10.dp).heightIn(max = panelHeight),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, tone.copy(alpha = .48f)),
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentApp?.let { Text(uiText("In %1\$s", it), style = MaterialTheme.typography.labelMedium) }
                        if (screenContextAttached) Text(uiText("Screen included"), style = MaterialTheme.typography.labelMedium,
                            color = colors.primary)
                    }
                    if (nativePrompt == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.halo.app.ui.zone6.Zone6IslandMark(signal.state, tone, reduced, Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(uiText(signal.label), style = MaterialTheme.typography.titleMedium)
                                signal.detail?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant) }
                            }
                        }
                        if (interactionSurface != null) {
                            val types = MaterialTheme.typography
                            val typography = if (easy) types.copy(
                                bodyLarge = types.bodyLarge.copy(fontSize = 23.sp, lineHeight = 30.sp),
                                bodyMedium = types.bodyMedium.copy(fontSize = 23.sp, lineHeight = 30.sp),
                            ) else types
                            MaterialTheme(typography = typography) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides actionHeight) {
                                    InteractionSurfaceRenderer(surface = interactionSurface,
                                        onRespond = onInteractionRespond, mode = SurfaceRenderMode.COMPACT)
                                }
                            }
                        }
                        if (committed != null && interactionSurface?.inputRequestId == null) {
                            AssistantReadAnswer(committed, busy,
                                showText = interactionSurface == null ||
                                    interactionSurface.content is com.halo.runtime.cognition.interaction.InteractionContent.Progress)
                        }
                    } else {
                        Text(nativePrompt.title, style = MaterialTheme.typography.titleMedium)
                        if (nativePrompt.confirm) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ nativePrompt.onChoose(1) }, Modifier.weight(1f).heightIn(min = actionHeight)) {
                                    Text(uiText("Not now"))
                                }
                                Button({ nativePrompt.onChoose(0) }, Modifier.weight(1f).heightIn(min = actionHeight),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary)) {
                                    Text(uiText(nativePrompt.options.first()))
                                }
                            }
                        } else {
                            Text(uiText("Tap an option or say its number."), style = MaterialTheme.typography.bodySmall)
                            if (numberCapture) {
                                val optionCount = nativePrompt.options.size
                                com.halo.app.ui.components.HaloVoiceTextActions(
                                    text = "",
                                    onText = { utterance -> spokenOption(utterance, optionCount)?.let(nativePrompt.onChoose) },
                                    showReadAction = false,
                                    appendDictation = false,
                                    maxChars = 32,
                                    dictationRequest = 1L,
                                    spokenTransform = { utterance ->
                                        spokenOption(utterance, optionCount)?.plus(1)?.toString()
                                    },
                                    previewIsValid = { spokenOption(it, optionCount) != null },
                                )
                            } else TextButton(onClick = { numberCapture = true }, modifier = Modifier.heightIn(min = actionHeight)) {
                                Text(uiText("Say the number"))
                            }
                            nativePrompt.options.forEachIndexed { index, label ->
                                OutlinedButton({ nativePrompt.onChoose(index) }, Modifier.fillMaxWidth().heightIn(min = maxOf(50.dp, actionHeight))) {
                                    Text((index + 1).toString() + ". " + label, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        if (nativePrompt != null) nativePrompt.onDismiss()
                        else { com.halo.app.ui.fx.HaloMotionEvents.stop(); onStopClicked() }
                    }, modifier = Modifier.weight(1f).heightIn(min = actionHeight)) {
                        Text(uiText(if (busy && nativePrompt == null) "Stop" else "Close"))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HaloSignalFace(signal, size = 60.dp, zone5 = true, contentDescription = caption,
                            onClick = if (signal.state == HaloSignalState.SPEAKING && nativePrompt == null) onStopClicked else null)
                        Text(caption, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onOpenHaloClicked, modifier = Modifier.weight(1f).heightIn(min = actionHeight)) {
                        Text(uiText("Open halo"))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantReadAnswer(response: PresentedAssistantResponse, busy: Boolean, showText: Boolean) {
    val graph = com.halo.app.di.ManualDependencyGraph
    val reader = remember { graph.readAloud }
    val state by reader.state.collectAsState()
    var token by remember(response.interactionId) { mutableStateOf<String?>(null) }
    val reading = state?.takeIf { token != null && it.sessionToken == token }
    DisposableEffect(response.interactionId) {
        onDispose { token?.let(reader::stopSession) }
    }
    val color = brandAccent(
        androidx.compose.foundation.isSystemInDarkTheme(), HaloAccent.LILAC).fill.copy(alpha = .35f)
    val highlighted = remember(response.text, reading, color) {
        buildAnnotatedString {
            append(response.text)
            reading?.range?.let { local ->
                var offset = 0
                for (index in 0..reading.index) {
                    offset = response.text.indexOf(reading.sections[index], offset)
                    if (offset < 0) break
                    if (index < reading.index) offset += reading.sections[index].length
                }
                if (offset >= 0 && offset + local.last < length) {
                    addStyle(SpanStyle(background = color), offset + local.first, offset + local.last + 1)
                }
            }
        }
    }
    if (showText || reading != null) Text(highlighted,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 23.sp, lineHeight = 30.sp))
    val active = reading?.playing == true || reading?.preparing == true
    TextButton(onClick = {
        if (active) token?.let(reader::stopSession)
        else if (!busy && com.halo.app.ui.components.VoiceFieldCancellation.activeFields.value.isEmpty()) {
            val provenance = response.remoteSpeechProvenance
            token = reader.start("assistant-answer:${response.interactionId}", response.text,
                graph.speechSynthesisRegistry.selector.ttsSelection.value.id,
                cloudApproved = provenance != null, language = null, remoteProvenance = provenance)
        }
    }, enabled = active || !busy, modifier = Modifier.heightIn(min = 64.dp)) {
        Text(uiText(if (active) "Stop reading" else "Read aloud"))
    }
    reading?.notice?.let { Text(uiText(it), style = MaterialTheme.typography.labelMedium) }
}


/** Accept a single number within the currently presented native choice. */
private fun spokenOption(utterance: String, optionCount: Int): Int? {
    val input = utterance.trim().trimEnd('.', '!', '?').lowercase(java.util.Locale.ROOT)
    val words = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    val number = input.toIntOrNull() ?: (words.indexOf(input.removePrefix("number ")) + 1)
    return (number - 1).takeIf { it in 0 until optionCount }
}
