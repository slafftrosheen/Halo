package com.halo.runtime.cognition.native

import com.halo.cognition.language.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.runtime.cognition.blackboard.Interpretation
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

/** No provider router, cognitive turn, provider turn or ToolUseMode is reachable from this boundary. */
data class NativeCapabilityRequest(val interactionId: String, val capability: CapabilityId,
    val operation: String, val arguments: Map<String, Any>, val intent: String)

data class NativeCapabilityResult(val request: NativeCapabilityRequest, val runId: AgentRunId,
    val result: ToolTurnResult) {
    fun responseText(): String = when (result) {
        is ToolTurnResult.Completed -> result.toolMessages.joinToString("\n") { message ->
            val content = message.content
            val details = content.substringAfter("details:\n", content)
            when { "action_truth=DISPATCHED" in content -> "Action dispatched. $details"
                "action_truth=UNKNOWN" in content -> "Action outcome is unconfirmed. $details"
                else -> details }
        }.ifBlank { "No action was executed" }
        is ToolTurnResult.WaitingForInput -> "Please review and approve this action."
        is ToolTurnResult.WaitingForResource -> "The device resource is busy. Try again when it is available."
        is ToolTurnResult.WaitingForCapability -> "This skill needs permission or setup."
        is ToolTurnResult.RunStopped -> result.message
    }
}

class NativeCapabilityDispatcher(private val skills: SkillExecutionService,
    private val coordinator: AgentRunCoordinator, private val store: AgentRunStore,
    private val messageDrafts: com.halo.runtime.cognition.apps.MessageDraftResolver? = null,
    private val stopSpeech: suspend () -> Unit = {}) {
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

    suspend fun dispatch(interpretation: Interpretation): NativeCapabilityResult? {
        when (val intent = interpretation.intent) {
            NativeContinueIntent -> {
                val candidates = store.nonTerminalRuns().filter { ownsRun(it.id) && it.state in setOf(AgentRunState.SUSPENDED,
                    AgentRunState.WAITING_FOR_CLARIFICATION, AgentRunState.WAITING_FOR_APPROVAL) }
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
                val pendingRuns = store.nonTerminalRuns().filter { it.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.kind != AgentRunKind.ROUTINE }
                pendingRuns.singleOrNull()?.let { coordinator.cancel(it.id, "User requested stop") }
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
                var last: NativeCapabilityResult? = null
                for (operation in intent.operations) {
                    last = dispatch(interpretation.copy(intent = operation)) ?: return last
                    // Existing durable run state is evidence; no output prose determines continuation.
                    val run = store.get(last.runId) ?: return last
                    if (run.state != AgentRunState.COMPLETED || run.hasUnknownAction) return last
                }
                return last
            }
            else -> Unit
        }
        val request = requestFor(interpretation) ?: return null
        val route = interpretation.origin?.speechRoute
        val target = if (route is com.halo.sdk.runtime.SpeechInputRoute.Device) SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind) else SpeechOutputTarget.Phone
        val runId = coordinator.start(AgentRequest(InteractionId(request.interactionId), if (interpretation.intent is NativeMessageIntent || interpretation.intent is ClipboardWriteIntent || interpretation.intent is ShareIntent || interpretation.intent == CallIntent) "Native " + request.intent.removeSuffix("Intent") else interpretation.transcript,
            speechOutputTarget = target, speechOutputFallback = if (target is SpeechOutputTarget.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE,
            sourceCaptureId = interpretation.origin?.captureId, kind = AgentRunKind.NATIVE))

        // Exact, journaled authority applies only to the bounded safe-native set above.
        if (directUserAuthorityAllowed(interpretation.intent)) {
            val digest = ToolArgumentCanonicalizer.digest(
                ToolArgumentCanonicalizer.canonicalize(request.arguments)
            )
            val authority = DirectUserCommandAuthority(
                interactionId = InteractionId(request.interactionId),
                toolId = request.operation,
                argumentsDigest = digest,
                origin = interpretation.origin ?: InteractionOrigin(Source.USER),
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
        val timingResult = when (res) {
            is ToolTurnResult.WaitingForInput -> "WAITING_FOR_INPUT"
            is ToolTurnResult.WaitingForResource -> "WAITING_FOR_RESOURCE"
            is ToolTurnResult.WaitingForCapability -> "WAITING_FOR_CAPABILITY"
            is ToolTurnResult.RunStopped -> "STOPPED"
            is ToolTurnResult.Completed -> when (finalState) {
                AgentRunState.COMPLETED -> "COMPLETED"
                AgentRunState.FAILED -> "FAILED"
                AgentRunState.INTERRUPTED -> "INTERRUPTED"
                else -> finalState?.name ?: "COMPLETED"
            }
            else -> "COMPLETED"
        }
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
                listOf(com.halo.sdk.provider.AIMessage(com.halo.sdk.provider.Role.TOOL, prompt))))
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
        if (run.hasUnknownAction) { coordinator.fail(runId, "Native action outcome unknown; do not replay"); return null }
        val takeover = run.steps.filterIsInstance<AgentStep.SemanticAppOperationRecorded>().lastOrNull()?.receipt
        if (takeover?.phase == com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_USER && run.state == AgentRunState.WAITING_FOR_CLARIFICATION) {
            val pending = run.steps.filterIsInstance<AgentStep.InputRequested>().lastOrNull() ?: return null
            if (run.steps.filterIsInstance<AgentStep.InputResolved>().none { it.requestId == pending.requestId && it.resolution == "Clarified:continue" }) return null
            if (!coordinator.claimResume(runId, run.state)) return null
            val call = ToolCall(UUID.randomUUID().toString(), "halo_current_app_action", mapOf("action" to takeover.semanticAction.value))
            return execute(NativeCapabilityRequest(run.interactionId.value, CapabilityId("host.app.semantic"), call.functionName, call.arguments, "NativeContinue"), runId, call)
        }
        val prepared = run.steps.filterIsInstance<AgentStep.ToolInvocationPrepared>().lastOrNull() ?: return null
        if (!coordinator.claimResume(runId, run.state)) return null
        val args = ToolArgumentCanonicalizer.parseToArgumentsMap(prepared.argumentsJson).filterValues { it != null }.mapValues { it.value!! }
        val call = ToolCall(prepared.toolCallId, prepared.toolId, args)
        return execute(NativeCapabilityRequest(run.interactionId.value, CapabilityId("host.native"), prepared.toolId, args, "NativeResume"), runId, call)
    }

    private suspend fun execute(request: NativeCapabilityRequest, runId: AgentRunId, call: ToolCall): NativeCapabilityResult {
        println("[HALO_NATIVE_ACTION_ROUTE] interactionId=${request.interactionId} intent=${request.intent} capability=${request.capability.value} tool=${request.operation} reason=DETERMINISTIC")
        val result = try { skills.execute(runId, InteractionId(request.interactionId), runId.value, listOf(call), true, setOf(call.functionName)) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { coordinator.cancel(runId, "Native interaction cancelled"); throw cancelled }
        catch (error: Exception) { coordinator.fail(runId, "Native execution failed"); throw error }
        val executedRun = store.get(runId)
        if (executedRun?.steps?.filterIsInstance<AgentStep.SemanticAppOperationRecorded>()?.any { it.receipt.operationId == call.id } != true) {
            val outcome = executedRun?.steps?.filterIsInstance<AgentStep.ToolOutcomeRecorded>()?.lastOrNull { it.toolCallId == call.id }
            val dispatched = outcome?.actionTruth in setOf(ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED, ActionTruth.CONFIRMED, ActionTruth.UNKNOWN)
            val phase = when (result) {
                is ToolTurnResult.WaitingForInput -> com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_APPROVAL
                is ToolTurnResult.Completed -> if (outcome?.success == true) com.halo.runtime.cognition.apps.SemanticOperationPhase.COMPLETED else com.halo.runtime.cognition.apps.SemanticOperationPhase.FAILED
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
            if (result.toolMessages.any { "status=rejected" in it.content || "status=failed" in it.content })
                coordinator.fail(runId, "Native action rejected or failed")
            else coordinator.complete(runId)
        }
        println("[HALO_NATIVE_ACTION_RESULT] interactionId=${request.interactionId} tool=${request.operation} outcome=${result::class.simpleName}")
        return NativeCapabilityResult(request, runId, result)
    }
}
