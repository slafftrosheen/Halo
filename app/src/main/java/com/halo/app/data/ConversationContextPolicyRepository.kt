package com.halo.app.data

import com.halo.runtime.cognition.context.ConversationContextPolicy
import com.halo.runtime.cognition.context.ConversationContextPolicyRepository
import com.halo.sdk.runtime.Storage
import com.halo.app.ai.readRuntimeAiContextPolicy
import org.json.JSONObject

class StorageConversationContextPolicyRepository(
    private val storage: Storage,
) : ConversationContextPolicyRepository {
    override fun get(conversationId: String): ConversationContextPolicy? {
        val raw = storage.getString(key(conversationId)) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val base = storage.readRuntimeAiContextPolicy()
            fun restricted(name: String, allowed: Boolean): Boolean? = if (!allowed) false else json.nullableBoolean(name)
            ConversationContextPolicy(
                includeMemories = restricted("memories", base.includeMemories),
                includeSkillsAndTools = restricted("skillsTools", base.includeAvailableTools),
                includeWorkspace = restricted("workspace", base.includeWorkspace),
                allowCloudWorkspace = restricted("cloudWorkspace", base.allowCloudWorkspace),
                allowCloudScreenContext = restricted("cloudScreen", base.allowCloudScreenContext),
                allowCloudImages = restricted("cloudImages", base.allowCloudImages),
                allowCloudMemories = restricted("cloudMemories", base.allowCloudMemories),
            )
        }.getOrNull()
    }

    override fun set(conversationId: String, policy: ConversationContextPolicy?) {
        if (policy == null) {
            storage.putString(key(conversationId), null)
            return
        }
        val json = JSONObject()
        fun put(name: String, value: Boolean?) { if (value != null) json.put(name, value) }
        put("memories", policy.includeMemories)
        put("skillsTools", policy.includeSkillsAndTools)
        put("workspace", policy.includeWorkspace)
        put("cloudWorkspace", policy.allowCloudWorkspace)
        put("cloudScreen", policy.allowCloudScreenContext)
        put("cloudImages", policy.allowCloudImages)
        put("cloudMemories", policy.allowCloudMemories)
        storage.putString(key(conversationId), json.toString())
    }

    private fun key(id: String) = "conversation_context_policy_${id.take(128)}"
    private fun JSONObject.nullableBoolean(name: String): Boolean? = if (has(name)) getBoolean(name) else null
}
