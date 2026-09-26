package com.halo.app.ui.state

import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.SpeechSessionState
import com.halo.sdk.runtime.embodiment.SessionState

/**
 * The single canonical projection from runtime state to the presence the Signal Face renders.
 *
 * Presence is derived, never chosen by the UI. A screen must not hold its own
 * presence enum or mutate one on tap: if the runtime is not listening, the face
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

fun presenceFromSnapshot(snapshot: RuntimeSnapshot): HaloRuntimeState {
    val speech = presenceFromSpeech(snapshot.speech)
    return speech
}

fun HaloRuntimeState.toSignalPresentation(
    detail: String? = null,
    progress: Float? = null,
    activityLevel: Float = 0f,
    reducedMotion: Boolean = false
): HaloSignalPresentation = when (this) {
    HaloRuntimeState.Idle -> HaloSignalPresentation(
        state = HaloSignalState.IDLE,
        glyph = HaloGlyph.HALO,
        label = "Ready",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> HaloSignalPresentation(
        state = HaloSignalState.LISTENING,
        glyph = HaloGlyph.LISTEN,
        label = "Listening",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Capturing -> HaloSignalPresentation(
        state = HaloSignalState.CAPTURING,
        glyph = HaloGlyph.CAMERA,
        label = "Capturing",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Recognizing -> HaloSignalPresentation(
        state = HaloSignalState.THINKING,
        glyph = HaloGlyph.LISTEN,
        label = "Recognizing",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Reasoning -> HaloSignalPresentation(
        state = HaloSignalState.THINKING,
        glyph = HaloGlyph.THINK,
        label = "Thinking",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Planning -> HaloSignalPresentation(
        state = HaloSignalState.THINKING,
        glyph = HaloGlyph.ACTION,
        label = "Planning",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Executing -> HaloSignalPresentation(
        state = HaloSignalState.ACTING,
        glyph = HaloGlyph.TOOL,
        label = "Working",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Speaking -> HaloSignalPresentation(
        state = HaloSignalState.SPEAKING,
        glyph = HaloGlyph.SPEAK,
        label = "Speaking",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Recovering -> HaloSignalPresentation(
        state = HaloSignalState.WARNING,
        glyph = HaloGlyph.WARNING,
        label = "Recovering",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
    HaloRuntimeState.Stopped -> HaloSignalPresentation(
        state = HaloSignalState.STOPPED,
        glyph = HaloGlyph.STOP,
        label = "Stopped",
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
}

fun RuntimeSnapshot.toSignalPresentation(
    detail: String? = null,
    progress: Float? = null,
    activityLevel: Float = 0f,
    reducedMotion: Boolean = false
): HaloSignalPresentation {
    val state = presenceFromSnapshot(this)
    return state.toSignalPresentation(
        detail = detail,
        progress = progress,
        activityLevel = activityLevel,
        reducedMotion = reducedMotion
    )
}

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
