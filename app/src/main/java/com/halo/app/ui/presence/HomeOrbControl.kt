package com.halo.app.ui.presence

import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.state.toSignalPresentation
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.sdk.provider.CognitiveSessionState

data class HaloOrbFace(val size: Dp, val content: @Composable (onClick: () -> Unit, label: String) -> Unit)

/** One host adapter for Home, Screen and Mini. No voice or tool lifecycle lives here. */
@Composable
fun HomeOrbControl(home: HomeViewModel, onMicrophone: () -> Unit, onOpen: (String) -> Unit,
    size: Dp = 64.dp, face: HaloOrbFace? = null, forceReview: Boolean = false,
    orbModifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val ui by home.uiState.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, home) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) home.cancelVoiceInput()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val submission by home.textSubmission.collectAsStateWithLifecycle()
    val starting by home.listeningRequested.collectAsStateWithLifecycle()
    val preferences = ManualDependencyGraph.appearancePreferences
    val shortcuts by preferences.orbShortcuts.collectAsStateWithLifecycle()
    val review = forceReview || ui.runtimeWaitingState in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION)
    val busy = submission.busy || ui.sessionState !is CognitiveSessionState.Completed &&
        ui.sessionState !is CognitiveSessionState.Failed && ui.sessionState != CognitiveSessionState.Idle
    val transcribing = ui.speechProcessing || ui.runtimeState == HaloRuntimeState.Recognizing
    val interaction = haloOrbInteraction(ui.inputStatus.capturing, starting || ui.speechActive, transcribing,
        ui.runtimeState == HaloRuntimeState.Speaking, busy, review)
    val signal = haloOrbInputPresentation(ui.sessionState.toSignalPresentation(fallback = ui.runtimeState),
        ui.inputStatus.capturing, starting || ui.speechActive, transcribing).copy(detail = null).let {
        if (review) it.copy(state = HaloSignalState.WAITING, glyph = HaloGlyph.APPROVAL, label = "Needs your review",
            artwork = PresenceArtwork.APPROVAL_REQUIRED, conditionGlyph = HaloGlyph.APPROVAL) else it
    }
    HaloOrbLauncher(signal, interaction, shortcuts, preferences::setOrbShortcuts,
        onPrimary = { primary -> when (primary) {
            HaloOrbPrimary.REVIEW -> onOpen("activity")
            HaloOrbPrimary.STOP -> home.stopOrbInteraction()
            HaloOrbPrimary.INTERRUPT -> home.stopSpeakingAndListen()
            HaloOrbPrimary.TALK -> {
                val latest = home.uiState.value
                when {
                    !latest.microphonePermissionGranted -> onMicrophone()
                    !latest.speechReady -> onOpen("voice_settings")
                    else -> home.startListening()
                }
            }
        } }, onStop = home::stopOrbInteraction,
        onTools = { request -> home.requestOrbCatalog(request); onOpen("home") },
        size = face?.size ?: size, face = face?.content, orbModifier = orbModifier)
}
