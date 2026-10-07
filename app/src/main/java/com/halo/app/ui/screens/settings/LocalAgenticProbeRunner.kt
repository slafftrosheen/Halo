package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.*
import com.halo.engines.ai.*
import com.halo.app.data.models.ModelRuntimeProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ProbePhase {
    MODEL_RESOLVE,
    MODEL_LOAD,
    FIRST_GENERATION,
    TOOL_PROTOCOL,
    TOOL_PARSE,
    SCHEMA_VALIDATE,
    TOOL_EXECUTE,
    TOOL_CONTINUATION,
    FINAL_RESPONSE
}

enum class ProbeResult {
    PASS, FAIL, NOT_RUN
}

data class ProbePhaseResult(
    val phase: ProbePhase,
    val result: ProbeResult,
    val reason: String? = null
)

class LocalAgenticProbeRunner(
    private val providerRouter: AiProviderRouter,
    private val modelProfileRepository: ModelRuntimeProfileRepository? = null,
    private val profileSource: com.halo.app.data.models.RoomLocalInferenceProfileSource? = null,
) {
    private val _phases = MutableStateFlow<List<ProbePhaseResult>>(
        ProbePhase.entries.map { ProbePhaseResult(it, ProbeResult.NOT_RUN) }
    )
    val phases: StateFlow<List<ProbePhaseResult>> = _phases.asStateFlow()

    private fun updatePhase(phase: ProbePhase, result: ProbeResult, reason: String? = null) {
        val current = _phases.value.toMutableList()
        val index = current.indexOfFirst { it.phase == phase }
        if (index >= 0) {
            current[index] = ProbePhaseResult(phase, result, reason)
            _phases.value = current
        }
    }

    suspend fun runProbe(
        explicitTarget: AiModelRef? = null,
        generation: (suspend (AiRequest, AiContext) -> AiResponse)? = null,
        retainProvenance: Boolean = true,
    ) {
        // Reset phases
        _phases.value = ProbePhase.entries.map { ProbePhaseResult(it, ProbeResult.NOT_RUN) }
        
        try {

            val toolDef = ToolDefinition(
                id = "probe_01",
                name = "halo_probe",
                description = "Probe tool for agentic loop verification",
                risk = ToolRisk.SAFE,
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "value" to mapOf("type" to "string", "description" to "Must be HALO_OK")
                    ),
                    "required" to listOf("value")
                )
            )

            val workload = AiWorkloadSpec(
                kind = AiWorkloadKind.TOOL_ASSISTED,
                tools = AiFeatureRequirement.REQUIRED,
                allowRemote = false
            )

            val context = AiContext(
                conversation = emptyList(),
                deviceState = null,
                recentEvents = emptyList(),
                memories = emptyList(),
                availableTools = listOf(toolDef)
            )

            val request = AiRequest(
                text = "Use the halo_probe tool with the value HALO_OK, then say Done.",
                toolUseMode = ToolUseMode.REQUIRED,
                maxOutputTokens = 192, temperatureOverride = 0.0,
                reasoningMode = AiReasoningMode.DISABLED,
            )

            val target = if (explicitTarget == null) providerRouter.resolveExecutionTarget(request, workload)
            else providerRouter.resolvePinnedExecutionTarget(request, workload, explicitTarget.providerId, explicitTarget.modelId)
            require(target.provider.isLocal) { "LOCAL_TARGET_REQUIRED" }
            val profileBefore = profileSource?.entity(target.providerId, target.modelId)
            val protocol = (target.provider as? ToolProtocolReporting)?.toolCallProtocol(target.modelId) ?: ToolCallProtocol.NONE
            require(protocol.supportsToolLoop) { "TOOL_PROTOCOL_UNAVAILABLE" }
            updatePhase(ProbePhase.MODEL_RESOLVE, ProbeResult.PASS)
            val generate = generation ?: target.provider::generate
            val response = generate(request.copy(modelId = target.modelId), context)
            require(response.resolvedModelId == target.modelId && response.providerId == target.providerId) { "TARGET_MISMATCH" }
            updatePhase(ProbePhase.MODEL_LOAD, ProbeResult.PASS)
            
            updatePhase(ProbePhase.FIRST_GENERATION, ProbeResult.PASS)

            if (response.toolCalls.size != 1 || response.toolCalls.single().id.isBlank()) {
                updatePhase(ProbePhase.TOOL_PROTOCOL, ProbeResult.FAIL, "No tool calls produced")
                return
            }
            
            val call = response.toolCalls.first()
            if (call.functionName != "halo_probe") {
                updatePhase(ProbePhase.TOOL_PARSE, ProbeResult.FAIL, "Wrong tool called")
                return
            }
            
            updatePhase(ProbePhase.TOOL_PROTOCOL, ProbeResult.PASS)
            updatePhase(ProbePhase.TOOL_PARSE, ProbeResult.PASS)

            if (call.arguments["value"] != "HALO_OK") {
                updatePhase(ProbePhase.SCHEMA_VALIDATE, ProbeResult.FAIL, "Wrong arguments")
                return
            }
            updatePhase(ProbePhase.SCHEMA_VALIDATE, ProbeResult.PASS)

            updatePhase(ProbePhase.TOOL_EXECUTE, ProbeResult.PASS)

            // Continuation
            val continuationContext = context.copy(
                conversation = listOf(
                    AIMessage(Role.USER, request.text!!),
                    AIMessage(Role.ASSISTANT, response.text, toolCalls = response.toolCalls),
                    AIMessage(Role.TOOL, "{\"result\":\"ok\"}", toolCallId = call.id)
                )
            )

            val contRequest = AiRequest(
                text = request.text,
                modelId = target.modelId,
                toolUseMode = ToolUseMode.REQUIRED,
                appendTextAsUserMessage = false,
                maxOutputTokens = 192, temperatureOverride = 0.0,
                reasoningMode = AiReasoningMode.DISABLED,
                turnKind = ProviderTurnKind.TOOL_CONTINUATION
            )

            val contResponse = generate(contRequest, continuationContext)
            require(contResponse.resolvedModelId == target.modelId && contResponse.providerId == target.providerId) { "TARGET_MISMATCH" }

            updatePhase(ProbePhase.TOOL_CONTINUATION, ProbeResult.PASS)

            if (contResponse.text.isBlank() || contResponse.toolCalls.isNotEmpty()) {
                updatePhase(ProbePhase.FINAL_RESPONSE, ProbeResult.FAIL, "Empty continuation response")
                return
            }
            updatePhase(ProbePhase.FINAL_RESPONSE, ProbeResult.PASS)

            val current = profileSource?.entity(target.providerId, target.modelId)
            if (retainProvenance && profileBefore != null && current != null && current.id == profileBefore.id &&
                response.executionBackend == current.backend && contResponse.executionBackend == current.backend) {
                markProbeProvenance(modelProfileRepository, current, protocol,
                    _phases.value.all { it.result == ProbeResult.PASS })
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            // Find first NOT_RUN and mark it FAIL
            val current = _phases.value.toMutableList()
            val index = current.indexOfFirst { it.result == ProbeResult.NOT_RUN }
            if (index >= 0) {
                current[index] = ProbePhaseResult(current[index].phase, ProbeResult.FAIL, "PROBE_FAILED")
                _phases.value = current
            }
        }
    }
}
