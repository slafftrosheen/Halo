package com.halo.runtime.cognition.tools

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.skills.SkillRisk
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class ToolApprovalPolicy {
    ALWAYS_ALLOW,
    ASK_EVERY_TIME,
    ALLOW_WHILE_APP_OPEN,
    DENY
}

data class ToolActionPreview(
    val title: String,
    val description: String,
    val toolName: String,
    val risk: SkillRisk,
    val target: String? = null
)

interface ToolAuthorizationCoordinator {
    suspend fun authorizeRequest(toolCall: ToolCall, preview: ToolActionPreview): ToolAuthorization

    /** RUNTIME-019: notification hook when the Runtime journals a durable approval request. */
    suspend fun onDurableApprovalRequest(request: AgentInputRequest.Approval) {}
}

/**
 * RUNTIME-018: authorization outcome that can outlive the process. When a
 * coordinator reports [AuthorizationDecision.NeedsUserInput], the caller persists
 * a durable [AgentInputRequest.Approval], moves the run to
 * WAITING_FOR_APPROVAL and stops cleanly instead of holding a live coroutine.
 */
sealed interface AuthorizationDecision {
    data class Authorized(val toolCall: ToolCall) : AuthorizationDecision
    data class Rejected(val toolCall: ToolCall, val reason: String) : AuthorizationDecision
    data class NeedsUserInput(
        val toolCall: ToolCall,
        val request: AgentInputRequest.Approval,
    ) : AuthorizationDecision
}

/**
 * RUNTIME-017: risk classification comes from the registry's [ToolDescriptor]
 * metadata, not from tool-name string patterns. The skill policy layer may still
 * override (deny / always-allow) per user preference; the descriptor provides the
 * physical truth about what the tool can affect.
 *
 * RUNTIME-018: [authorizeDurable] returns without blocking; a required approval
 * comes back as a durable [AgentInputRequest.Approval] whose id, digest and
 * replay policy belong to the journal, not to a live coroutine.
 */
class PolicyToolAuthorizer(
    private val coordinator: ToolAuthorizationCoordinator? = null,
    private val defaultPolicyProvider: (String) -> ToolApprovalPolicy = { ToolApprovalPolicy.ASK_EVERY_TIME },
    private val authorizationGroupProvider: (String) -> String = { it },
    private val descriptorProvider: (String) -> ToolDescriptor? = { null },
    private val agentRunStore: AgentRunStore? = null,
) : ToolAuthorizer {

    private val scopedGrants = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun authorize(toolCall: ToolCall): ToolAuthorization = authorizeInternal(toolCall, null)

    override suspend fun authorize(toolCall: ToolCall, scopeId: String): ToolAuthorization =
        authorizeInternal(toolCall, scopeId)

    override fun clearScope(scopeId: String) {
        scopedGrants.remove(scopeId)
    }

    /**
     * Durable authorization evaluation. Never waits for a user; when interactive
     * approval is required it returns [AuthorizationDecision.NeedsUserInput] with
     * a stable request identity and the exact arguments digest.
     */
    suspend fun authorizeDurable(
        toolCall: ToolCall,
        runId: AgentRunId,
        interactionId: InteractionId,
        invocationId: com.halo.runtime.cognition.agent.ToolInvocationId,
        argumentsDigest: String,
        presentationContext: com.halo.runtime.cognition.ui.UiTargetPreview? = null,
        grantSummary: String? = null,
        newRequestId: () -> String,
    ): AuthorizationDecision {
        val toolName = toolCall.functionName
        val policy = defaultPolicyProvider(toolName)

        if (policy == ToolApprovalPolicy.DENY) {
            return AuthorizationDecision.Rejected(toolCall, "Tool execution denied by user policy")
        }

        val security = descriptorProvider(toolName)?.security
        val destructive = (security?.risk ?: ToolRiskClass.SENSITIVE) == ToolRiskClass.DESTRUCTIVE || presentationContext?.forceConfirmation == true
        val risk = security?.risk.toSkillRisk()
        val safeDirectCommands = setOf(
            "halo_open_app",
            "halo_timer",
            "halo_volume",
            "halo_flashlight",
            "halo_brightness",
            "halo_ringer",
            "halo_open_settings",
            "halo_media_control",
            "halo_set_alarm"
        )
        if (toolName in safeDirectCommands && agentRunStore != null &&
            security != null && security.confirmation != ConfirmationPolicy.ALWAYS &&
            security.risk != ToolRiskClass.SENSITIVE && !destructive
        ) {
            val run = agentRunStore.get(runId)
            val directAuthority = run?.steps?.filterIsInstance<AgentStep.DirectCommandAuthorityRecorded>()?.lastOrNull()?.authority
            if (directAuthority != null &&
                directAuthority.matches(interactionId, toolName, argumentsDigest)
            ) {
                return AuthorizationDecision.Authorized(toolCall)
            }
        }

        if (policy == ToolApprovalPolicy.ALWAYS_ALLOW && !destructive &&
            security?.confirmation != ConfirmationPolicy.ALWAYS
        ) {
            return AuthorizationDecision.Authorized(toolCall)
        }

        if (security?.confirmation == ConfirmationPolicy.NONE &&
            policy != ToolApprovalPolicy.ASK_EVERY_TIME
        ) {
            return AuthorizationDecision.Authorized(toolCall)
        }

        // Durable model: interactive approval becomes a journal request. The
        // scoped (in-memory) grant path remains only for the legacy authorize().
        val request = AgentInputRequest.Approval(
            id = AgentInputRequestId(newRequestId()),
            runId = runId,
            createdAt = Instant.now(),
            invocationId = invocationId,
            toolId = ToolId(toolName),
            action = presentationContext?.let { "${it.action} ${it.targetLabel.orEmpty()}".trim() } ?: buildApprovalAction(toolCall),
            detail = grantSummary?.take(2400) ?: presentationContext?.description ?: buildApprovalDetail(toolCall),
            risk = risk.name,
            argumentsDigest = argumentsDigest,
        )
        coordinator?.onDurableApprovalRequest(request)
        return AuthorizationDecision.NeedsUserInput(toolCall, request)
    }

    /**
     * RUNTIME-019: safe approval copy from [ToolPresentation] metadata. No raw
     * argument maps, no secrets — sensitive arguments are never interpolated.
     */
    private fun buildApprovalAction(toolCall: ToolCall): String {
        val presentation = descriptorProvider(toolCall.functionName)?.presentation
        return presentation?.title ?: "Confirm ${toolCall.functionName}"
    }

    private fun buildApprovalDetail(toolCall: ToolCall): String? {
        val descriptor = descriptorProvider(toolCall.functionName) ?: return null
        val presentation = descriptor.presentation ?: return null
        val parts = mutableListOf<String>()
        presentation.actionVerb?.let { parts.add(it) }
        presentation.targetArgument?.let { key ->
            toolCall.arguments[key]?.takeIf { it != null }?.let { value ->
                val isSensitive = key in presentation.sensitiveArguments
                parts.add(if (isSensitive) key else value.toString().take(160))
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString(separator = " ")
    }

    private suspend fun authorizeInternal(toolCall: ToolCall, scopeId: String?): ToolAuthorization {
        val toolName = toolCall.functionName
        val policy = defaultPolicyProvider(toolName)

        if (policy == ToolApprovalPolicy.DENY) {
            return ToolAuthorization.Rejected(toolCall, "Tool execution denied by user policy")
        }

        val security = descriptorProvider(toolName)?.security
        val destructive = (security?.risk ?: ToolRiskClass.SENSITIVE) == ToolRiskClass.DESTRUCTIVE
        val risk = security?.risk.toSkillRisk()

        if (policy == ToolApprovalPolicy.ALWAYS_ALLOW && !destructive &&
            security?.confirmation != ConfirmationPolicy.ALWAYS
        ) {
            return ToolAuthorization.Authorized(toolCall)
        }

        if (security?.confirmation == ConfirmationPolicy.NONE &&
            policy != ToolApprovalPolicy.ASK_EVERY_TIME
        ) {
            return ToolAuthorization.Authorized(toolCall)
        }

        val group = authorizationGroupProvider(toolName)
        if (!destructive && security?.confirmation != ConfirmationPolicy.ALWAYS && scopeId != null &&
            scopedGrants[scopeId]?.contains(group) == true) {
            return ToolAuthorization.Authorized(toolCall)
        }

        val preview = generatePreview(toolCall, risk)

        if (coordinator != null) {
            return coordinator.authorizeRequest(toolCall, preview).also { authorization ->
                if (authorization is ToolAuthorization.Authorized &&
                    !destructive && security?.confirmation != ConfirmationPolicy.ALWAYS && scopeId != null) {
                    scopedGrants.computeIfAbsent(scopeId) { ConcurrentHashMap.newKeySet() }.add(group)
                }
            }
        }

        return ToolAuthorization.Rejected(toolCall, "Interactive confirmation required for $toolName")
    }

    /**
     * RUNTIME-019: preview copy derives from [ToolPresentation] metadata on the
     * descriptor. The tool-name switch is gone; unknown tools get a neutral
     * generic title, never a raw dump of arguments.
     */
    private fun generatePreview(toolCall: ToolCall, risk: SkillRisk): ToolActionPreview {
        val presentation = descriptorProvider(toolCall.functionName)?.presentation
        return ToolActionPreview(
            title = presentation?.title ?: "Confirm action",
            description = buildApprovalDetail(toolCall)
                ?: presentation?.actionVerb
                ?: "Halo wants to use ${toolCall.functionName}",
            toolName = toolCall.functionName,
            risk = risk,
            target = presentation?.targetArgument?.let { key ->
                toolCall.arguments[key]?.toString()?.take(160)
                    ?.takeIf { key !in presentation.sensitiveArguments }
            },
        )
    }
}

/** Maps runtime risk classes onto the skill-layer risk vocabulary used by previews. */
fun ToolRiskClass?.toSkillRisk(): SkillRisk = when (this) {
    ToolRiskClass.READ_ONLY -> SkillRisk.READ_ONLY
    ToolRiskClass.REVERSIBLE -> SkillRisk.REVERSIBLE
    ToolRiskClass.EXTERNAL_SIDE_EFFECT -> SkillRisk.EXTERNAL_SIDE_EFFECT
    ToolRiskClass.SENSITIVE -> SkillRisk.SENSITIVE
    ToolRiskClass.DESTRUCTIVE -> SkillRisk.DESTRUCTIVE
    null -> SkillRisk.SENSITIVE
}
