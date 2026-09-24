package com.halo.runtime.cognition

import java.util.UUID
import com.halo.icl.ontology.Goal
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget

data class ExecutionPlan(
    val id: UUID = UUID.randomUUID(),
    val goal: Goal,
    val tasks: List<Task>,
    val responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY,
    val speechOutputTarget: SpeechOutputTarget = SpeechOutputTarget.Phone,
    val speechOutputFallback: SpeechOutputFallback = SpeechOutputFallback.ALLOW_PHONE,
    val sourceCaptureId: String? = null,
    val interactionId: String? = null,
    val agentRunId: String? = null,
    val userRequest: String? = null
)
