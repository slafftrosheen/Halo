package com.halo.engines.interpretation

import com.halo.engines.interpretation.internal.*
import com.halo.engines.interpretation.rules.ReminderRule
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.clarification.DefaultClarificationCoordinator
import com.halo.cognition.language.ReminderEntity
import com.halo.cognition.language.ReminderIntent
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReminderInterpretationTest {
    private val events = object : EventBus {
        override suspend fun publish(event: HaloEvent) = Unit
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        override fun events(): Flow<HaloEvent> = emptyFlow()
    }
    private fun engine() = DefaultInterpretationEngine(RulePipeline(RuleRegistry().apply { registerHardRule(ReminderRule()) }), DefaultBlackboard(), events, DefaultClarificationCoordinator())
    private fun observation(text: String) = Observation(ObservationId(text), Modality.AUDIO, Source.MICROPHONE,
        SpeechRecognizedPayload(text, "en"), timestamp = System.currentTimeMillis())
    @Test fun invalidMissingAndAmbiguousTimesStayInClarification() = runBlocking {
        for (text in listOf("remind me to call John", "remind me to call John at seven", "remind me to call John at banana", "remind me to call John at 32:91")) {
            val interpretation = engine().interpret(observation(text)).interpretation
            assertEquals(text, ReminderIntent, interpretation.intent)
            assertEquals(text, "true", interpretation.metadata["clarificationOnly"])
        }
    }
    @Test fun validRemindersAndEveningFollowupExecuteOnlyOnce() = runBlocking {
        for (text in listOf("remind me to call John at seven pm", "remind me tomorrow morning to call John")) {
            val interpretation = engine().interpret(observation(text)).interpretation
            assertEquals(ReminderIntent, interpretation.intent)
            assertNotEquals("true", interpretation.metadata["clarificationOnly"])
            assertNotNull(interpretation.entities.filterIsInstance<ReminderEntity>().single().temporalExpression)
        }
        val engine = engine()
        engine.interpret(observation("remind me to call John at seven"))
        val resolved = engine.interpret(observation("in the evening")).interpretation
        assertEquals(ReminderIntent, resolved.intent)
        assertEquals("at 19:00", resolved.entities.filterIsInstance<ReminderEntity>().single().temporalExpression)
        val repeated = engine.interpret(observation("in the evening")).interpretation
        assertNotEquals(ReminderIntent, repeated.intent)
    }
}
