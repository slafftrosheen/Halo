package com.halo.engines.reasoning.strategy

import com.halo.cognition.language.CallIntent
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.Intent
import com.halo.cognition.language.LocationEntity
import com.halo.cognition.language.MediaEntity
import com.halo.cognition.language.NavigateIntent
import com.halo.cognition.language.OpenIntent
import com.halo.cognition.language.PauseIntent
import com.halo.cognition.language.PersonEntity
import com.halo.cognition.language.PlayIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.ReminderIntent
import com.halo.cognition.language.SearchIntent
import com.halo.engines.reasoning.service.GoalManager
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.NavigationPayload
import com.halo.icl.ontology.MediaPayload
import com.halo.icl.ontology.MemoryPayload
import com.halo.icl.ontology.SearchPayload
import com.halo.icl.ontology.CommunicationPayload
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.ConversationPayload
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
        Analyze the user's conversational input and output a strict JSON object describing the response.
        Do NOT output any conversational text outside the JSON. ONLY output the JSON object.
        
        The JSON must match this structure:
        {
          "intent": "ConversationIntent",
          "target": "string (the conversational response or topic extracted)"
        }
    """.trimIndent()

    suspend fun generateGoal(
        interpretation: Interpretation,
        parentKnowledgeId: String? = null
    ): Goal {
        return when (interpretation.intent) {
            is NavigateIntent,
            is PlayIntent,
            is PauseIntent,
            is ReminderIntent,
            is SearchIntent,
            is CallIntent,
            is OpenIntent,
            is QuestionIntent -> {
                defaultStrategy.generateGoal(interpretation, parentKnowledgeId)
            }

            is ConversationIntent -> {
                generateConversationGoalWithLlm(interpretation, parentKnowledgeId)
            }

            else -> {
                defaultStrategy.generateGoal(interpretation, parentKnowledgeId)
            }
        }
    }

    private suspend fun generateConversationGoalWithLlm(
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
        val jsonString = extractJsonObject(jsonText)
        val intentStr = parseIntent(extractJsonString(jsonString, "intent") ?: "")
        
        val target = extractJsonString(jsonString, "target") ?: interpretation.transcript
        
        val (type, intentPayload, payload) = when (intentStr) {
            NavigateIntent -> Triple(GoalType.NAVIGATION, NavigateIntent, NavigationPayload(target))
            PlayIntent -> Triple(GoalType.MEDIA, PlayIntent, MediaPayload(target, "play"))
            PauseIntent -> Triple(GoalType.MEDIA, PauseIntent, MediaPayload("", "pause"))
            ReminderIntent -> Triple(GoalType.MEMORY, ReminderIntent, MemoryPayload(target))
            SearchIntent -> Triple(GoalType.SEARCH, SearchIntent, SearchPayload(target))
            CallIntent -> Triple(GoalType.COMMUNICATION, CallIntent, CommunicationPayload(target))
            OpenIntent -> Triple(GoalType.DEVICE, OpenIntent, DevicePayload("open", target))
            ConversationIntent -> Triple(GoalType.CONVERSATION, ConversationIntent, ConversationPayload(target))
            else -> throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Unknown intent from LLM: $intentStr")
        }
        
        return goalManager.createGoal(
            type = type,
            intent = intentPayload,
            payload = payload,
            confidence = 0.9f,
            parentId = parentKnowledgeId
        )
    }

    private fun extractJsonObject(output: String): String {
        val text = output
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')

        require(start >= 0 && end > start) {
            "Model did not return a JSON object"
        }

        return text.substring(start, end + 1)
    }

    private fun extractJsonString(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parseIntent(value: String): Intent {
        val clean = value.trim().lowercase()
        return when {
            clean.contains("navigate") -> NavigateIntent
            clean.contains("play") -> PlayIntent
            clean.contains("pause") -> PauseIntent
            clean.contains("reminder") -> ReminderIntent
            clean.contains("search") -> SearchIntent
            clean.contains("call") -> CallIntent
            clean.contains("open") -> OpenIntent
            else -> ConversationIntent
        }
    }
}
