package com.halo.app.ui.state

import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.SpeechSessionState
import com.halo.sdk.runtime.embodiment.SessionState

/**
 * The single canonical projection from runtime state to the presence the orb renders.
 *
 * Presence is derived, never chosen by the UI. A screen must not hold its own
 * presence enum or mutate one on tap: if the runtime is not listening, the orb
 * must not claim that it is.
 */
fun presenceFromSpeech(speech: SpeechSessionState): HaloRuntimeState = when (speech) {
    SpeechSessionState.Idle -> HaloRuntimeState.Idle
    SpeechSessionState.PassiveListening -> HaloRuntimeState.Idle
    SpeechSessionState.Triggered -> HaloRuntimeState.Wakeword
    SpeechSessionState.Capturing -> HaloRuntimeState.Capturing
    SpeechSessionState.Recognizing -> HaloRuntimeState.Recognizing
    SpeechSessionState.Thinking -> HaloRuntimeState.Reasoning
    SpeechSessionState.Processing -> HaloRuntimeState.Planning
    SpeechSessionState.Handoff -> HaloRuntimeState.Executing
    SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
    SpeechSessionState.Recovering -> HaloRuntimeState.Recovering
    is SpeechSessionState.Error -> HaloRuntimeState.Recovering
}

/**
 * Presence for a whole snapshot: the speech pipeline drives the visible stage,
 * while engine health remains available separately as diagnostic information.
 */
fun presenceFromSnapshot(snapshot: RuntimeSnapshot): HaloRuntimeState {
    val speech = presenceFromSpeech(snapshot.speech)
    // Engine health is a diagnostic, not an active recovery operation. A latched
    // provider failure must not hide a new capture or keep an idle microphone spinning.
    return speech
}

/**
 * Canonical mapping from SessionState to user-facing display text.
 * Used by HomeViewModel and DevicesViewModel to show connection status.
 */
fun SessionState.toDisplayText(): String = when (this) {
    SessionState.SAVED -> "Saved"
    SessionState.DISCOVERED -> "Discovered"
    SessionState.CONNECTING -> "Connecting"
    SessionState.CONNECTED -> "Connected"
    SessionState.HANDSHAKE -> "Handshaking"
    SessionState.CAPABILITY_SYNC -> "Syncing capabilities"
    SessionState.STATE_SYNC -> "Syncing state"
    SessionState.READY -> "Ready"
    SessionState.DISCONNECTING -> "Disconnecting"
    SessionState.DISCONNECTED -> "Disconnected"
    SessionState.ERROR -> "Error"
}
