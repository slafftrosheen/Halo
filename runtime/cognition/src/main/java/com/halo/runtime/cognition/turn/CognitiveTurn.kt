package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.LocalModelToolPolicy
import com.halo.runtime.cognition.tools.ToolExposureSet
import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.ToolCallProtocol
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.sdk.runtime.ResponseDelivery

enum class CognitiveTurnMode {
    FRESH,
    RECOVER_PROVIDER,
    CONTINUE_AFTER_TOOL,
}

data class CognitiveTurnRequest(
    val interactionId: InteractionId,
    val requestId: String,
    val request: AiRequest,
    val policy: AiContextPolicy,
    val responseDelivery: ResponseDelivery,
    val origin: InteractionOrigin?,
    val metadata: Map<String, String>,
    val runId: AgentRunId? = null,
    val mode: CognitiveTurnMode = CognitiveTurnMode.FRESH,
    val sourceCaptureId: String? = null,
    /** Runtime-supplied explicit child context. Never populated from model arguments. */
    val voicePolicy: com.halo.sdk.runtime.VoiceResponsePolicy = com.halo.sdk.runtime.VoiceResponsePolicy(),
    val conversationId: String? = null,
    val outputTarget: com.halo.sdk.runtime.SpeechOutputTarget? = null,
    val outputFallback: com.halo.sdk.runtime.SpeechOutputFallback? = null,
    val delegatedInput: com.halo.runtime.cognition.delegation.DelegatedTurnInput? = null,
)

data class PreparedCognitiveTurn(
    val turn: CognitiveTurnRequest,
    val workload: CognitiveWorkloadPlan,
    val target: AiExecutionTarget,
    val context: AiContext,
    val providerRequest: AiRequest,
    val requiredCapabilityIds: Set<String>,
    val contextRequirementIds: Set<String>,
    /**
     * AGENTIC-001: the tool set this turn starts with, carried across tool rounds.
     * Null when no planner is wired, in which case the gate result is the exposure.
     */
    val toolExposure: ToolExposureSet? = null,
    /** AGENTIC-001: allowlist and schema budget for a constrained local model. */
    val localToolPolicy: LocalModelToolPolicy? = null,
    val toolProtocol: ToolCallProtocol = ToolCallProtocol.NATIVE,
)
