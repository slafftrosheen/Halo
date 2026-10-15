@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.halo.app.ui.mini

import androidx.compose.foundation.layout.*
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import com.halo.app.ui.presence.haloSharedOrbModifier
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.state.toSignalPresentation

/** Same conversation, runtime and durable approvals as the full Home screen. */
@Composable
fun MiniHaloScreen(home: HomeViewModel, onMicrophone: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit,
    reviewRequired: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    allowSharedOrb: Boolean = false,
) {
    val ui by home.uiState.collectAsStateWithLifecycle()
    val submission by home.textSubmission.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val incoming by MiniHaloEntry.pendingDraft.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { incoming?.let { draft = it; MiniHaloEntry.pendingDraft.compareAndSet(it, null) } }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) home.cancelVoiceInput()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); home.cancelVoiceInput() }
    }
    MiniHaloContent(ui.sessionState.toSignalPresentation(fallback = ui.runtimeState), draft, { draft = it },
        submission.busy, ui.presentedResponse, submission.message ?: ui.rejectionNotice?.reasonText,
        onTalk = { if (ui.microphonePermissionGranted) home.toggleListening() else onMicrophone() },
        onSend = { home.submitText(draft) }, onStop = home::cancelSession,
        onTool = home::submitText, onOpen = { screen ->
            if (screen == "home" && draft.isNotBlank()) home.captureQuestionDraft.value = draft
            onOpen(screen)
        }, onClose = onClose, sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        allowSharedOrb = allowSharedOrb,
        reviewRequired = reviewRequired || ui.runtimeWaitingState in setOf(
            com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_APPROVAL,
            com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_CLARIFICATION))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MiniHaloContent(signal: HaloSignalPresentation, draft: String, onDraft: (String) -> Unit,
    busy: Boolean, response: String?, notice: String?, onTalk: () -> Unit, onSend: () -> Unit,
    onStop: () -> Unit, onTool: (String) -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit, reviewRequired: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    allowSharedOrb: Boolean = false,
) {
    var tools by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val stackPresence = maxWidth < 320.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
        Column(Modifier.fillMaxSize()
            .imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val largeText = stackPresence
            if (largeText) {
                Text(uiText("Mini Halo"), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground)
                OutlinedButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop")) }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Mini Halo"), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                OutlinedButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop")) }
            }
            val presence: @Composable () -> Unit = {
                HaloSignalFace(if (reviewRequired) signal.copy(
                    state = com.halo.app.ui.presence.HaloSignalState.WAITING,
                    glyph = com.halo.app.ui.icons.HaloGlyph.APPROVAL,
                    label = "Needs your review",
                    artwork = com.halo.app.ui.presence.PresenceArtwork.APPROVAL_REQUIRED,
                    conditionGlyph = com.halo.app.ui.icons.HaloGlyph.APPROVAL
                ) else signal,
                    modifier = haloSharedOrbModifier(sharedTransitionScope, animatedVisibilityScope, allowSharedOrb),
                    size = 88.dp,
                    contentDescription = uiText(if (reviewRequired) "Needs your review" else "Talk to Halo"),
                    onClick = if (reviewRequired) null else onTalk)
            }
            val status: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(uiText(if (reviewRequired) "Needs your review" else signal.label),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(uiText("Ask Halo"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stackPresence) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                presence()
                status()
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presence()
                Box(Modifier.weight(1f)) { status() }
            }
            if (reviewRequired) {
                // Open the canonical review owner; this button never grants an approval itself.
                Button(onClick = { onOpen("activity") }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Review")) }
                TextButton(onClick = onClose) { Text(uiText("Not now")) }
            } else {
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft, onDraft, placeholder = { Text(uiText("Ask Halo")) },
                            modifier = Modifier.weight(1f), minLines = 1, maxLines = 4,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        FilledIconButton(onClick = if (draft.isBlank()) onTalk else onSend,
                            enabled = draft.isBlank() || !busy,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.size(48.dp)) {
                            Icon(androidx.compose.ui.res.painterResource(if (draft.isBlank())
                                com.halo.app.R.drawable.halo_ic_mic else com.halo.app.R.drawable.halo_ic_send),
                                uiText(if (draft.isBlank()) "Talk" else "Send"), Modifier.size(24.dp))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { tools = !tools }) { Text(uiText("My tools")) }
                TextButton(onClick = { onOpen("home") }) { Text(uiText("Open Halo")) }
                TextButton(onClick = onClose) { Text(uiText("Close")) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!response.isNullOrBlank()) Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(response, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
            if (tools) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTool("open camera") }, enabled = !busy && !reviewRequired) { Text(uiText("Open camera")) }
                OutlinedButton(onClick = { onOpen("notebook") }) { Text(uiText("Notebook")) }
                OutlinedButton(onClick = { onOpen("study") }) { Text(uiText("Continue learning")) }
            }
        }
    }
}
