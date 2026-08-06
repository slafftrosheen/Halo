package com.halo.engines.agents

import com.halo.engines.interpretation.internal.DefaultInterpretationEngine
import com.halo.engines.reasoning.service.GoalManager
import com.halo.engines.planning.PlanningEngine
import com.halo.engines.execution.ExecutionEngine
import com.halo.engines.memory.ReflectionEngine
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.Outcome
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationId
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.Modality
import com.halo.icl.ontology.*
import com.halo.cognition.language.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The RuntimeOrchestrator coordinates the Alpha Loop deterministically:
 * Speech Event -> Interpretation -> Goal Manager -> Planning -> Execution -> Outcome -> Timeline
 * 
 * No redundant pub/sub between these steps. The orchestrator calls them in sequence.
 */
class RuntimeOrchestrator(
    private val interpretationEngine: DefaultInterpretationEngine,
    private val goalManager: GoalManager,
    private val planningEngine: PlanningEngine,
    private val executionEngine: ExecutionEngine,
    private val reflectionEngine: ReflectionEngine
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Triggered when speech input is recognized and transcribed.
     */
    fun onSpeechRecognized(transcription: String) {
        scope.launch {
            // 1. Interpretation
            val observation = Observation(
                id = ObservationId(UUID.randomUUID().toString()),
                timestamp = System.currentTimeMillis(),
                modality = Modality.AUDIO,
                source = Source.MICROPHONE,
                confidence = 1.0f,
                payload = SpeechRecognizedPayload(transcription, "en-US")
            )
            val result = interpretationEngine.interpret(observation)
            
            // 2. Goal Generation
            val intent = result.interpretation.intent
            val payload = when (intent) {
                is CallIntent -> CommunicationPayload(target = extractEntityValue(result.interpretation.entities) ?: "")
                is NavigateIntent -> NavigationPayload(destination = extractEntityValue(result.interpretation.entities) ?: "")
                is PlayIntent -> MediaPayload(query = extractEntityValue(result.interpretation.entities) ?: "")
                is OpenIntent -> DevicePayload(action = "open", target = extractEntityValue(result.interpretation.entities) ?: "")
                else -> ConversationPayload(topic = transcription)
            }
            val type = when (intent) {
                is CallIntent -> GoalType.COMMUNICATION
                is NavigateIntent -> GoalType.NAVIGATION
                is PlayIntent -> GoalType.MEDIA
                is OpenIntent -> GoalType.DEVICE
                else -> GoalType.CONVERSATION
            }
            val goal = goalManager.createGoal(type, intent, payload, Priority.NORMAL, result.interpretation.confidence)
            
            // 3. Planning
            val plan = planningEngine.createPlan(goal)
            
            // 4. Execution
            val outcome = executionEngine.executePlan(plan)
            
            // 5. Reflection (Timeline)
            reflectionEngine.reflectOnExecution(plan, outcome)
            
            // Return to Idle is handled by the caller or UI observing state
        }
    }

    private fun extractEntityValue(entities: List<Entity>): String? {
        val first = entities.firstOrNull() ?: return null
        return when (first) {
            is PersonEntity -> first.name
            is LocationEntity -> first.location
            is PlaceEntity -> first.name
            is ObjectEntity -> first.name
            is ApplicationEntity -> first.name
            is DeviceEntity -> first.name
            is MediaEntity -> first.title
            is WebEntity -> first.query
            is TimeEntity -> first.expression
            is DateEntity -> first.expression
            is NumberEntity -> first.value.toString()
        }
    }
}
