package com.halo.runtime.cognition.turn

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.ToolUseMode
import com.halo.sdk.runtime.ResponseDelivery

fun PreparedCognitiveTurn.streamingSpeechEligible(): Boolean =
    turn.responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL && turn.voicePolicy.streamingSpeechEnabled &&
    turn.mode == CognitiveTurnMode.FRESH && turn.runId == null && turn.delegatedInput == null &&
    providerRequest.publishStreamingTokens && AiCapability.STREAMING in target.effectiveCapabilities &&
    workload.inference.toolUseMode == ToolUseMode.NONE && context.availableTools.isEmpty()
