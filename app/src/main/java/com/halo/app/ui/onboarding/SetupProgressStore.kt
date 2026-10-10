package com.halo.app.ui.onboarding

import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SetupStage(val title: String) {
    WELCOME("Welcome to Halo"), USE("How would you like to start?"), PERMISSIONS("You choose the access"),
    AI("How Halo answers"), SPEECH("Listening and speaking"), DEVICES("Your devices"),
    PRIVACY("Your data, your choice"), READINESS("Let's check what works"), FIRST("Try Halo for real")
}
enum class SetupError { PERMISSION_DENIED, PROVIDER_CONNECTION, SPEECH_TEST, UNCONFIRMED_ACTION }
enum class SetupOutcome { INCOMPLETE, LIMITED, VERIFIED }
data class SetupProgress(val stage: SetupStage = SetupStage.WELCOME, val intention: String = "Explore first",
    val limited: Boolean = false, val outcome: SetupOutcome = SetupOutcome.INCOMPLETE,
    val interactionId: String? = null, val firstVerified: Boolean = false)

/** Presentation receipts only. Never stores credentials, transcripts, prompts or provider payloads. */
class SetupProgressStore(private val storage: Storage) {
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    val diagnosticsEnabled = MutableStateFlow(storage.getBoolean("setup_diagnostics_enabled_v1"))
    fun enableDiagnostics(enabled: Boolean) { storage.putBoolean("setup_diagnostics_enabled_v1",enabled); diagnosticsEnabled.value=enabled; if(!enabled) storage.remove("setup_events_v1") }
    private fun events(): List<String> = storage.getString("setup_events_v1").orEmpty().lineSequence().filter { line ->
        val parts=line.split(':')
        parts.size == 3 && SetupStage.entries.any { it.name == parts[0] } && SetupOutcome.entries.any { it.name == parts[1] } && (parts[2] == "NONE" || SetupError.entries.any { it.name == parts[2] })
    }.take(50).toList()
    @Synchronized fun diagnostic(error: SetupError? = null) {
        if(!diagnosticsEnabled.value) return
        val entry="${state.value.stage.name}:${state.value.outcome.name}:${error?.name ?: "NONE"}"
        val entries=(events()+entry).takeLast(50)
        storage.putString("setup_events_v1",entries.joinToString("\n"))
    }
    private fun read() = SetupProgress(
        stage = SetupStage.entries.firstOrNull { it.name == storage.getString("setup_stage_v1") } ?: SetupStage.WELCOME,
        intention = storage.getString("setup_intention_v1", "Explore first") ?: "Explore first",
        limited = storage.getBoolean("setup_limited_v1"),
        outcome = SetupOutcome.entries.firstOrNull { it.name == storage.getString("setup_outcome_v1") } ?: SetupOutcome.INCOMPLETE,
        interactionId = storage.getString("setup_interaction_v1"), firstVerified = storage.getBoolean("setup_first_verified_v1"))
    @Synchronized private fun update(value: SetupProgress) {
        storage.putBatch(strings = mapOf("setup_stage_v1" to value.stage.name, "setup_intention_v1" to value.intention,
            "setup_outcome_v1" to value.outcome.name, "setup_interaction_v1" to value.interactionId),
            booleans = mapOf("setup_limited_v1" to value.limited, "setup_first_verified_v1" to value.firstVerified))
        mutable.value = value
        diagnostic()
    }
    fun open(stage: SetupStage = SetupStage.WELCOME) = update(state.value.copy(stage = stage))
    fun intention(value: String) { require(value in intentions); update(state.value.copy(intention = value)) }
    fun limited(value: Boolean) = update(state.value.copy(limited = value))
    fun next() = open(SetupStage.entries[(state.value.stage.ordinal + 1).coerceAtMost(SetupStage.entries.lastIndex)])
    fun back() = open(SetupStage.entries[(state.value.stage.ordinal - 1).coerceAtLeast(0)])
    fun bindInteraction(id: String) = update(state.value.copy(interactionId = id, firstVerified = false))
    fun verifyInteraction(id: String, committed: Boolean, pending: Boolean): Boolean {
        if (id != state.value.interactionId || !committed || pending) return false
        update(state.value.copy(firstVerified = true)); return true
    }
    fun finish(withLimitations: Boolean): Boolean {
        if (!withLimitations && !state.value.firstVerified) return false
        update(state.value.copy(limited = withLimitations, outcome = if (withLimitations) SetupOutcome.LIMITED else SetupOutcome.VERIFIED))
        return true
    }
    fun report(microphone: Boolean, recognition: Boolean, inference: Boolean, output: Boolean, device: Boolean) =
        "Halo setup v1\nstage=${state.value.stage.name}\noutcome=${state.value.outcome.name}\nlimited=${state.value.limited}\nfirstInteractionVerified=${state.value.firstVerified}\nmicrophoneCaptureVerified=$microphone\nrecognitionTestVerified=$recognition\ninferenceTestVerified=$inference\naudibleOutputConfirmed=$output\nconnectedDevice=$device\n${if(diagnosticsEnabled.value) events().joinToString("\n") else "Diagnostics disabled"}\nNo credentials, device names, speech, requests or private content included."
    companion object { val intentions = listOf("Talk to Halo", "Use headphones or glasses", "Notes and Canvas", "Study and guidance", "Explore first") }
}
