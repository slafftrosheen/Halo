package com.halo.engines.reasoning.strategy

import com.halo.cognition.language.*
import com.halo.engines.reasoning.service.GoalManager
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRequest

class LlmReasoningStrategy(
    private val router: AiProviderRouter,
    private val goalManager: GoalManager,
    private val defaultStrategy: DefaultReasoningStrategy
) {

    private val SYSTEM_PROMPT = """
        You are the reasoning engine for Halo. 
        Analyze the user's intent and output a strict JSON object describing the action.
        Do NOT output any conversational text. ONLY output the JSON object.
        
        The JSON must match this structure:
        {
          "intent": "NavigateIntent | PlayIntent | PauseIntent | ReminderIntent | SearchIntent | CallIntent | OpenIntent | ConversationIntent",
          "target": "string (optional, e.g., destination, media title, contact name, app name)",
          "action": "string (optional, e.g., 'play', 'pause')"
        }
    """.trimIndent()

    suspend fun generateGoal(
        interpretation: Interpretation,
        parentKnowledgeId: String? = null
    ): Goal {
        val prompt = "User transcript: \"${interpretation.transcript}\""
        
        val response = try {
            router.execute(
                AiRequest(text = prompt),
                AiContext.EMPTY.copy(
                    conversation = listOf(com.halo.sdk.provider.AIMessage(com.halo.sdk.provider.Role.SYSTEM, SYSTEM_PROMPT))
                )
            )
        } catch (e: Exception) {
            // If the LLM router fails (e.g., due to local unavailability or rate limits), 
            // fallback to the deterministic default strategy.
            return defaultStrategy.generateGoal(interpretation, parentKnowledgeId)
        }
        
        val jsonText = response.text?.trim() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty response from LLM")
        
        val startIndex = jsonText.indexOf('{')
        val endIndex = jsonText.lastIndexOf('}')
        if (startIndex == -1 || endIndex == -1) {
            throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Invalid JSON model output")
        }
        
        val jsonString = jsonText.substring(startIndex, endIndex + 1)
        val intentStr = extractJsonString(jsonString, "intent") ?: ""
        val target = extractJsonString(jsonString, "target") ?: interpretation.transcript
        val action = extractJsonString(jsonString, "action") ?: ""
        
        val (type, intent, payload) = when (intentStr) {
            "NavigateIntent" -> Triple(GoalType.NAVIGATION, NavigateIntent, NavigationPayload(target))
            "PlayIntent" -> Triple(GoalType.MEDIA, PlayIntent, MediaPayload(target, "play"))
            "PauseIntent" -> Triple(GoalType.MEDIA, PauseIntent, MediaPayload("", "pause"))
            "ReminderIntent" -> Triple(GoalType.MEMORY, ReminderIntent, MemoryPayload(target))
            "SearchIntent" -> Triple(GoalType.SEARCH, SearchIntent, SearchPayload(target))
            "CallIntent" -> Triple(GoalType.COMMUNICATION, CallIntent, CommunicationPayload(target))
            "OpenIntent" -> Triple(GoalType.DEVICE, OpenIntent, DevicePayload("open", target))
            "ConversationIntent" -> Triple(GoalType.CONVERSATION, ConversationIntent, ConversationPayload(target))
            else -> throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Unknown intent from LLM: $intentStr")
        }
        
        return goalManager.createGoal(
            type = type,
            intent = intent,
            payload = payload,
            confidence = 0.9f,
            parentId = parentKnowledgeId
        )
    }

    private fun extractJsonString(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }
}
