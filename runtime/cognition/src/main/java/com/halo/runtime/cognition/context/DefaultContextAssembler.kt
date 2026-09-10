package com.halo.runtime.cognition.context

import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.ObservationKnowledge
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.*

class DefaultContextAssembler(
    private val conversationStore: ConversationStore,
    private val blackboard: Blackboard,
    private val toolRegistry: ToolRegistry? = null
) : ContextAssembler {

    companion object {
        const val MAX_RECENT_AI_EVENTS = 20
        const val MAX_IMAGE_CONTEXT_AGE_MS = 60_000L
    }

    override suspend fun assemble(
        policy: AiContextPolicy,
        capabilities: Set<AiCapability>
    ): ContextAssemblyResult {
        val omissions = mutableListOf<ContextOmission>()

        // 1. Conversation Assembly
        val conversation = if (policy.includeConversation) {
            conversationStore.recentMessages(maxTurns = 10)
        } else {
            emptyList()
        }

        // 2. Device State Assembly
        val deviceState = if (policy.includeDeviceState) {
            val latestObs = blackboard.flow.value
                .mapNotNull { (it.payload as? ObservationKnowledge)?.observation }
                .lastOrNull()
            
            val props = mutableMapOf<String, Any>()
            if (latestObs != null) {
                props["last_observation_modality"] = latestObs.modality.name
                props["last_observation_source"] = latestObs.source.name
            }
            DeviceState(properties = props)
        } else {
            null
        }

        // 3. Recent Events Assembly
        val recentEvents = if (policy.includeRecentEvents) {
            val facts = blackboard.flow.value
            facts.mapNotNull { knowledge ->
                val payload = knowledge.payload
                when (payload) {
                    is ObservationKnowledge -> sanitizeObservationEvent(payload.observation)
                    else -> null
                }
            }.takeLast(MAX_RECENT_AI_EVENTS)
        } else {
            emptyList()
        }

        // 4. Memories
        val memories: List<Memory> = if (policy.includeMemories) {
            omissions.add(ContextOmission("MEMORIES", "Memory subsystem not yet populated"))
            emptyList()
        } else {
            emptyList()
        }

        // 5. Tools Assembly & Capability Filtering
        val tools: List<ToolDefinition> = if (policy.includeAvailableTools) {
            if (capabilities.contains(AiCapability.TOOLS)) {
                toolRegistry?.definitions() ?: emptyList()
            } else {
                omissions.add(ContextOmission("TOOLS", "Provider lacks TOOLS capability"))
                emptyList()
            }
        } else {
            emptyList()
        }

        // 6. Image Observation Assembly & Capability Filtering
        if (policy.includeImages) {
            val supportsImages = capabilities.contains(AiCapability.IMAGE) || capabilities.contains(AiCapability.MULTIMODAL)
            if (!supportsImages) {
                omissions.add(ContextOmission("IMAGE", "Provider lacks IMAGE/MULTIMODAL capability"))
            } else {
                val latestImageObs = blackboard.flow.value
                    .mapNotNull { (it.payload as? ObservationKnowledge)?.observation }
                    .findLast { it.payload is ImageObservationPayload }

                if (latestImageObs != null) {
                    val age = System.currentTimeMillis() - latestImageObs.timestamp
                    if (age > MAX_IMAGE_CONTEXT_AGE_MS) {
                        omissions.add(ContextOmission("IMAGE", "Latest image observation exceeded freshness limit (${age}ms > ${MAX_IMAGE_CONTEXT_AGE_MS}ms)"))
                    }
                }
            }
        }

        val context = AiContext(
            conversation = conversation,
            deviceState = deviceState,
            recentEvents = recentEvents,
            memories = memories,
            availableTools = tools
        )

        return ContextAssemblyResult(
            context = context,
            omissions = omissions
        )
    }

    private fun sanitizeObservationEvent(obs: Observation): HaloEvent? {
        val payload = obs.payload
        if (payload is SpeechObservationPayload) {
            return SpeechRecognized(text = sanitizeText(payload.transcript), timestamp = obs.timestamp, locale = payload.language)
        }
        if (obs.modality == Modality.SYSTEM && obs.metadata.containsKey("battery")) {
            return ObservationUpdated(observation = obs)
        }
        return null
    }

    private fun sanitizeText(text: String): String {
        return text.replace(Regex("(?i)sk-[a-zA-Z0-9_-]{20,}"), "[REDACTED_API_KEY]")
            .replace(Regex("/data/user/\\d+/"), "/data/.../")
    }
}
