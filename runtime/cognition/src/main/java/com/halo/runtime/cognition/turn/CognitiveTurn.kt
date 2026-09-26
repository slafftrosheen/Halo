package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiRequest
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
)

data class PreparedCognitiveTurn(
    val turn: CognitiveTurnRequest,
    val workload: CognitiveWorkloadPlan,
    val target: AiExecutionTarget,
    val context: AiContext,
    val providerRequest: AiRequest,
    val requiredCapabilityIds: Set<String>,
    val contextRequirementIds: Set<String>,
)
