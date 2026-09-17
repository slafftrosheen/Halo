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
        // 1. Direct deterministic mapping for commands already matched by the Interpretation Engine
        val deterministicGoal = mapDeterministicIntent(interpretation, parentKnowledgeId)
        if (deterministicGoal != null) {
            return deterministicGoal
        }

        // 2. Fall back to LLM for conversational intents
        val prompt = "User transcript: \"${interpretation.transcript}\""
        
        val response = try {
            router.execute(
                AiRequest(text = prompt),
                AiContext.EMPTY.copy(
                    conversation = listOf(com.halo.sdk.provider.AIMessage(com.halo.sdk.provider.Role.SYSTEM, SYSTEM_PROMPT))
                )
            )
        } catch (e: Exception) {
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

    private fun mapDeterministicIntent(interpretation: Interpretation, parentKnowledgeId: String?): Goal? {
        val (type, payload) = when (val intent = interpretation.intent) {
            is PlayIntent -> {
                val title = interpretation.entities.filterIsInstance<MediaEntity>().firstOrNull()?.title ?: ""
                GoalType.MEDIA to MediaPayload(title, "play")
            }
            is PauseIntent -> GoalType.MEDIA to MediaPayload("", "pause")
            is NavigateIntent -> {
                val dest = interpretation.entities.filterIsInstance<LocationEntity>().firstOrNull()?.location ?: ""
                GoalType.NAVIGATION to NavigationPayload(dest)
            }
            is CallIntent -> {
                val name = interpretation.entities.filterIsInstance<PersonEntity>().firstOrNull()?.name ?: ""
                GoalType.COMMUNICATION to CommunicationPayload(name)
            }
            is OpenIntent -> {
                // Heuristic: check if there's any entity string
                val app = interpretation.entities.firstOrNull()?.let { it.toString() } ?: ""
                GoalType.DEVICE to DevicePayload("open", app)
            }
            else -> return null
        }

        return goalManager.createGoal(
            type = type,
            intent = interpretation.intent,
            payload = payload,
            confidence = interpretation.confidence,
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
        val normalized = value.trim()
            .removePrefix("navigation")
            .replaceFirstChar { it.uppercase() }
            
        return when (normalized) {
            "NavigateIntent" -> NavigateIntent
            "PlayIntent" -> PlayIntent
            "PauseIntent" -> PauseIntent
            "ReminderIntent" -> ReminderIntent
            "SearchIntent" -> SearchIntent
            "CallIntent" -> CallIntent
            "OpenIntent" -> OpenIntent
            "ConversationIntent" -> ConversationIntent
            else -> throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Unknown intent from LLM: $value")
        }
    }
}
