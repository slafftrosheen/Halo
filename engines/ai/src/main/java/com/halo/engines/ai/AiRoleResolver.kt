package com.halo.engines.ai

import com.halo.sdk.provider.*

data class AiExecutionPreference(
    val providerId: String?,
    val modelId: String?,
    val policy: AiRoutingPolicy
)

interface AiRoleResolver {
    fun resolvePreference(
        workload: AiWorkloadSpec,
        defaultSelection: PersistedAiSelection,
        bindings: Map<AiModelRole, AiRoleBinding>,
        providers: List<AiProvider>
    ): AiExecutionPreference

    fun determineRole(workload: AiWorkloadSpec): AiModelRole
}

class DefaultAiRoleResolver : AiRoleResolver {
    override fun resolvePreference(
        workload: AiWorkloadSpec,
        defaultSelection: PersistedAiSelection,
        bindings: Map<AiModelRole, AiRoleBinding>,
        providers: List<AiProvider>
    ): AiExecutionPreference {
        val role = determineRole(workload)
        
        if (role == AiModelRole.CHAT) {
            return AiExecutionPreference(defaultSelection.providerId, defaultSelection.modelId, defaultSelection.policy)
        }
        
        val binding = bindings[role] ?: AiRoleBinding(role, null, null, if (role in setOf(AiModelRole.AGENTIC, AiModelRole.VISION)) AiRoleSelection.AUTOMATIC else AiRoleSelection.INHERIT_DEFAULT)
        
        return when (binding.selection) {
            AiRoleSelection.INHERIT_DEFAULT -> {
                AiExecutionPreference(defaultSelection.providerId, defaultSelection.modelId, defaultSelection.policy)
            }
            AiRoleSelection.EXPLICIT -> {
                if (binding.providerId != null) {
                    AiExecutionPreference(binding.providerId, binding.modelId, AiRoutingPolicy.EXPLICIT)
                } else {
                    AiExecutionPreference(defaultSelection.providerId, defaultSelection.modelId, defaultSelection.policy)
                }
            }
            AiRoleSelection.AUTOMATIC -> {
                // Find a suitable provider for this workload
                // Just fallback to automatic policy on default or try to find one.
                AiExecutionPreference(null, null, AiRoutingPolicy.AUTOMATIC)
            }
            AiRoleSelection.DISABLED -> {
                AiExecutionPreference(null, null, AiRoutingPolicy.EXPLICIT) // Would fail explicitly
            }
        }
    }
    
    override fun determineRole(workload: AiWorkloadSpec): AiModelRole {
        if (AiCapability.IMAGE in workload.requiredCapabilities || AiCapability.VIDEO in workload.requiredCapabilities) {
            return AiModelRole.VISION
        }
        if (workload.tools == AiFeatureRequirement.REQUIRED || workload.kind == AiWorkloadKind.TOOL_ASSISTED) {
            return AiModelRole.AGENTIC
        }
        workload.preferredRole?.let { return it }
        return AiModelRole.CHAT
    }
}
