package com.halo.engines.interpretation.rules

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

class SavedContextSpeechRuleTest {
    private fun context(text: String, source: Source) = InterpretationContext(
        observation = Observation(id = ObservationId("saved-context-qa"), source = source,
            modality = if (source == Source.USER) Modality.TEXT else Modality.AUDIO,
            payload = SpeechRecognizedPayload(transcript = text, language = "en"), timestamp = 1L),
        workingMemory = WorkingMemorySnapshot(null, null, null, null, null, null),
        goals = emptyList(), tasks = emptyList(), timestamp = Clock.System.now())

    @Test fun typedAndFinalSpeechSummariesReadSavedContextEvenWithoutAnIntentModel() = runBlocking {
        val phrases = listOf("Summarize my day", "Sum up the passed day", "What happened yesterday?",
            "Could you summarize my notes?", "Compare my saved articles", "Read my notifications",
            "Подведи итоги дня", "Обобщи мои уведомления", "Apkopo manu dienu", "Apkopo manus avotus",
            "Podsumuj mój dzień", "Podsumuj moje źródła")
        // A local model's generic ANSWER label must not strip required owner reads.
        for (classifier in listOf<ModelIntentDecision?>(null, ModelIntentDecision.ANSWER)) {
            val rule = SpeechRule { classifier }
            for (phrase in phrases) for (source in listOf(Source.USER, Source.MICROPHONE)) {
                val match = rule.match(context(phrase, source))
                assertEquals(phrase, ToolActionIntent(ToolActionReason.WORKSPACE_LOOKUP), match?.intent)
                assertTrue(match?.entities.orEmpty().isEmpty())
            }
        }
    }

    @Test fun exactPhoneWorkflowIsActionEvidenceWithoutInventingNoteFields() = runBlocking {
        val phrase = "Create exactly one note titled Halo workflow QA with body The orange sail is ready. Then read the saved note and report its body."
        for (source in listOf(Source.USER, Source.MICROPHONE)) {
            val match = SpeechRule { null }.match(context(phrase, source))
            assertEquals(ToolActionIntent(ToolActionReason.ACTION_INTENT), match?.intent)
            assertTrue(match?.entities.orEmpty().isEmpty())
        }
    }

    @Test fun explanationsQuotedInstructionsAndModelOnlyRequestsStayNonActionable() = runBlocking {
        for (phrase in listOf("How do I summarize my day?", "Explain how to summarize my notifications",
            "Summarize this text about a day in ancient Rome", "Do not summarize my day",
            "Answer from your model: summarize my day", "Don't use tools; summarize my notes",
            "\"Summarize my day\"", "The example says: summarize my day", "Расскажи как подвести итоги дня")) {
            assertFalse(phrase, SpeechRule { null }.match(context(phrase, Source.USER))?.intent is ToolActionIntent)
        }
    }
}
