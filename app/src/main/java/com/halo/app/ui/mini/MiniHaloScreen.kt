@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.halo.app.ui.mini

import androidx.compose.foundation.layout.*
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import com.halo.app.ui.presence.haloSharedOrbModifier
import androidx.compose.ui.draw.clip
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.fx.haloEdgeGlow
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
    val listeningRequested by home.listeningRequested.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val incoming by MiniHaloEntry.pendingDraft.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { incoming?.let { draft = it; MiniHaloEntry.pendingDraft.compareAndSet(it, null) } }
    // Clear only an accepted completed draft, never text the user has since edited.
    LaunchedEffect(submission.phase, submission.interactionId) {
        if (submission.phase == com.halo.app.ui.screens.home.TextSubmissionPhase.COMPLETED &&
            draft.trim() == submission.draft.trim()) draft = ""
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) home.cancelVoiceInput()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); home.cancelVoiceInput() }
    }
    val signal = com.halo.app.ui.presence.haloOrbInputPresentation(ui.sessionState.toSignalPresentation(fallback = ui.runtimeState),
        ui.inputStatus.capturing, listeningRequested || ui.speechActive,
        ui.speechProcessing || ui.runtimeState == com.halo.app.ui.glass.material.HaloRuntimeState.Recognizing).copy(detail = null)
    val active = submission.busy || listeningRequested || ui.inputStatus.capturing || ui.speechActive || ui.speechProcessing ||
        ui.runtimeState == com.halo.app.ui.glass.material.HaloRuntimeState.Recognizing ||
        ui.runtimeState == com.halo.app.ui.glass.material.HaloRuntimeState.Speaking ||
        ui.sessionState !is com.halo.sdk.provider.CognitiveSessionState.Completed &&
        ui.sessionState !is com.halo.sdk.provider.CognitiveSessionState.Failed && ui.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle
    MiniHaloContent(signal, draft, { draft = it },
        active, ui.presentedResponse, submission.message ?: ui.rejectionNotice?.reasonText,
        onTalk = { when {
            !ui.microphonePermissionGranted -> onMicrophone()
            !ui.speechReady -> onOpen("voice_settings")
            else -> home.startListening()
        } },
        onSend = { home.submitText(draft) }, onStop = home::stopOrbInteraction,
        onOpen = { screen ->
            if (screen == "home" && draft.isNotBlank()) home.captureQuestionDraft.value = draft
            onOpen(screen)
        }, onClose = onClose, sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        allowSharedOrb = allowSharedOrb,
        orbControl = { size -> com.halo.app.ui.presence.HomeOrbControl(home, onMicrophone,
            onOpen = { route ->
                if (route == "home" && draft.isNotBlank()) home.captureQuestionDraft.value = draft
                onOpen(route)
            }, size = size, forceReview = reviewRequired,
            orbModifier = haloSharedOrbModifier(sharedTransitionScope, animatedVisibilityScope, allowSharedOrb)) },
        reviewRequired = reviewRequired || ui.runtimeWaitingState in setOf(
            com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_APPROVAL,
            com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_CLARIFICATION))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MiniHaloContent(signal: HaloSignalPresentation, draft: String, onDraft: (String) -> Unit,
    busy: Boolean, response: String?, notice: String?, onTalk: () -> Unit, onSend: () -> Unit,
    onStop: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit, reviewRequired: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    allowSharedOrb: Boolean = false,
    orbControl: (@Composable (androidx.compose.ui.unit.Dp) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val active = busy || signal.state in setOf(HaloSignalState.LISTENING, HaloSignalState.CAPTURING,
        HaloSignalState.THINKING, HaloSignalState.ACTING, HaloSignalState.SPEAKING)
    BoxWithConstraints(Modifier.fillMaxSize().clip(com.halo.app.ui.design.HaloShapes.hero)
        .background(scheme.background)
        .haloEdgeGlow(active = active && signal.state == HaloSignalState.ACTING, accent = scheme.primary, corner = 26.dp)) {
        val stackPresence = maxWidth < 320.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
        Column(Modifier.fillMaxSize().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Mini Halo"), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), color = scheme.onBackground)
                if (active) OutlinedButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) {
                    HaloIcon(HaloGlyph.STOP, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(uiText("Stop"))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_close),
                        uiText("Close"), Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val presence: @Composable () -> Unit = {
                if (orbControl != null) orbControl(if (stackPresence) 64.dp else 80.dp) else {
                HaloSignalFace(if (reviewRequired) signal.copy(
                    state = HaloSignalState.WAITING, glyph = HaloGlyph.APPROVAL, label = "Needs your review",
                    artwork = com.halo.app.ui.presence.PresenceArtwork.APPROVAL_REQUIRED,
                    conditionGlyph = HaloGlyph.APPROVAL) else signal,
                    modifier = haloSharedOrbModifier(sharedTransitionScope, animatedVisibilityScope, allowSharedOrb),
                    size = if (stackPresence) 64.dp else 80.dp,
                    contentDescription = uiText(if (reviewRequired) "Needs your review" else "Talk to Halo"),
                    onClick = if (reviewRequired || busy) null else onTalk)
                com.halo.app.ui.screens.home.HomeVoiceSummonFx(signal.state == HaloSignalState.CAPTURING)
                }
            }
            val status: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(uiText(if (reviewRequired) "Needs your review" else signal.label),
                        style = MaterialTheme.typography.titleMedium, color = scheme.onBackground)
                    Text(uiText(if (reviewRequired) "Open Activity to review the next step." else "A little space for a quick question."),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
                color = scheme.surfaceContainerLow, border = BorderStroke(1.dp, scheme.outlineVariant)) {
                if (stackPresence) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    presence(); status()
                } else Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    presence(); Box(Modifier.weight(1f)) { status() }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.surfaceContainer)
            notice?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
            if (!response.isNullOrBlank()) Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
                color = scheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("halo", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        com.halo.app.ui.glass.components.HaloMarkdownText(response, textColor = scheme.onSurface)
                    }
                    com.halo.app.ui.components.HaloVoiceTextActions(text = "", onText = {},
                        allowDictation = false, readText = response, enabled = !busy)
                }
            }
            }
            if (reviewRequired) {
                Button(onClick = { onOpen("activity") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    HaloIcon(HaloGlyph.APPROVAL, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(uiText("Review"))
                }
                TextButton(onClick = onClose) { Text(uiText("Not now")) }
            } else {
                OutlinedTextField(draft, onDraft, placeholder = { Text(uiText("Ask Halo")) },
                    modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                    shape = com.halo.app.ui.design.HaloShapes.control,
                    trailingIcon = {
                        FilledIconButton(onClick = if (draft.isBlank()) onTalk else onSend,
                            enabled = !busy,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                            modifier = Modifier.padding(4.dp).size(48.dp)) {
                            Icon(androidx.compose.ui.res.painterResource(if (draft.isBlank())
                                com.halo.app.R.drawable.halo_ic_mic else com.halo.app.R.drawable.halo_ic_send),
                                uiText(if (draft.isBlank()) "Talk" else "Send"), Modifier.size(22.dp))
                        }
                    })
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onOpen("home") }, modifier = Modifier.heightIn(min = 48.dp)) {
                    HaloIcon(HaloGlyph.OPEN_APP, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(uiText("Open Halo"))
                }
            }

        }
    }
}
