package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.model.ApplicationEntity
import com.halo.engines.interpretation.model.CallIntent
import com.halo.engines.interpretation.model.ConversationIntent
import com.halo.engines.interpretation.model.MediaEntity
import com.halo.engines.interpretation.model.NavigateIntent
import com.halo.engines.interpretation.model.OpenIntent
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.interpretation.model.PlaceEntity
import com.halo.engines.interpretation.model.PlayIntent
import com.halo.engines.interpretation.model.ReminderIntent
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.engines.interpretation.rules.ContactRule
import com.halo.engines.interpretation.rules.SpeechRule
import com.halo.engines.interpretation.rules.TimeRule
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.context.CognitiveContext
import com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.Source
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePipelineTest {
    private val pipeline = RulePipeline(
        listOf(
            ContactRule(knownContacts = setOf("John", "Sarah")),
            TimeRule(),
            SpeechRule()
        )
    )

    @Test
    fun openSpotifyBuildsTypedIntentAndApplicationEntity(): Unit = runBlocking {
        val result = pipeline.execute(context("Open Spotify"))

        assertEquals(OpenIntent, result.interpretation.intent)
        assertTrue(
            result.interpretation.entities.any {
                it is ApplicationEntity && it.name == "Spotify"
            }
        )
    }

    @Test
    fun navigateHomeBuildsTypedIntentAndPlaceEntity(): Unit = runBlocking {
        val result = pipeline.execute(context("Navigate home"))

        assertEquals(NavigateIntent, result.interpretation.intent)
        assertTrue(
            result.interpretation.entities.any {
                it is PlaceEntity && it.name == "Home"
            }
        )
    }

    @Test
    fun callCommandMergesIntentContactAndTimeMatches(): Unit = runBlocking {
        val result = pipeline.execute(context("Call John tomorrow morning"))

        assertEquals(CallIntent, result.interpretation.intent)
        assertEquals(3, result.matches.size)
        assertTrue(
            result.interpretation.entities.any {
                it is PersonEntity && it.name == "John"
            }
        )
        assertTrue(
            result.interpretation.entities.any {
                it is TimeEntity && it.expression == "tomorrow morning"
            }
        )
        assertEquals(0.9068895f, result.interpretation.confidence.overall, 0.0001f)
    }

    @Test
    fun playCommandExtractsMediaEntity(): Unit = runBlocking {
        val result = pipeline.execute(context("Play Metallica"))

        assertEquals(PlayIntent, result.interpretation.intent)
        assertTrue(
            result.interpretation.entities.any {
                it is MediaEntity && it.title == "Metallica"
            }
        )
    }

    @Test
    fun reminderCommandExtractsTimeEntity(): Unit = runBlocking {
        val result = pipeline.execute(context("Remind me tomorrow at six"))

        assertEquals(ReminderIntent, result.interpretation.intent)
        assertTrue(
            result.interpretation.entities.any {
                it is TimeEntity && it.expression == "tomorrow 06:00"
            }
        )
    }

    @Test
    fun unmatchedTranscriptFallsBackToConversationIntent(): Unit = runBlocking {
        val result = pipeline.execute(context("Tell me something interesting"))

        assertEquals(ConversationIntent, result.interpretation.intent)
        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun matchingAndMergingAreDeterministic(): Unit = runBlocking {
        val first = pipeline.execute(context("Call John tomorrow morning"))
        val second = pipeline.execute(context("Call John tomorrow morning"))

        assertEquals(first.interpretation, second.interpretation)
        assertEquals(
            first.matches.map { it.ruleName },
            second.matches.map { it.ruleName }
        )
    }

    private fun context(
        transcript: String,
        confidence: Float = 0.93f
    ): CognitiveContext {
        val observation = Observation(
            modality = Modality.AUDIO,
            source = Source.MICROPHONE,
            payload = SpeechRecognizedPayload(transcript, "en"),
            confidence = confidence,
            timestamp = 1_000L
        )
        return CognitiveContext(
            observation = observation,
            workingMemory = WorkingMemorySnapshot(
                currentGoal = null,
                activeTask = null,
                latestObservation = null,
                latestInterpretation = null,
                focusedPerson = null,
                currentLocation = null
            ),
            goals = emptyList(),
            tasks = emptyList(),
            timestamp = Instant.fromEpochMilliseconds(observation.timestamp)
        )
    }
}
