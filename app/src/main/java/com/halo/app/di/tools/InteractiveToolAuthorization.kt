package com.halo.app.di.tools

import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RUNTIME-019: the ephemeral approval UI state is GONE. No CompletableDeferred,
 * no global pending decision, no 120-second timer. The Runtime journals a
 * durable [AgentInputRequest.Approval]; [com.halo.runtime.cognition.interaction]
 * projects it into surfaces and resolves it atomically through
 * [com.halo.runtime.cognition.interaction.InteractionResponseHandler].
 *
 * This class now only (a) keeps a reference map of durable requests for
 * diagnostics/Activity and (b) conservatively rejects legacy non-durable
 * authorize calls that arrive without a durable run context.
 */
class InteractiveToolAuthorization(private val isForeground: () -> Boolean) : ToolAuthorizationCoordinator {

    /** Unresolved durable approvals by request id (source of truth: the journal). */
    private val durablePending = MutableStateFlow<Map<String, AgentInputRequest.Approval>>(emptyMap())
    val durablePendingApprovals: StateFlow<Map<String, AgentInputRequest.Approval>> = durablePending.asStateFlow()

    fun newRequestId(): String = UUID.randomUUID().toString()

    /** RUNTIME-019 hook: the Runtime journals the request; we only record it. */
    override suspend fun onDurableApprovalRequest(request: AgentInputRequest.Approval) {
        durablePending.value = durablePending.value + (request.id.value to request)
    }

    /** Called after the response handler resolved the durable request. */
    fun clearDurableRequest(requestId: String) {
        durablePending.value = durablePending.value - requestId
    }

    /**
     * Legacy direct-coordinator path (calls that arrive without a durable run
     * context). Kept foreground-gated and conservative.
     */
    override suspend fun authorizeRequest(toolCall: ToolCall, preview: ToolActionPreview): ToolAuthorization {
        if (!isForeground()) return ToolAuthorization.Rejected(toolCall, "Open Halo to approve this action")
        return ToolAuthorization.Rejected(toolCall, "Interactive confirmation required for ${toolCall.functionName}")
    }
}
