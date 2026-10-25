package com.halo.engines.interpretation.rules

import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.ToolActionIntent
import com.halo.cognition.language.ToolActionReason
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot
import com.halo.sdk.runtime.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test

class SelectedCanvasWriteSpeechRuleTest {
    private val exact = "Change only the selected text card on this Canvas to exactly: Silver kites rest at noon. They fly west at dusk."

    private fun context(text: String, source: Source) = InterpretationContext(
        observation = Observation(id = ObservationId("selected-canvas-write"), source = source,
            modality = if (source == Source.USER) Modality.TEXT else Modality.AUDIO,
            payload = SpeechRecognizedPayload(transcript = text, language = "en"), timestamp = 1L),
        workingMemory = WorkingMemorySnapshot(currentGoal = null, activeTask = null, latestObservation = null,
            latestInterpretation = null, focusedPerson = null, currentLocation = null),
        goals = emptyList(), tasks = emptyList(), timestamp = Clock.System.now())

    @Test fun exactPhoneGoalRoutesBothTypedAndFinalSpeechToToolsWhenClassifierIsAbsent() = runBlocking {
        val rule = SpeechRule { null }
        listOf(Source.USER, Source.MICROPHONE).forEach { source ->
            val match = rule.match(context(exact, source))
            assertEquals(source.name, ToolActionIntent(ToolActionReason.ACTION_INTENT), match?.intent)
            assertEquals(0.95f, requireNotNull(match).confidence, 0.001f)
            assertEquals("ACTION_INTENT", match.diagnostics)
            assertTrue("Action routing cannot fabricate selected card/board IDs or write slots", match.entities.isEmpty())
        }
    }

    @Test fun classifierFailureRetainsNarrowChangeAndOnlyWorkspaceFallback() = runBlocking {
        val rule = SpeechRule { throw IllegalStateException("Local intent classifier unavailable") }
        listOf(
            exact,
            "Change the selected text card to Silver kites rest at noon.",
            "Update only the selected text card on this Canvas to exactly: Silver kites rest at noon.",
            "Please edit only the selected text card on this Canvas.",
            "Can you change only this selected text card to Silver kites rest at noon?",
        ).forEach { request ->
            val match = rule.match(context(request, Source.MICROPHONE))
            assertEquals(request, ToolActionIntent(ToolActionReason.ACTION_INTENT), match?.intent)
            assertTrue(request, match?.entities.orEmpty().isEmpty())
        }
    }

    @Test fun conversationNegationQuotesAndUnrelatedChangeAreNeverFallbackToolActions() = runBlocking {
        val rule = SpeechRule { null }
        listOf(
            "How do I change only the selected text card on this Canvas?",
            "Can you explain how to change only the selected text card?",
            "Do not change only the selected text card on this Canvas.",
            "Could you not change only the selected text card?",
            "Don't use tools; change only the selected text card.",
            "Answer from your model: change only the selected text card.",
            "\"Change only the selected text card on this Canvas\"",
            "The instruction says: Change only the selected text card on this Canvas.",
            "Change is hard when writing a story about a Canvas.",
            "Change only the selected text color to silver.",
            "Change only the selected existing local text card to silver.",
        ).forEach { request ->
            listOf(Source.USER, Source.MICROPHONE).forEach { source ->
                assertFalse("$source: $request", rule.match(context(request, source))?.intent is ToolActionIntent)
            }
        }
    }

    @Test fun explicitExplanationAndModelOnlyStillTakePrecedenceOverAnActionClassifyingModel() = runBlocking {
        var classified = 0
        val rule = SpeechRule { classified++; ModelIntentDecision.ACTION }
        listOf("Explain how to change only the selected text card.",
            "Don't use tools; change only the selected text card.",
            "Answer from your model: change only the selected text card.").forEach { request ->
            assertEquals(request, QuestionIntent, rule.match(context(request, Source.USER))?.intent)
        }
        assertEquals(0, classified)
    }
}
