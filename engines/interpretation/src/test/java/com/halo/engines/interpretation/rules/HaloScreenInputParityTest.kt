package com.halo.engines.interpretation.rules

import com.halo.cognition.language.NativeHaloScreenDestination
import com.halo.cognition.language.NativeHaloScreenIntent
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot
import com.halo.sdk.runtime.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test

class HaloScreenInputParityTest {
    @Test fun typedAndSttFinalPayloadsResolveIdenticalPagesWithoutAClassifier() = runBlocking {
        val rule = SpeechRule { error("Exact navigation must not need a model") }
        NativeHaloScreenDestination.entries.forEach { destination ->
            destination.phrases.forEach { phrase ->
                listOf(Source.USER, Source.MICROPHONE).forEach { source ->
                    val context = InterpretationContext(
                        observation = Observation(id = ObservationId("screen-parity"),
                            modality = if (source == Source.USER) Modality.TEXT else Modality.AUDIO,
                            source = source, payload = SpeechRecognizedPayload(transcript = phrase, language = "und"),
                            timestamp = System.currentTimeMillis()),
                        workingMemory = WorkingMemorySnapshot(currentGoal = null, activeTask = null,
                            latestObservation = null, latestInterpretation = null, focusedPerson = null, currentLocation = null),
                        goals = emptyList(), tasks = emptyList(), timestamp = Clock.System.now())
                    val match = rule.match(context)
                    assertEquals("$source: $phrase", NativeHaloScreenIntent(destination.wireId), match?.intent)
                    assertEquals(0.99f, match!!.confidence, 0.001f)
                }
            }
        }
    }
}
