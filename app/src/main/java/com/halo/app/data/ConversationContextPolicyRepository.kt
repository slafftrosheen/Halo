package com.halo.app.data

import com.halo.runtime.cognition.context.ConversationContextPolicy
import com.halo.runtime.cognition.context.ConversationContextPolicyRepository
import com.halo.sdk.runtime.Storage
import org.json.JSONObject

class StorageConversationContextPolicyRepository(
    private val storage: Storage,
) : ConversationContextPolicyRepository {
    override fun get(conversationId: String): ConversationContextPolicy? {
        val raw = storage.getString(key(conversationId)) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ConversationContextPolicy(
                includeMemories = json.nullableBoolean("memories"),
                includeSkillsAndTools = json.nullableBoolean("skillsTools"),
                includeWorkspace = json.nullableBoolean("workspace"),
                allowCloudWorkspace = json.nullableBoolean("cloudWorkspace"),
                allowCloudScreenContext = json.nullableBoolean("cloudScreen"),
                allowCloudImages = json.nullableBoolean("cloudImages"),
                allowCloudMemories = json.nullableBoolean("cloudMemories"),
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
