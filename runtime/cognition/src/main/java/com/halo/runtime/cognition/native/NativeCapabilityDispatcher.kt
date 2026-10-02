package com.halo.runtime.cognition.native

import com.halo.cognition.language.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.skills.SkillExecutionService
import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.turn.ToolTurnResult
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechOutputFallback
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
    private val coordinator: AgentRunCoordinator, private val store: AgentRunStore) {
    fun owns(intent: Intent): Boolean = intent is NativeControlIntent || intent in setOf(OpenIntent, CallIntent,
        SetAlarmIntent, PlayIntent, PauseIntent, NavigateIntent, SearchIntent)

    fun requestFor(interpretation: Interpretation): NativeCapabilityRequest? {
        if (interpretation.confidence < 0.8f) return null
        val args: Map<String, Any>
        val tool: String
        val capability: String
        when (val intent = interpretation.intent) {
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
        val request = requestFor(interpretation) ?: return null
        val route = interpretation.origin?.speechRoute
        val target = if (route is com.halo.sdk.runtime.SpeechInputRoute.Device) SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind) else SpeechOutputTarget.Phone
        val runId = coordinator.start(AgentRequest(InteractionId(request.interactionId), interpretation.transcript,
            speechOutputTarget = target, speechOutputFallback = if (target is SpeechOutputTarget.Device) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE,
            sourceCaptureId = interpretation.origin?.captureId, kind = AgentRunKind.NATIVE))
        coordinator.transition(runId, AgentRunState.EXECUTING)
        val call = ToolCall(UUID.randomUUID().toString(), request.operation, request.arguments)
        return execute(request, runId, call)
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
        if (result is ToolTurnResult.Completed) {
            if (result.toolMessages.any { "status=rejected" in it.content || "status=failed" in it.content })
                coordinator.fail(runId, "Native action rejected or failed")
            else coordinator.complete(runId)
        }
        println("[HALO_NATIVE_ACTION_RESULT] interactionId=${request.interactionId} tool=${request.operation} outcome=${result::class.simpleName}")
        return NativeCapabilityResult(request, runId, result)
    }
}
