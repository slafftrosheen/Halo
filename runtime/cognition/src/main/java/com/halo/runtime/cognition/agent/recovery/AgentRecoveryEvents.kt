package com.halo.runtime.cognition.agent.recovery

import com.halo.sdk.runtime.HaloEvent
import java.util.UUID

/**
 * RUNTIME-018 portable recovery events.
 *
 * Recovery lifecycle is observable without exposing private tool arguments.
 * Hosts may render or log these; Runtime owns the semantics.
 */
data class AgentRecoveryAvailable(
    val runId: String,
    val interactionId: String,
    val recoveryKind: String,
    val reason: String,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent

data class AgentRecoveryStarted(
    val runId: String,
    val interactionId: String,
    val recoveryKind: String,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent

data class AgentRecoveryWaitingForInput(
    val runId: String,
    val interactionId: String,
    val inputRequestId: String,
    val inputKind: String,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent

data class AgentRecoveryBlocked(
    val runId: String,
    val interactionId: String,
    val reason: String,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent

data class AgentRecoveryCompleted(
    val runId: String,
    val interactionId: String,
    val recoveryKind: String,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent
