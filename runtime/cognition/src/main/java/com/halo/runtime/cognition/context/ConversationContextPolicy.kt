package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AiContextPolicy
import java.util.concurrent.ConcurrentHashMap

/** Nullable fields inherit the global policy; explicit false is never treated as absent. */
data class ConversationContextPolicy(
    val includeMemories: Boolean? = null,
    val includeSkillsAndTools: Boolean? = null,
    val includeWorkspace: Boolean? = null,
    val allowCloudWorkspace: Boolean? = null,
    val allowCloudScreenContext: Boolean? = null,
    val allowCloudImages: Boolean? = null,
    val allowCloudMemories: Boolean? = null,
) {
    fun applyTo(base: AiContextPolicy): AiContextPolicy = base.copy(
        includeMemories = includeMemories ?: base.includeMemories,
        includeAvailableTools = includeSkillsAndTools ?: base.includeAvailableTools,
        includeWorkspace = includeWorkspace ?: base.includeWorkspace,
        allowCloudWorkspace = allowCloudWorkspace ?: base.allowCloudWorkspace,
        allowCloudScreenContext = allowCloudScreenContext ?: base.allowCloudScreenContext,
        allowCloudImages = allowCloudImages ?: base.allowCloudImages,
        allowCloudMemories = allowCloudMemories ?: base.allowCloudMemories,
    )
}

interface ConversationContextPolicyRepository {
    fun get(conversationId: String): ConversationContextPolicy?
    fun set(conversationId: String, policy: ConversationContextPolicy?)
}

class InMemoryConversationContextPolicyRepository : ConversationContextPolicyRepository {
    private val policies = ConcurrentHashMap<String, ConversationContextPolicy>()
    override fun get(conversationId: String): ConversationContextPolicy? = policies[conversationId]
    override fun set(conversationId: String, policy: ConversationContextPolicy?) {
        if (policy == null) policies.remove(conversationId) else policies[conversationId] = policy
    }
}
