package com.halo.runtime.cognition.turn

import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer
import com.halo.runtime.cognition.agent.recovery.AgentContinuationAssembler
import com.halo.runtime.cognition.agent.recovery.AgentContinuationMessage
import com.halo.runtime.cognition.agent.recovery.AgentContinuationRole
import com.halo.runtime.cognition.context.ContextAcquisitionCoordinator
import com.halo.runtime.cognition.context.ContextAcquisitionResult
import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.cognition.context.ContextAssemblyRequest
import com.halo.runtime.cognition.context.ContextAssemblyResult
import com.halo.runtime.cognition.context.ContextKind
import com.halo.runtime.cognition.context.ContextQuery
import com.halo.runtime.cognition.workload.AiWorkloadPlanner
import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.runtime.cognition.workload.WorkloadPlanningRequest
import com.halo.runtime.cognition.tools.ToolId
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.ContextRequirement
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.Source

interface CognitiveTurnPreparer {
    suspend fun prepare(
        turn: CognitiveTurnRequest,
    ): PreparedCognitiveTurn
}

class DefaultCognitiveTurnPreparer(
    private val workloadPlanner: AiWorkloadPlanner,
    private val providerRouter: AiProviderRouter,
    private val contextAcquisitionCoordinator: ContextAcquisitionCoordinator?,
    private val contextAssembler: ContextAssembler,
    private val agentRunStore: AgentRunStore?,
    /** AGENTIC-001: initial exposure planning, scoped for local models. */
    private val toolExposurePlanner: com.halo.runtime.cognition.tools.ToolExposurePlanner? = null,
) : CognitiveTurnPreparer {

    override suspend fun prepare(turn: CognitiveTurnRequest): PreparedCognitiveTurn {
        val planningRequest = WorkloadPlanningRequest(
            request = turn.request,
            interactionId = turn.interactionId,
            origin = turn.origin?.let {
                val obsSrc = when(it.source) {
                    Source.MICROPHONE -> ObservationSource.AndroidPhoneMic
                    else -> ObservationSource.AndroidAssist
                }
                ObservationOrigin(obsSrc, it.sourceDeviceId)
            },
            metadata = turn.metadata,
            contextPolicy = turn.policy,
        )
        val workload = workloadPlanner.plan(planningRequest)

        val target = if (turn.runId != null) {
            val run = agentRunStore?.get(turn.runId)
            val metadata = run?.steps?.filterIsInstance<AgentStep.ProviderTurnStarted>()?.lastOrNull()
            val providerId = metadata?.providerId
            val modelId = metadata?.modelId
            
            val eligible = providerRouter.getAllProviders().firstOrNull { it.id == providerId } as? AiInferenceProvider
                ?: throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Pinned provider $providerId is unavailable")
            val model = eligible.listModels().firstOrNull { it.id == modelId }
                ?: throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Pinned model $modelId is unavailable")
            com.halo.sdk.provider.AiExecutionTarget(
                provider = eligible,
                model = model,
                providerCapabilities = eligible.getCapabilities(),
            )
        } else {
            providerRouter.resolveExecutionTarget(turn.request, workload.inference)
        }

        val obsOrigin = turn.origin?.let {
            ObservationOrigin(ObservationSource.AndroidAssist, it.sourceDeviceId)
        }

        val requiredCapabilities = target.effectiveCapabilities
        val contextRequirementIds = contextRequirementsFor(workload, turn.request)

        val targetContextPlan = workload.context.copy(
            needs = workload.context.needs.filterNot { need ->
                need.kind == ContextKind.VISUAL_IMAGE &&
                    need.requirement == ContextRequirement.OPTIONAL &&
                    AiCapability.IMAGE !in requiredCapabilities
            }
        )

        val acquisitionResult = contextAcquisitionCoordinator?.acquire(
            plan = targetContextPlan,
            query = ContextQuery(
                interactionId = turn.interactionId,
                text = turn.request.text.orEmpty(),
                requestedCapabilities = workload.inference.requiredCapabilities,
                activeWorkspaceObjectId = turn.metadata["workspaceObjectId"],
                origin = obsOrigin,
            ),
        ) ?: ContextAcquisitionResult(
            candidates = emptyList(),
            unsatisfiedRequired = targetContextPlan.needs.filter {
                it.requirement == ContextRequirement.REQUIRED
            },
        )

        /*
         * AGENTIC-001 tool exposure planning.
         *
         * A local provider reports how a Halo tool loop can run. NATIVE means the
         * runtime emits tool calls; HALO_PROMPT means Halo parses one bounded envelope
         * out of the model's text. Either way the model gets a bounded allowlist and a
         * bootstrap exposure set instead of every registered schema.
         */
        /*
         * The bounded local allowlist applies to providers that actually declare how a tool
         * loop runs (LiteRT). A local provider that declares nothing keeps the previous
         * gated-catalogue behaviour rather than inheriting a small-model restriction it
         * never asked for.
         */
        val toolProtocolReporter = (target.provider as? com.halo.sdk.provider.ToolProtocolReporting)
            ?.takeIf { target.provider.isLocal }
        val toolProtocol = toolProtocolReporter?.toolCallProtocol()
            ?: com.halo.sdk.provider.ToolCallProtocol.NATIVE
        val localToolPolicy = if (toolProtocolReporter != null) {
            com.halo.runtime.cognition.tools.LocalModelToolPolicy.forProtocol(toolProtocol)
        } else null
        var toolExposure = when {
            localToolPolicy != null && toolExposurePlanner != null ->
                toolExposurePlanner.localBootstrapExposure(localToolPolicy, toolProtocol)
            toolExposurePlanner != null -> toolExposurePlanner.initialExposure()
            else -> null
        }

        val assemblyResult = contextAssembler.assemble(
            policy = turn.policy,
            capabilities = requiredCapabilities,
            request = ContextAssemblyRequest(
                interactionId = turn.interactionId.value,
                acquisition = acquisitionResult,
                targetIsLocal = target.provider.isLocal,
                contextWindowTokens = target.model.contextLength,
                requiredNeeds = targetContextPlan.needs.filter { it.requirement == ContextRequirement.REQUIRED },
                exposureSet = toolExposure,
                localToolPolicy = localToolPolicy,
            )
        )

        val boundRequest = bindAssembledContext(turn.request.copy(modelId = target.modelId), assemblyResult, turn.responseDelivery)

        var finalContext = assemblyResult.context
        
        if (turn.mode == CognitiveTurnMode.CONTINUE_AFTER_TOOL || turn.mode == CognitiveTurnMode.RECOVER_PROVIDER) {
            val run = turn.runId?.let { agentRunStore?.get(it) }
            if (run != null) {
                val continuation = AgentContinuationAssembler.fromJournal(run, excludeLastCompletedTurn = (turn.mode == CognitiveTurnMode.RECOVER_PROVIDER))
                if (toolExposure != null && toolExposurePlanner != null) {
                    toolExposure = toolExposure.copy(
                        pinned = toolExposure.pinned + continuation.exposedToolIds.map(::ToolId)
                    )
                    finalContext = finalContext.copy(
                        availableTools = toolExposurePlanner.effectiveDefinitions(
                            exposure = toolExposure,
                            localPolicy = localToolPolicy,
                        )
                    )
                }
                // CONTINUE_AFTER_TOOL sends no user-authored outgoing message, so the
                // durable original request must be present in history. Provider retry
                // still sends its original request and therefore excludes that first
                // journal message to avoid duplication.
                val reconstructedMessages = if (turn.mode == CognitiveTurnMode.CONTINUE_AFTER_TOOL) {
                    continuation.messages
                } else {
                    continuation.messages.drop(1)
                }
                val additionalMessages = reconstructedMessages.map {
                    continuationMessageToAiMessage(it, boundRequest.appendTextAsUserMessage, turn.responseDelivery)
                }
                finalContext = finalContext.copy(
                    conversation = finalContext.conversation + additionalMessages
                )
            }
        }

        return PreparedCognitiveTurn(
            turn = turn,
            workload = workload,
            target = target,
            context = finalContext,
            providerRequest = boundRequest,
            requiredCapabilityIds = requiredCapabilities.map { it.name }.toSet(),
            contextRequirementIds = contextRequirementIds,
            toolExposure = toolExposure,
            localToolPolicy = localToolPolicy,
            toolProtocol = toolProtocol,
        )
    }

    private fun bindAssembledContext(
        request: AiRequest,
        assembly: ContextAssemblyResult,
        responseDelivery: ResponseDelivery
    ): AiRequest {
        var result = request
        if (responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL && !request.text.isNullOrBlank()) {
            val styleInstruction = "[Instruction: Respond conversationally and concisely in 1-2 short sentences. Do not say 'an AI language model'.]\n\n"
            result = result.copy(text = styleInstruction + request.text)
        }
        val image = assembly.image ?: return result
        if (result.image != null) return result
        return result.copy(image = image.imageBytes, mimeTypes = listOf(image.mimeType))
    }

    private fun contextRequirementsFor(plan: CognitiveWorkloadPlan, request: AiRequest): Set<String> {
        val result = mutableSetOf<String>()
        plan.context.needs.forEach { need ->
            result.add("${need.kind.name}:${need.visualFreshness?.name ?: need.affinity.name}")
        }
        if (request.image != null) result.add("VISUAL_IMAGE:REQUEST_BYTES")
        return result
    }

    private fun continuationMessageToAiMessage(
        msg: AgentContinuationMessage,
        inlineUserInContext: Boolean,
        responseDelivery: ResponseDelivery
    ): AIMessage {
        return when (msg.role) {
            AgentContinuationRole.USER -> {
                var text = msg.text.orEmpty()
                if (responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL && text.isNotBlank()) {
                    text = "[Instruction: Respond conversationally and concisely in 1-2 short sentences. Do not say 'an AI language model'.]\n\n$text"
                }
                AIMessage(Role.USER, text)
            }
            AgentContinuationRole.ASSISTANT -> {
                val tools = if (msg.toolCallId != null && msg.toolName != null) {
                    listOf(
                        ToolCall(
                            id = msg.toolCallId,
                            functionName = msg.toolName,
                            arguments = ToolArgumentCanonicalizer.parseToArgumentsMap(msg.argumentsJson.orEmpty()).mapValues { it.value ?: "" }
                        )
                    )
                } else emptyList()
                AIMessage(Role.ASSISTANT, msg.text.orEmpty(), toolCalls = tools)
            }
            AgentContinuationRole.TOOL -> {
                AIMessage(
                    role = Role.TOOL,
                    content = msg.text.orEmpty(),
                    toolCallId = msg.toolCallId
                )
            }
        }
    }
}
