package com.halo.engines.interpretation.rules

import com.halo.cognition.language.ToolActionIntent
import com.halo.cognition.language.ToolActionReason
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.model.QuestionIntent
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationId
import com.halo.sdk.runtime.Source
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test

class SmallIntentSpeechRuleTest {
    private fun context(phrase: String, language: String = "en"): InterpretationContext = InterpretationContext(
        observation = Observation(id = ObservationId("intent-final"),
            modality = Modality.AUDIO, source = Source.MICROPHONE,
            payload = SpeechRecognizedPayload(transcript = phrase, language = language),
            timestamp = System.currentTimeMillis()),
        workingMemory = WorkingMemorySnapshot(currentGoal=null,activeTask=null,
            latestObservation=null,latestInterpretation=null,focusedPerson=null,currentLocation=null),
        goals=emptyList(),tasks=emptyList(),timestamp=Clock.System.now())

    @Test fun tinyModelMayRouteVagueRequestToToolsButNeverSuppliesArguments() = runBlocking {
        val rule = SpeechRule { ModelIntentDecision.ACTION }
        val match = rule.match(context("Could you get the newest information from my workspace?"))
        assertEquals(ToolActionIntent(ToolActionReason.ACTION_INTENT), match?.intent)
        assertTrue(match?.entities.orEmpty().isEmpty())
        assertEquals("Local intent: tool-capable route", match?.diagnostics)
    }

    @Test fun tinyModelPreventsBroadCommandRuleFromBecomingAnAction() = runBlocking {
        val rule = SpeechRule { ModelIntentDecision.ANSWER }
        assertEquals(QuestionIntent,rule.match(context("Open ideas for my art project"))?.intent)
    }

    @Test fun actionClassificationRetainsScreenOperationHint() = runBlocking {
        val rule = SpeechRule { ModelIntentDecision.ACTION }
        val match = rule.match(context("Open Gmail and find the latest invoice"))
        assertEquals(ToolActionIntent(ToolActionReason.SCREEN_OPERATION), match?.intent)
    }

    @Test fun uncertainClassificationAsksUserRatherThanCallingATool() = runBlocking {
        val rule = SpeechRule { ModelIntentDecision.UNCERTAIN }
        val match = rule.match(context("Open a completely unknown thing"))
        val clarification = match?.intent as? com.halo.cognition.language.IntentNeedsClarification
        assertNotNull(clarification)
        assertTrue(clarification!!.prompt.contains("explain how"))
        assertEquals(0.7f,match.confidence,0.001f)
    }

    @Test fun uncertainClassificationPromptsInVoiceLanguage() = runBlocking {
        val rule = SpeechRule { ModelIntentDecision.UNCERTAIN }
        mapOf(
            "ru-RU" to "Вы хотите",
            "lv-LV" to "Vai vēlaties",
            "pl-PL" to "Czy Halo",
            "en-US" to "Would you like"
        ).forEach { (language,prefix) ->
            val match = rule.match(context("Maybe open something",language))
            val intent = match?.intent as? com.halo.cognition.language.IntentNeedsClarification
            assertNotNull(language,intent)
            assertTrue(language,intent!!.prompt.startsWith(prefix))
        }
    }

    @Test fun exactNativeCommandWinsAndClassifierCannotOverrideIt() = runBlocking {
        var called = false
        val rule = SpeechRule { called = true; ModelIntentDecision.ANSWER }
        assertEquals(com.halo.cognition.language.FlashlightIntent(true),
            rule.match(context("turn on flashlight"))?.intent)
        assertFalse(called)
    }

    @Test fun partialASRNeverInvokesTheModel() = runBlocking {
        var called = false
        val rule = SpeechRule { called = true; ModelIntentDecision.ACTION }
        rule.previewMatch(context("Find the latest"))
        assertFalse(called)
    }

    @Test fun modelTimeoutFallbackRetainsRuleRouting() = runBlocking {
        val rule = SpeechRule { null }
        assertEquals(com.halo.engines.interpretation.model.SearchIntent,
            rule.match(context("search for cats"))?.intent)
    }
}
