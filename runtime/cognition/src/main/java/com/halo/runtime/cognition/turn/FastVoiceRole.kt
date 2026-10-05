package com.halo.runtime.cognition.turn

import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.ResponseDelivery

internal fun preferFastVoiceRole(turn: CognitiveTurnRequest, plan: CognitiveWorkloadPlan): CognitiveWorkloadPlan {
    val simple = turn.inputModality() == InteractionInputModality.VOICE &&
        turn.mode == CognitiveTurnMode.FRESH && turn.runId == null && turn.delegatedInput == null &&
        turn.metadata["preferredRole"] == null &&
        plan.inference.preferredRole in setOf(null, AiModelRole.CHAT) &&
        plan.inference.toolUseMode == ToolUseMode.NONE &&
        plan.inference.kind == AiWorkloadKind.CONVERSATION &&
        plan.context.needs.isEmpty() && turn.request.image == null && turn.request.audio == null && turn.request.video == null &&
        turn.metadata["hasScreenContext"] != "true" && turn.metadata["hasAssistantImage"] != "true" &&
        turn.metadata["workspaceObjectId"].isNullOrBlank()
    return if (simple) plan.copy(inference = plan.inference.copy(preferredRole = AiModelRole.FAST)) else plan
}
