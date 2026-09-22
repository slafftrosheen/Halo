package com.halo.runtime.cognition

import com.halo.icl.ontology.Goal
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import java.util.UUID

data class GoalCreated(
    override val id: String = UUID.randomUUID().toString(),
    val goal: Goal,
    val responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY,
    val speechOutputTarget: SpeechOutputTarget = SpeechOutputTarget.Phone,
    val speechOutputFallback: SpeechOutputFallback = SpeechOutputFallback.ALLOW_PHONE,
    val sourceCaptureId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class OutcomePublished(
    override val id: String = UUID.randomUUID().toString(),
    val outcome: String,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class ExecutionResultPublished(
    override val id: String = UUID.randomUUID().toString(),
    val executionId: String,
    val planId: String,
    val task: Task,
    val outcome: Outcome,
    val message: String = "",
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class RecoveryStarted(
    override val id: String = UUID.randomUUID().toString(),
    val observationId: String,
    val recoveryAction: String,
    val attempt: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class RecoveryCompleted(
    override val id: String = UUID.randomUUID().toString(),
    val observationId: String,
    val recoveryAction: String,
    val success: Boolean,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class RecoveryFailed(
    override val id: String = UUID.randomUUID().toString(),
    val observationId: String,
    val recoveryAction: String,
    val reason: String,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
