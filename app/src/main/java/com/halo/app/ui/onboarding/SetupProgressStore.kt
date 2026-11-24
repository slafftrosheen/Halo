package com.halo.app.ui.onboarding

import com.halo.sdk.runtime.Storage
import com.halo.app.ui.onboarding.v13.Thinking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SetupStage(val title: String) {
    WELCOME("Welcome to Halo"), USE("How would you like to start?"), PERMISSIONS("You choose the access"),
    AI("How Halo answers"), SPEECH("Listening and speaking"), DEVICES("Your devices"),
    PRIVACY("Your data, your choice"), EVERYDAY("Your day, your choices"), READINESS("Let's check what works"), FIRST("Try Halo for real")
}
enum class SetupError { PERMISSION_DENIED, PROVIDER_CONNECTION, SPEECH_TEST, UNCONFIRMED_ACTION }
enum class SetupOutcome { INCOMPLETE, LIMITED, VERIFIED }
data class SetupProgress(val stage: SetupStage = SetupStage.WELCOME, val intention: String = "Explore first",
    val limited: Boolean = false, val outcome: SetupOutcome = SetupOutcome.INCOMPLETE,
    val interactionId: String? = null, val firstVerified: Boolean = false)

/** Presentation receipts only. Never stores credentials, transcripts, prompts or provider payloads. */
class SetupProgressStore(private val storage: Storage) {
    private val guide = MutableStateFlow(CapabilityGuideProgress.decode(storage.getString("setup_capability_guide_v1")))
    val capabilityGuideState = guide.asStateFlow()
    val capabilityGuide: CapabilityGuideProgress get() = guide.value

    @Synchronized private fun saveGuide(value: CapabilityGuideProgress) {
        storage.putString("setup_capability_guide_v1", value.encode())
        guide.value = value
    }

    @Synchronized fun startCapabilityGuide(firstId: String?) {
        require(firstId == null || firstId in CapabilityGuideProgress.ids)
        saveGuide(capabilityGuide.copy(active = firstId != null, currentId = firstId, pendingId = null))
    }

    /** Persist before launching Android; interrupted requests must never automatically re-prompt. */
    @Synchronized fun markCapabilityRequested(id: String) {
        if (!capabilityGuide.active || capabilityGuide.currentId != id) return
        saveGuide(capabilityGuide.copy(attemptedIds = capabilityGuide.attemptedIds + id,
            skippedIds = capabilityGuide.skippedIds - id, pendingId = id))
    }

    @Synchronized fun advanceCapabilityGuide(id: String, nextId: String?, attempted: Boolean) {
        require(nextId == null || nextId in CapabilityGuideProgress.ids)
        if (!capabilityGuide.active || capabilityGuide.currentId != id) return
        saveGuide(capabilityGuide.copy(active = nextId != null, currentId = nextId, pendingId = null,
            attemptedIds = if (attempted) capabilityGuide.attemptedIds + id else capabilityGuide.attemptedIds,
            skippedIds = if (!attempted) capabilityGuide.skippedIds + id else capabilityGuide.skippedIds))
    }

    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    val diagnosticsEnabled = MutableStateFlow(storage.getBoolean("setup_diagnostics_enabled_v1"))
    private val firstTry = MutableStateFlow(storage.getString("welcome_13_first_try"))
    val welcomeFirstTry = firstTry.asStateFlow()
    private val thinkingSetup = MutableStateFlow(Thinking.entries.firstOrNull {
        it.name == storage.getString("welcome_13_thinking_setup")
    })
    val welcomeThinkingSetup = thinkingSetup.asStateFlow()

    fun shouldOpenWelcome13(): Boolean = !storage.getBoolean("welcome_13_finished") && shouldAutoLaunch()

    @Synchronized fun finishWelcome13(state: com.halo.app.ui.onboarding.v13.OnboardingState) {
        require(state.finished)
        val suggestion = com.halo.app.ui.onboarding.v13.Onboarding.firstTry(state)
        val thinking = state.thinking?.takeUnless { it == Thinking.DECIDE_LATER }
        storage.putBatch(
            strings = mapOf("welcome_13_first_try" to suggestion, "welcome_13_thinking_setup" to thinking?.name,
                "welcome_13_who" to state.whoFor.joinToString(",") { it.name },
                "welcome_13_date" to java.time.LocalDate.now().toString()),
            booleans = mapOf("welcome_13_finished" to true, "setup_auto_dismissed_v1" to true),
        )
        firstTry.value = suggestion
        thinkingSetup.value = thinking
    }

    @Synchronized fun dismissWelcomeFirstTry() {
        storage.remove("welcome_13_first_try")
        firstTry.value = null
    }

    @Synchronized fun dismissWelcomeThinkingSetup() {
        storage.remove("welcome_13_thinking_setup")
        thinkingSetup.value = null
    }

    fun offerWelcomeVoice(date: java.time.LocalDate): Boolean {
        val started = storage.getString("welcome_13_date") ?: return false
        val onboarded = runCatching { java.time.LocalDate.parse(started) }.getOrNull() ?: return false
        val dismissals = storage.getString("welcome_13_voice_dismissals")?.toIntOrNull() ?: 0
        return date > onboarded && storage.getString("welcome_13_voice_hidden") != date.toString() &&
            dismissals < com.halo.app.ui.onboarding.v13.SetupCards.DISMISS_FOREVER_AFTER
    }

    @Synchronized fun dismissWelcomeVoice(date: java.time.LocalDate) {
        if (!offerWelcomeVoice(date)) return
        val count = storage.getString("welcome_13_voice_dismissals")?.toIntOrNull() ?: 0
        storage.putBatch(strings = mapOf(
            "welcome_13_voice_dismissals" to (count + 1).toString(),
            "welcome_13_voice_hidden" to date.toString(),
        ))
    }

    internal fun welcomeAudience(): Set<String> =
        storage.getString("welcome_13_who").orEmpty().split(',').filter(String::isNotBlank).toSet().ifEmpty { setOf("ME") }

    @Synchronized internal fun setupCard(date: java.time.LocalDate, eligible: List<String>): String? {
        val onboarded = runCatching { java.time.LocalDate.parse(storage.getString("welcome_13_date") ?: return null) }.getOrNull()
            ?: return null
        if (date <= onboarded || storage.getString("welcome_13_card_hidden") == date.toString()) return null
        if (storage.getString("welcome_13_card_day") == date.toString())
            return storage.getString("welcome_13_card_id")?.takeIf { it in eligible }
        return eligible.firstOrNull { id ->
            !storage.getBoolean("welcome_13_card_opened_$id") &&
                (storage.getString("welcome_13_card_dismissals_$id")?.toIntOrNull() ?: 0) < 2 &&
                (id != "hear_you" || offerWelcomeVoice(date))
        }
    }

    @Synchronized internal fun noteSetupCard(date: java.time.LocalDate, id: String) {
        if (storage.getString("welcome_13_card_day") != date.toString())
            storage.putBatch(strings = mapOf("welcome_13_card_day" to date.toString(), "welcome_13_card_id" to id))
    }

    @Synchronized internal fun respondToSetupCard(date: java.time.LocalDate, id: String, opened: Boolean) {
        if (storage.getString("welcome_13_card_day") != date.toString() ||
            storage.getString("welcome_13_card_id") != id || storage.getString("welcome_13_card_hidden") == date.toString()) return
        val key = "welcome_13_card_dismissals_$id"
        val count = storage.getString(key)?.toIntOrNull() ?: 0
        storage.putBatch(strings = mapOf("welcome_13_card_hidden" to date.toString(),
            key to (count + if (opened) 0 else 1).toString()),
            booleans = mapOf("welcome_13_card_opened_$id" to opened))
    }
    /**
     * A saved-incomplete wizard is not permission to force navigation forever.
     * Once the user explicitly chooses Explore Halo, setup becomes opt-in from Settings
     * until they ask to reset onboarding. Progress is preserved.
     */
    fun shouldAutoLaunch(): Boolean =
        state.value.outcome == SetupOutcome.INCOMPLETE && !storage.getBoolean("setup_auto_dismissed_v1")

    @Synchronized fun dismissAutomaticLaunch() {
        storage.putBoolean("setup_auto_dismissed_v1", true)
    }

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
