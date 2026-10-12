package com.halo.runtime.cognition.native

import com.halo.cognition.language.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.blackboard.toObservationOrigin
import com.halo.runtime.cognition.skills.SkillExecutionService
import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.turn.ToolTurnResult
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechOutputFallback
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first

/** No provider router, cognitive turn, provider turn or ToolUseMode is reachable from this boundary. */
data class NativeCapabilityRequest(val interactionId: String, val capability: CapabilityId,
    val operation: String, val arguments: Map<String, Any>, val intent: String)

enum class NativeActionOutcome {
    CONFIRMED, DISPATCHED, REJECTED, FAILED, UNKNOWN,
    WAITING_FOR_INPUT, WAITING_FOR_RESOURCE, WAITING_FOR_CAPABILITY, CANCELLED
}

/** Resolve only the current invocation, so an earlier successful call cannot mask a rejection. */
fun resolveNativeActionOutcome(run: AgentRun?, toolCallId: String, result: ToolTurnResult): NativeActionOutcome {
    val evidence = run?.steps?.lastOrNull {
        it is AgentStep.ToolRejected && it.toolCallId == toolCallId ||
            it is AgentStep.ToolOutcomeRecorded && it.toolCallId == toolCallId
    }
    if (evidence is AgentStep.ToolRejected) return NativeActionOutcome.REJECTED
    if (evidence is AgentStep.ToolOutcomeRecorded && evidence.actionTruth == ActionTruth.UNKNOWN) return NativeActionOutcome.UNKNOWN
    if (run?.state == AgentRunState.CANCELLED) return NativeActionOutcome.CANCELLED
    if (run?.state == AgentRunState.FAILED) return NativeActionOutcome.FAILED
    if (evidence is AgentStep.ToolOutcomeRecorded) {
        if (!evidence.success) return NativeActionOutcome.FAILED
        return when (evidence.actionTruth) {
            ActionTruth.CONFIRMED -> NativeActionOutcome.CONFIRMED
            ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED -> NativeActionOutcome.DISPATCHED
            ActionTruth.FAILED -> NativeActionOutcome.FAILED
            else -> NativeActionOutcome.UNKNOWN
        }
    }
    return when {
        run?.state == AgentRunState.FAILED -> NativeActionOutcome.FAILED
        result is ToolTurnResult.WaitingForInput -> NativeActionOutcome.WAITING_FOR_INPUT
        result is ToolTurnResult.WaitingForResource -> NativeActionOutcome.WAITING_FOR_RESOURCE
        result is ToolTurnResult.WaitingForCapability -> NativeActionOutcome.WAITING_FOR_CAPABILITY
        result is ToolTurnResult.RunStopped -> NativeActionOutcome.CANCELLED
        else -> NativeActionOutcome.UNKNOWN
    }
}

/**
 * A dispatch acknowledgement is not effect evidence. The sole exception for
 * advancing a script is an app launch immediately followed by a screen action:
 * the host resolver must observe the exact requested app foreground before
 * authorizing that next screen action. All other steps need confirmed truth.
 */
internal fun canAdvanceNativeScript(
    step: Intent,
    next: Intent?,
    outcome: NativeActionOutcome,
    expectedApp: String?,
): Boolean = when {
    outcome == NativeActionOutcome.CONFIRMED -> true
    outcome == NativeActionOutcome.DISPATCHED &&
        step is NativeOpenApplicationIntent &&
        expectedApp != null &&
        expectedApp.equals(step.application, ignoreCase = true) &&
        next is NativeScreenActionIntent -> true
    else -> false
}

data class NativeCapabilityResult(val request: NativeCapabilityRequest, val runId: AgentRunId,
    val result: ToolTurnResult, val outcome: NativeActionOutcome = NativeActionOutcome.UNKNOWN,
    val details: String = "", val workflowIncomplete: Boolean = false) {
    /** UI tool output is untrusted third-party screen data, never conversational or TTS copy. */
    fun responseText(): String {
        if (workflowIncomplete)
            return "This step finished, but the remaining phone workflow was not resumed. Check the app and request the remaining steps."
        if (request.operation.startsWith("halo_ui_") || request.operation == "halo_find_on_screen") {
            return when (outcome) {
                NativeActionOutcome.CONFIRMED -> when (request.operation) {
                    "halo_find_on_screen" -> "Found one matching control on the current screen."
                    "halo_ui_set_text" -> "Text entered and verified."
                    "halo_ui_click" -> "Screen control activated and verified."
                    else -> "Screen action verified."
                }
                NativeActionOutcome.DISPATCHED, NativeActionOutcome.UNKNOWN ->
                    "Screen action completion is unconfirmed; the next step was not executed."
                NativeActionOutcome.REJECTED, NativeActionOutcome.FAILED ->
                    "Screen action could not be completed. Check the current screen and try again."
                NativeActionOutcome.WAITING_FOR_INPUT -> "Review and approve the screen action in Halo."
                NativeActionOutcome.WAITING_FOR_RESOURCE -> "Screen control is busy. Try again shortly."
                NativeActionOutcome.WAITING_FOR_CAPABILITY -> "Enable the required screen-control permission or skill."
                NativeActionOutcome.CANCELLED -> "Screen action cancelled."
            }
        }
        return when (outcome) {
            NativeActionOutcome.CONFIRMED -> details.ifBlank { "Action confirmed." }
            NativeActionOutcome.DISPATCHED -> "Action dispatched; completion is unconfirmed."
            NativeActionOutcome.REJECTED -> "Action rejected. $details".trim()
            NativeActionOutcome.FAILED -> "Action failed."
            NativeActionOutcome.UNKNOWN -> "Action outcome is unconfirmed."
            NativeActionOutcome.WAITING_FOR_INPUT -> details.ifBlank { "Please review and approve this action." }
            NativeActionOutcome.WAITING_FOR_RESOURCE -> "The device resource is busy. Try again when it is available."
            NativeActionOutcome.WAITING_FOR_CAPABILITY -> "This skill needs permission or setup."
            NativeActionOutcome.CANCELLED -> "Action cancelled."
        }
    }
}

/**
 * Transient, trusted-host observation of the exact requested foreground app.
 * Never written to the journal as raw package/target data. The host produces
 * this only after a fresh accessibility capture matches its installed-app
 * resolution. It does not authorize a UI action or imply launch causality.
 */
data class NativeForegroundObservation(
    val requestedApp: String,
    val observedPackage: String,
    val capturedAtMs: Long,
)

/** Host only resolves a fresh, unique semantic target. Runtime retains execution authority. */
sealed interface NativeScreenTargetResolution {
    data class Ready(
        val toolId: String,
        val arguments: Map<String, Any>,
        val foreground: NativeForegroundObservation? = null,
    ) : NativeScreenTargetResolution
    data class NeedsUser(val reason: String) : NativeScreenTargetResolution
}

class NativeCapabilityDispatcher(private val skills: SkillExecutionService,
    private val coordinator: AgentRunCoordinator, private val store: AgentRunStore,
    private val messageDrafts: com.halo.runtime.cognition.apps.MessageDraftResolver? = null,
    private val stopSpeech: suspend () -> Unit = {},
    private val conversationId: suspend () -> String? = { null },
    private val onRunStarted: suspend (AgentRun) -> Unit = {},
    private val resolveScreenTarget: suspend (NativeScreenActionIntent, String?) -> NativeScreenTargetResolution =
        { _, _ -> NativeScreenTargetResolution.NeedsUser("Screen control is not available on this host.") }) {
    /** In-process only. Unapproved remaining steps never auto-resume after process death. */
    private data class PendingNativeSequence(
        val interpretation: Interpretation,
        val remaining: List<Intent>,
        val expectedApp: String?,
        val expiresAtMs: Long,
        val generation: Long,
    )
    private val pendingSequences = java.util.concurrent.ConcurrentHashMap<AgentRunId, PendingNativeSequence>()
    /** Stop invalidates both approvals awaiting continuation and running scripted loops. */
    private val sequenceStopGeneration = java.util.concurrent.atomic.AtomicLong(0)

    private fun holdSequence(runId: AgentRunId, interpretation: Interpretation, remaining: List<Intent>,
        expectedApp: String?, generation: Long = sequenceStopGeneration.get()) {
        if (generation != sequenceStopGeneration.get()) return
        // The final step may itself await approval. Keep an in-process proof
        // even when there are no subsequent steps; after process death, its
        // invocation cannot be reconstituted from the redacted journal.
        val now = System.currentTimeMillis()
        pendingSequences.entries.removeIf { it.value.expiresAtMs < now }
        if (pendingSequences.size >= 12)
            pendingSequences.entries.minByOrNull { it.value.expiresAtMs }?.let { pendingSequences.remove(it.key) }
        pendingSequences[runId] = PendingNativeSequence(interpretation, remaining, expectedApp, now + 300_000, generation)
    }

    /**
     * Persisted workflow owner is never an execution authority. We compute
     * completion from the actual journaled step effects, not command parsing
     * or the last assistant response.
     */
    /**
     * Commit the host's separate, fresh foreground observation to the owning
     * workflow, not the completed launch run. The launch tool outcome remains
     * DISPATCHED and can be audited independently.
     */
    private suspend fun recordForegroundLaunchEvidence(
        interpretation: Interpretation,
        expectedApp: String?,
        observation: NativeForegroundObservation?,
    ): Boolean {
        val metadata = interpretation.metadata
        val launchId = metadata["nativeWorkflowLaunchRunId"]?.let(::AgentRunId) ?: return true
        val parentId = metadata["nativeWorkflowParentRunId"]?.let(::AgentRunId) ?: return false
        val workflowId = metadata["nativeWorkflowId"] ?: return false
        val ordinal = metadata["nativeWorkflowLaunchOrdinal"]?.toIntOrNull() ?: return false
        val observed = observation ?: return false
        if (expectedApp.isNullOrBlank() ||
            !observed.requestedApp.equals(expectedApp, ignoreCase = true) ||
            observed.observedPackage.isBlank()) return false
        val launch = store.get(launchId) ?: return false
        val parent = store.get(parentId) ?: return false
        if (parent.state.isTerminal ||
            NativeWorkflowJournal.parentMarker(parent)?.workflowId != workflowId) return false
        val marker = NativeWorkflowJournal.marker(launch) ?: return false
        if (marker.workflowId != workflowId || marker.ordinal != ordinal || marker.kind != "OPEN_APP")
            return false
        val receipt = launch.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>()
            .lastOrNull { it.toolId == "halo_open_app" } ?: return false
        if (!receipt.success || receipt.actionTruth != ActionTruth.DISPATCHED) return false
        val now = System.currentTimeMillis()
        if (observed.capturedAtMs < receipt.createdAt.toEpochMilli() ||
            observed.capturedAtMs > now + 1_000 ||
            now - observed.capturedAtMs > 30_000) return false
        if (NativeWorkflowJournal.foregroundVerifiedForRun(
                parent, workflowId, ordinal, launchId)) return true
        return coordinator.recordStep(NativeWorkflowJournal.foregroundCheckpoint(
            parentId, workflowId, ordinal, launchId,
            observed.observedPackage, observed.capturedAtMs))
    }

    private suspend fun settleWorkflowParent(interpretation: Interpretation) {
        val parentId = interpretation.metadata["nativeWorkflowParentRunId"]
            ?.let(::AgentRunId) ?: return
        val parent = store.get(parentId) ?: return
        if (parent.state.isTerminal) return
        val projection = NativeWorkflowJournal.progress(store.observeRuns().first())
            .firstOrNull { it.parentRunId == parentId } ?: return
        val latest = projection.steps.lastOrNull()
        val hasLiveApproval = projection.steps.any { step ->
            step.state == AgentRunState.WAITING_FOR_APPROVAL &&
                pendingSequences[step.runId]?.let { pending ->
                    pending.generation == sequenceStopGeneration.get() &&
                        System.currentTimeMillis() <= pending.expiresAtMs
                } == true
        }
        when {
            projection.steps.size == projection.total &&
                projection.steps.all { it.outcome == NativeActionOutcome.CONFIRMED } ->
                coordinator.complete(parentId)
            latest?.outcome in setOf(NativeActionOutcome.REJECTED, NativeActionOutcome.FAILED) ->
                coordinator.fail(parentId, "Phone workflow step rejected or failed")
            latest?.outcome == NativeActionOutcome.CANCELLED ->
                coordinator.cancel(parentId, "Phone workflow cancelled")
            hasLiveApproval ->
                coordinator.transition(parentId, AgentRunState.SUSPENDED,
                    "Phone workflow awaits an explicit step approval")
            else ->
                coordinator.transition(parentId, AgentRunState.INTERRUPTED,
                    "Phone workflow incomplete or effect unverified. Request remaining steps afresh.")
        }
    }

    private suspend fun continueApprovedSequence(runId: AgentRunId, approved: NativeCapabilityResult): NativeCapabilityResult {
        val pending = pendingSequences.remove(runId)
        if (pending == null) {
            val marker = store.get(runId)?.let(NativeWorkflowJournal::marker)
            return if (marker != null) approved.copy(workflowIncomplete = true) else approved
        }
        try {
            if (System.currentTimeMillis() > pending.expiresAtMs ||
                pending.generation != sequenceStopGeneration.get())
                return approved.copy(workflowIncomplete = true)
            val parentId = pending.interpretation.metadata["nativeWorkflowParentRunId"]
                ?.let(::AgentRunId)
            if (parentId != null) {
                val parent = store.get(parentId)
                if (parent == null || parent.state.isTerminal)
                    return approved.copy(workflowIncomplete = true)
                if (parent.state == AgentRunState.SUSPENDED)
                    coordinator.transition(parentId, AgentRunState.EXECUTING)
            }
            val completed = store.get(runId) ?: return approved
            if (completed.state != AgentRunState.COMPLETED || completed.hasUnknownAction ||
                approved.outcome != NativeActionOutcome.CONFIRMED) return approved
            var last = approved
            val total = pending.interpretation.metadata["nativeWorkflowTotal"]?.toIntOrNull()
            for ((index, step) in pending.remaining.withIndex()) {
                if (pending.generation != sequenceStopGeneration.get()) return last
                val ordinal = total?.minus(pending.remaining.size)?.plus(index + 1)
                last = dispatch(pending.interpretation.copy(intent = step,
                    metadata = pending.interpretation.metadata + ("nativeSequence" to "true") +
                        (if (ordinal != null) mapOf("nativeWorkflowOrdinal" to ordinal.toString()) else emptyMap())),
                    expectedApp = pending.expectedApp)
                    ?: return last
                val run = store.get(last.runId) ?: return last
                if (run.state == AgentRunState.WAITING_FOR_APPROVAL &&
                    last.outcome == NativeActionOutcome.WAITING_FOR_INPUT) {
                    holdSequence(last.runId, pending.interpretation, pending.remaining.drop(index + 1),
                        pending.expectedApp, pending.generation)
                    return last
                }
                if (run.state != AgentRunState.COMPLETED || run.hasUnknownAction ||
                    !canAdvanceNativeScript(step, pending.remaining.getOrNull(index + 1),
                        last.outcome, pending.expectedApp) ||
                    step is NativeScreenActionIntent &&
                        !NativeScreenEffectEvidence.confirmed(run, step)) return last
            }
            return last
        } finally {
            settleWorkflowParent(pending.interpretation)
        }
    }

    private val references = com.halo.runtime.cognition.apps.InteractionReferenceResolver()
    fun foregroundChanged(packageName: String, label: String) { references.foregroundChanged(com.halo.runtime.cognition.apps.ApplicationReference(label, packageName)) }
    fun owns(intent: Intent): Boolean = intent is NativeControlIntent || intent in setOf(OpenIntent, CallIntent,
        SetAlarmIntent, PlayIntent, PauseIntent, NavigateIntent, SearchIntent)

    private fun directUserAuthorityAllowed(intent: Intent): Boolean = when (intent) {
        is NativeTargetMediaIntent,
        is NativeOpenApplicationIntent,
        is TimerIntent,
        is VolumeIntent,
        is FlashlightIntent,
        is BrightnessIntent,
        is RingerIntent,
        is OpenSettingsIntent,
        DeviceInfoIntent,
        BatteryStatusIntent,
        NetworkStatusIntent,
        OpenIntent,
        SetAlarmIntent,
        PlayIntent,
        PauseIntent,
        NavigateIntent,
        SearchIntent -> true

        // Communications, clipboard access and semantic UI/app actions keep their normal
        // descriptor policy. Direct speech/text ownership is not blanket authorization.
        is NativeMessageIntent,
        is NativeAppActionIntent,
        is ShareIntent,
        is ClipboardWriteIntent,
        ClipboardReadIntent,
        CallIntent -> false

        else -> false
    }

    fun requestFor(interpretation: Interpretation): NativeCapabilityRequest? {
        if (interpretation.confidence < 0.8f) return null
        val args: Map<String, Any>
        val tool: String
        val capability: String
        when (val intent = interpretation.intent) {
            is NativeTargetMediaIntent -> { tool = "halo_media_control"; capability = "host.media"; args = mapOf("action" to intent.action, "targetApp" to intent.application) }
            is NativeOpenApplicationIntent -> { tool = "halo_open_app"; capability = "host.application.launch"; args = mapOf("appName" to intent.application) }
            is NativeMessageIntent -> { tool = "halo_compose_" + intent.channel; capability = "host.messaging.compose"; args = mapOf("recipient" to intent.recipient, "body" to intent.body) }
            is NativeAppActionIntent -> { tool = if (intent.action == "inspect_capabilities") "halo_current_app_capabilities" else "halo_current_app_action"
                capability = "host.app.semantic"; args = if (intent.action == "inspect_capabilities") emptyMap() else mapOf("action" to intent.action) }

            is TimerIntent -> { tool = "halo_timer"; capability = "host.timer.set"; args = mapOf("seconds" to intent.seconds) }
            is VolumeIntent -> { tool = "halo_volume"; capability = "host.volume.control"; args = intent.percent?.let { mapOf("percent" to it) } ?: mapOf("direction" to (intent.direction ?: return null)) }
            is FlashlightIntent -> { tool = "halo_flashlight"; capability = "host.flashlight.control"; args = mapOf("state" to if (intent.enabled) "on" else "off") }
            is BrightnessIntent -> { tool = "halo_brightness"; capability = "host.screen.brightness"; args = mapOf("level" to intent.percent) }
            is RingerIntent -> { tool = "halo_ringer"; capability = "host.ringer.control"; args = mapOf("mode" to intent.mode) }
            is OpenSettingsIntent -> { tool = "halo_open_settings"; capability = "host.settings.open"; args = mapOf("section" to intent.section) }
            is ShareIntent -> { tool = "halo_share_text"; capability = "host.sharing.share"; args = mapOf("text" to intent.text) }
            is ClipboardWriteIntent -> { tool = "halo_clipboard_write"; capability = "host.clipboard.write"; args = mapOf("text" to intent.text) }
            ClipboardReadIntent -> { tool = "halo_clipboard_read"; capability = "host.clipboard.read"; args = emptyMap() }
            DeviceInfoIntent -> { tool = "halo_device_info"; capability = "host.device.inspect"; args = emptyMap() }
            BatteryStatusIntent -> { tool = "halo_battery_status"; capability = "host.battery.read"; args = emptyMap() }
            NetworkStatusIntent -> { tool = "halo_network_status"; capability = "host.network.read"; args = emptyMap() }
            OpenIntent -> { tool = "halo_open_app"; capability = "host.application.launch"; args = mapOf("appName" to (interpretation.entities.filterIsInstance<ApplicationEntity>().singleOrNull()?.name ?: return null)) }
            CallIntent -> { tool = "halo_dial_contact"; capability = "host.contacts.read"; args = mapOf("name" to (interpretation.entities.filterIsInstance<PersonEntity>().singleOrNull()?.name ?: return null)) }
            SetAlarmIntent -> { tool = "halo_set_alarm"; capability = "host.alarm.set"; args = mapOf("timeExpression" to interpretation.transcript.trim().trimEnd('.').replace(Regex("^(?:set (?:an? )?alarm(?: for)?|alarm for|wake me at)\\s+", RegexOption.IGNORE_CASE), "")) }
            PlayIntent, PauseIntent -> {
                // A named track must never silently become "resume whatever is playing".
                if (interpretation.intent == PlayIntent && interpretation.entities.filterIsInstance<MediaEntity>().any { it.title.lowercase() !in setOf("music", "the music", "media") }) return null
                tool = "halo_media_control"; capability = "host.media"; args = mapOf("action" to if (interpretation.intent == PlayIntent) "play" else "pause") }
            NavigateIntent -> { tool = "halo_navigation"; capability = "host.navigation.open"; args = mapOf("destination" to (interpretation.entities.filterIsInstance<PlaceEntity>().singleOrNull()?.name ?: return null)) }
            SearchIntent -> { tool = "halo_search"; capability = "host.web.search"; args = mapOf("query" to (interpretation.entities.filterIsInstance<WebEntity>().singleOrNull()?.query ?: return null)) }
            else -> return null
        }
        return NativeCapabilityRequest(interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString(),
            CapabilityId(capability), tool, args, interpretation.intent::class.simpleName ?: "NativeIntent")
    }

    suspend fun dispatch(interpretation: Interpretation, expectedApp: String? = null): NativeCapabilityResult? {
        when (val intent = interpretation.intent) {
            NativeContinueIntent -> {
                val candidates = store.nonTerminalRuns().filter {
                    ownsRun(it.id) && NativeWorkflowJournal.parentMarker(it) == null &&
                        it.state in setOf(AgentRunState.SUSPENDED,
                            AgentRunState.WAITING_FOR_CLARIFICATION, AgentRunState.WAITING_FOR_APPROVAL)
                }
                if (candidates.size == 1) {
                    val pending = candidates.single().steps.filterIsInstance<AgentStep.InputRequested>().lastOrNull()?.input
                    if (pending is AgentInputRequest.Clarification && pending.field == "uiTakeover")
                        store.resolveInputIfPending(pending.id, AgentInputResolution.Clarified("continue"))
                    return resume(candidates.single().id) ?: clarification(interpretation, "Open Halo to review the pending step before continuing.")
                }
                return clarification(interpretation, if (candidates.isEmpty()) "There is no pending native task to continue." else "Open Halo to choose which pending task to continue.")
            }
            NativeStopIntent -> {
                messageDrafts?.clear()
                references.clear()
                // A later approval callback must not resurrect remaining scripted steps.
                sequenceStopGeneration.incrementAndGet()
                pendingSequences.clear()
                val pendingRuns = store.nonTerminalRuns().filter {
                    it.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.kind != AgentRunKind.ROUTINE
                }
                val nativeRuns = pendingRuns.filter {
                    it.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.kind == AgentRunKind.NATIVE
                }
                nativeRuns.forEach { coordinator.cancel(it.id, "User requested stop") }
                if (nativeRuns.isEmpty()) pendingRuns.singleOrNull()?.let {
                    coordinator.cancel(it.id, "User requested stop")
                }
                stopSpeech()
                return clarification(interpretation, "Stopped.")
            }
            is NativeMessageIntent -> {
                val draft = messageDrafts?.prepare(intent)
                when (draft) {
                    is com.halo.runtime.cognition.apps.MessageDraftResolution.Ready -> if (draft.intent != intent) return dispatch(interpretation.copy(intent = draft.intent))
                    is com.halo.runtime.cognition.apps.MessageDraftResolution.NeedsChoice -> return clarification(interpretation, draft.prompt)
                    is com.halo.runtime.cognition.apps.MessageDraftResolution.Missing -> return clarification(interpretation, draft.prompt)
                    else -> Unit
                }
            }
            is NativeReferenceIntent -> {
                when (val draft = messageDrafts?.choose(intent.text)) {
                    is com.halo.runtime.cognition.apps.MessageDraftResolution.Ready -> return dispatch(interpretation.copy(intent = draft.intent))
                    is com.halo.runtime.cognition.apps.MessageDraftResolution.NeedsChoice -> return clarification(interpretation, draft.prompt)
                    is com.halo.runtime.cognition.apps.MessageDraftResolution.Missing -> return clarification(interpretation, draft.prompt)
                    else -> Unit
                }
                val resolution = references.resolve(intent.text)
                if (resolution is com.halo.runtime.cognition.apps.ReferenceResolution.Resolved) {
                    println("[HALO_REFERENCE_RESOLVED] intent=${resolution.intent::class.simpleName}")
                    return dispatch(interpretation.copy(intent = resolution.intent))
                }
                val prompt = when (resolution) {
                    is com.halo.runtime.cognition.apps.ReferenceResolution.Ambiguous -> resolution.prompt
                    is com.halo.runtime.cognition.apps.ReferenceResolution.Missing -> resolution.prompt
                    is com.halo.runtime.cognition.apps.ReferenceResolution.Expired -> resolution.prompt
                    else -> "Please name the target."
                }
                println("[HALO_REFERENCE_AMBIGUOUS] reason=${resolution::class.simpleName}")
                return clarification(interpretation, prompt)
            }
            is NativeCommandSequenceIntent -> {
                if (interpretation.confidence < 0.8f) return clarification(
                    interpretation, "Please repeat the full phone command before Halo acts.")
                val generation = sequenceStopGeneration.get()
                val workflowId = UUID.randomUUID().toString()
                val interaction = interpretation.metadata["interactionId"]
                    ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString()
                val parentId = coordinator.start(AgentRequest(
                    InteractionId(interaction), "Phone workflow", kind = AgentRunKind.NATIVE,
                    origin = interpretation.origin?.toObservationOrigin(),
                    sourceCaptureId = interpretation.origin?.captureId,
                    conversationId = interpretation.metadata["conversationId"] ?: conversationId()
                ))
                val recorded = coordinator.recordStep(NativeWorkflowJournal.parentCheckpoint(
                    parentId, workflowId, intent.operations.size
                ))
                if (!recorded) {
                    coordinator.fail(parentId, "Phone workflow parent checkpoint unavailable")
                    return clarification(interpretation,
                        "Could not save the phone workflow. No phone action was executed.")
                }
                coordinator.transition(parentId, AgentRunState.EXECUTING)
                val scripted = interpretation.copy(metadata = interpretation.metadata +
                    ("nativeWorkflowId" to workflowId) +
                    ("nativeWorkflowParentRunId" to parentId.value) +
                    ("nativeWorkflowTotal" to intent.operations.size.toString()) +
                    ("nativeStopGeneration" to generation.toString()))
                var last: NativeCapabilityResult? = null
                val expectedApp = (intent.operations.firstOrNull() as? NativeOpenApplicationIntent)?.application
                try {
                    for ((index, operation) in intent.operations.withIndex()) {
                        if (generation != sequenceStopGeneration.get()) return last
                        // Preserve an in-memory link to the immediately preceding
                        // dispatched launch, for the next foreground-verified step only.
                        val priorLaunch = if (index == 1 &&
                            intent.operations.firstOrNull() is NativeOpenApplicationIntent &&
                            last?.outcome == NativeActionOutcome.DISPATCHED) last?.runId else null
                        val stepMetadata = scripted.metadata + ("nativeSequence" to "true") +
                            ("nativeWorkflowOrdinal" to (index + 1).toString()) +
                            (priorLaunch?.let { mapOf("nativeWorkflowLaunchRunId" to it.value,
                                "nativeWorkflowLaunchOrdinal" to "1") } ?: emptyMap())
                        last = dispatch(scripted.copy(intent = operation, metadata = stepMetadata),
                            expectedApp = if (index > 0) expectedApp else null) ?: return last
                        val run = store.get(last.runId) ?: return last
                        if (run.state == AgentRunState.WAITING_FOR_APPROVAL &&
                            last.outcome == NativeActionOutcome.WAITING_FOR_INPUT) {
                            holdSequence(last.runId, scripted, intent.operations.drop(index + 1), expectedApp, generation)
                            return last
                        }
                        if (run.state != AgentRunState.COMPLETED || run.hasUnknownAction ||
                            !canAdvanceNativeScript(operation, intent.operations.getOrNull(index + 1),
                                last.outcome, expectedApp) ||
                            operation is NativeScreenActionIntent &&
                                !NativeScreenEffectEvidence.confirmed(run, operation)) return last
                    }
                    return last
                } finally {
                    settleWorkflowParent(scripted)
                }
            }
            else -> Unit
        }
        val request = if (interpretation.intent is NativeScreenActionIntent) {
            if (interpretation.confidence < 0.8f) return clarification(
                interpretation, "Please repeat the screen control request before Halo acts.")
            when (val resolved = resolveScreenTarget(interpretation.intent, expectedApp)) {
                is NativeScreenTargetResolution.NeedsUser -> return clarification(interpretation, resolved.reason)
                is NativeScreenTargetResolution.Ready -> {
                    // Only the host's exact requested-package observation may
                    // verify the preceding launch. No proof, no next UI action.
                    if (!recordForegroundLaunchEvidence(
                            interpretation, expectedApp, resolved.foreground)) {
                        return clarification(interpretation,
                            "Halo could not verify the requested app in the foreground. Check the app and request the remaining steps.")
                    }
                    NativeCapabilityRequest(
                        interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString(),
                        CapabilityId("host.ui.semantic"), resolved.toolId, resolved.arguments, "NativeScreenAction")
                }
            }
        } else requestFor(interpretation) ?: return null
        val route = interpretation.origin?.speechRoute
        val target = if (route is com.halo.sdk.runtime.SpeechInputRoute.Device) SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind) else SpeechOutputTarget.Phone
        val boundConversation = interpretation.metadata["conversationId"] ?: conversationId()
        // Tool arguments use their existing sensitive-data journal policy. Never
        // also persist a spoken text-field value inside the human-readable run title.
        val runTitle = when {
            interpretation.intent is NativeScreenActionIntent -> "Native screen control"
            interpretation.metadata["nativeSequence"] == "true" -> "Native phone script step"
            interpretation.intent is NativeMessageIntent || interpretation.intent is ClipboardWriteIntent ||
                interpretation.intent is ShareIntent || interpretation.intent == CallIntent ->
                "Native " + request.intent.removeSuffix("Intent")
            else -> interpretation.transcript
        }
        val runId = coordinator.start(AgentRequest(InteractionId(request.interactionId), runTitle,
            speechOutputTarget = target, speechOutputFallback = if (target is SpeechOutputTarget.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE,
            sourceCaptureId = interpretation.origin?.captureId, kind = AgentRunKind.NATIVE,
            origin = interpretation.origin?.toObservationOrigin(), conversationId = boundConversation,
            responseDelivery = if (interpretation.source == Source.MICROPHONE) com.halo.sdk.runtime.ResponseDelivery.SPEECH_AND_VISUAL else com.halo.sdk.runtime.ResponseDelivery.VISUAL_ONLY))
        val flowId = interpretation.metadata["nativeWorkflowId"]
        val ordinal = interpretation.metadata["nativeWorkflowOrdinal"]?.toIntOrNull()
        val totalSteps = interpretation.metadata["nativeWorkflowTotal"]?.toIntOrNull()
        if (flowId != null && ordinal != null && totalSteps != null) {
            val kind = when (val op = interpretation.intent) {
                is NativeScreenActionIntent -> op.verb.name
                is NativeOpenApplicationIntent, OpenIntent -> "OPEN_APP"
                else -> "NATIVE_ACTION"
            }
            // A scripted effect is never executed without a durable, redacted
            // record of its position in the user's multi-step request.
            val saved = coordinator.recordStep(
                NativeWorkflowJournal.checkpoint(runId, flowId, ordinal, totalSteps, kind)
            )
            if (!saved) {
                coordinator.fail(runId, "Phone workflow checkpoint could not be recorded")
                return clarification(interpretation,
                    "Phone control could not save the workflow step safely. No action was executed.")
            }
        }
        store.get(runId)?.let { onRunStarted(it) }

        // A stop may occur while the host waits for an app to settle or
        // reacquires an accessibility tree. Recheck immediately before any
        // authorization or tool dispatch, not only at the sequence-loop edge.
        val scriptedParentId = interpretation.metadata["nativeWorkflowParentRunId"]
            ?.let(::AgentRunId)
        if (scriptedParentId != null) {
            val expectedGeneration = interpretation.metadata["nativeStopGeneration"]?.toLongOrNull()
            val parentActive = store.get(scriptedParentId)?.state?.isTerminal == false
            if (expectedGeneration != sequenceStopGeneration.get() || !parentActive) {
                coordinator.cancel(runId, "Phone workflow stopped before action dispatch")
                return clarification(interpretation,
                    "Phone workflow stopped. No new action was executed.")
            }
        }

        // Exact, journaled authority applies only to the bounded safe-native set above.
        if (directUserAuthorityAllowed(interpretation.intent) && interpretation.source in setOf(Source.USER, Source.MICROPHONE)) {
            val digest = ToolArgumentCanonicalizer.digest(
                ToolArgumentCanonicalizer.canonicalize(request.arguments)
            )
            val authority = DirectUserCommandAuthority(
                interactionId = InteractionId(request.interactionId),
                toolId = request.operation,
                argumentsDigest = digest,
                origin = interpretation.origin ?: InteractionOrigin(interpretation.source),
                issuedAt = Instant.now()
            )
            coordinator.recordStep(
                AgentStep.DirectCommandAuthorityRecorded(
                    id = AgentStepId(UUID.randomUUID().toString()),
                    runId = runId,
                    createdAt = authority.issuedAt,
                    authority = authority
                )
            )
        }

        val startMs = System.currentTimeMillis()
        coordinator.transition(runId, AgentRunState.EXECUTING)
        val call = ToolCall(UUID.randomUUID().toString(), request.operation, request.arguments)
        val res = execute(request, runId, call)
        val totalMs = System.currentTimeMillis() - startMs
        val finalState = store.get(runId)?.state
        val timingResult = res.outcome.name
        println("HALO_NATIVE_ACTION_TIMING interactionId=${request.interactionId} intent=${request.intent} tool=${request.operation} totalMs=$totalMs result=$timingResult")
        if (finalState == AgentRunState.COMPLETED) references.remember(if (interpretation.intent == OpenIntent) NativeOpenApplicationIntent(request.arguments["appName"].toString()) else interpretation.intent)
        return res
    }

    private suspend fun clarification(interpretation: Interpretation, prompt: String): NativeCapabilityResult {
        val id = interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString()
        val runId = coordinator.start(AgentRequest(InteractionId(id), "Native reference clarification", kind = AgentRunKind.NATIVE))
        coordinator.complete(runId)
        return NativeCapabilityResult(NativeCapabilityRequest(id, CapabilityId("host.native.reference"), "reference", emptyMap(), "NativeReference"), runId,
            ToolTurnResult.Completed(com.halo.sdk.provider.AiResponse(text = "", providerId = "host"), false,
                listOf(com.halo.sdk.provider.AIMessage(com.halo.sdk.provider.Role.TOOL, prompt))),
            NativeActionOutcome.WAITING_FOR_INPUT, prompt)
    }

    suspend fun ownsRun(runId: AgentRunId): Boolean =
        store.get(runId)?.steps?.filterIsInstance<AgentStep.RunPrepared>()?.firstOrNull()?.kind == AgentRunKind.NATIVE

    suspend fun resumeOrigin(runId: AgentRunId): com.halo.runtime.cognition.blackboard.InteractionOrigin? {
        val run = store.get(runId) ?: return null
        val target = run.speechOutputTarget as? SpeechOutputTarget.Device ?: return null
        return com.halo.runtime.cognition.blackboard.InteractionOrigin(com.halo.sdk.runtime.Source.USER,
            captureId = run.sourceCaptureId, speechRoute = com.halo.sdk.runtime.SpeechInputRoute.Device(
                target.deviceId, target.platformEndpointId, target.displayName, target.kind))
    }

    suspend fun resume(runId: AgentRunId): NativeCapabilityResult? {
        val run = store.get(runId) ?: return null
        if (run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.kind != AgentRunKind.NATIVE) return null
        if (run.state.isTerminal) return null
        val workflowMarker = NativeWorkflowJournal.marker(run)
        if (workflowMarker != null) {
            val pending = pendingSequences[runId]
            val stillLive = pending != null &&
                pending.generation == sequenceStopGeneration.get() &&
                System.currentTimeMillis() <= pending.expiresAtMs
            val parentId = NativeWorkflowJournal.progress(store.observeRuns().first())
                .firstOrNull { it.workflowId == workflowMarker.workflowId }?.parentRunId
            val parentActive = parentId?.let { store.get(it)?.state?.isTerminal == false } ?: false
            if (!stillLive || !parentActive || run.state == AgentRunState.SUSPENDED) {
                pendingSequences.remove(runId)
                coordinator.fail(runId, "Scripted approval context expired or lost")
                if (parentActive && parentId != null)
                    coordinator.transition(parentId, AgentRunState.INTERRUPTED,
                        "The scripted approval context expired. No action was replayed.")
                return clarification(Interpretation("Phone workflow recovery"),
                    "The phone workflow was interrupted. Review the current app, then request the remaining steps. Halo will not replay an old screen action.")
            }
        }
        // "Continue" cannot approve an action. Only an explicit, matching durable
        // approval resolution can authorize a WAITING_FOR_APPROVAL invocation.
        if (run.state == AgentRunState.WAITING_FOR_APPROVAL) {
            val pendingApproval = run.steps.filterIsInstance<AgentStep.InputRequested>()
                .lastOrNull()?.input as? AgentInputRequest.Approval ?: return null
            val approved = run.steps.filterIsInstance<AgentStep.InputResolved>().any {
                it.requestId == pendingApproval.id && it.resolution == "Approved"
            }
            if (!approved) return null
        }
        if (run.hasUnknownAction) { coordinator.fail(runId, "Native action outcome unknown; do not replay"); return null }
        val takeover = run.steps.filterIsInstance<AgentStep.SemanticAppOperationRecorded>().lastOrNull()?.receipt
        if (takeover?.phase == com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_USER && run.state == AgentRunState.WAITING_FOR_CLARIFICATION) {
            val pending = run.steps.filterIsInstance<AgentStep.InputRequested>().lastOrNull() ?: return null
            if (run.steps.filterIsInstance<AgentStep.InputResolved>().none { it.requestId == pending.requestId && it.resolution == "Clarified:continue" }) return null
            if (!coordinator.claimResume(runId, run.state)) return null
            val call = ToolCall(UUID.randomUUID().toString(), "halo_current_app_action", mapOf("action" to takeover.semanticAction.value))
            return continueApprovedSequence(runId, execute(NativeCapabilityRequest(run.interactionId.value,
                CapabilityId("host.app.semantic"), call.functionName, call.arguments, "NativeContinue"), runId, call))
        }
        val prepared = run.steps.filterIsInstance<AgentStep.ToolInvocationPrepared>().lastOrNull() ?: return null
        if (!coordinator.claimResume(runId, run.state)) return null
        val args = ToolArgumentCanonicalizer.parseToArgumentsMap(prepared.argumentsJson).filterValues { it != null }.mapValues { it.value!! }
        val call = ToolCall(prepared.toolCallId, prepared.toolId, args)
        return continueApprovedSequence(runId, execute(NativeCapabilityRequest(run.interactionId.value,
            CapabilityId("host.native"), prepared.toolId, args, "NativeResume"), runId, call))
    }

    private suspend fun execute(request: NativeCapabilityRequest, runId: AgentRunId, call: ToolCall): NativeCapabilityResult {
        println("[HALO_NATIVE_ACTION_ROUTE] interactionId=${request.interactionId} intent=${request.intent} capability=${request.capability.value} tool=${request.operation} reason=DETERMINISTIC")
        val result = try { skills.execute(runId, InteractionId(request.interactionId), runId.value, listOf(call), true, setOf(call.functionName)) }
        catch (cancelled: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { coordinator.cancel(runId, "Native interaction cancelled") }
            throw cancelled
        }
        catch (error: Exception) { coordinator.fail(runId, "Native execution failed"); throw error }
        val executedRun = store.get(runId)
        val resolved = resolveNativeActionOutcome(executedRun, call.id, result)
        if (executedRun?.steps?.filterIsInstance<AgentStep.SemanticAppOperationRecorded>()?.any { it.receipt.operationId == call.id } != true) {
            val outcome = executedRun?.steps?.filterIsInstance<AgentStep.ToolOutcomeRecorded>()?.lastOrNull { it.toolCallId == call.id }
            val dispatched = outcome?.actionTruth in setOf(ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED, ActionTruth.CONFIRMED, ActionTruth.UNKNOWN)
            val phase = when (resolved) {
                NativeActionOutcome.WAITING_FOR_INPUT -> com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_APPROVAL
                NativeActionOutcome.CONFIRMED, NativeActionOutcome.DISPATCHED -> com.halo.runtime.cognition.apps.SemanticOperationPhase.COMPLETED
                NativeActionOutcome.REJECTED, NativeActionOutcome.FAILED, NativeActionOutcome.UNKNOWN, NativeActionOutcome.CANCELLED -> com.halo.runtime.cognition.apps.SemanticOperationPhase.FAILED
                else -> com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_USER
            }
            coordinator.recordStep(AgentStep.SemanticAppOperationRecorded(AgentStepId(UUID.randomUUID().toString()), runId, java.time.Instant.now(),
                com.halo.runtime.cognition.apps.SemanticOperationReceipt(call.id, com.halo.runtime.cognition.apps.SemanticActionId(request.operation.removePrefix("halo_")),
                    com.halo.runtime.cognition.apps.AppOperationRoute.HALO_NATIVE, null,
                    if (dispatched) com.halo.runtime.cognition.apps.DispatchTruth.DISPATCHED else com.halo.runtime.cognition.apps.DispatchTruth.NOT_DISPATCHED,
                    when (outcome?.actionTruth) { ActionTruth.CONFIRMED -> com.halo.runtime.cognition.apps.EffectTruth.CONFIRMED
                        ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED -> com.halo.runtime.cognition.apps.EffectTruth.UNCONFIRMED
                        else -> com.halo.runtime.cognition.apps.EffectTruth.UNKNOWN },
                    if (phase == com.halo.runtime.cognition.apps.SemanticOperationPhase.FAILED) com.halo.runtime.cognition.apps.SemanticOperationFailure.UNKNOWN else null,
                    java.time.Instant.now(), phase = phase)))
        }
        if (result is ToolTurnResult.Completed) {
            when (resolved) {
                NativeActionOutcome.CONFIRMED, NativeActionOutcome.DISPATCHED -> coordinator.complete(runId)
                else -> coordinator.fail(runId, "Native action ${resolved.name}")
            }
        }
        val run = store.get(runId)
        val evidence = run?.steps?.filterIsInstance<AgentStep.ToolOutcomeRecorded>()?.lastOrNull { it.toolCallId == call.id }
        val rejection = run?.steps?.filterIsInstance<AgentStep.ToolRejected>()?.lastOrNull { it.toolCallId == call.id }
        println("HALO_NATIVE_ACTION_TRUTH interactionId=${request.interactionId} run=${runId.value} tool=${request.operation} runState=${run?.state} toolResult=${if (rejection != null) "REJECTED" else evidence?.success} actionTruth=${evidence?.actionTruth ?: "NONE"} resolvedOutcome=$resolved")
        println("[HALO_NATIVE_ACTION_RESULT] interactionId=${request.interactionId} tool=${request.operation} outcome=$resolved")
        return NativeCapabilityResult(request, runId, result, resolved,
            rejection?.reason ?: evidence?.output?.substringAfter("details:\n", evidence.output).orEmpty())
    }
}
