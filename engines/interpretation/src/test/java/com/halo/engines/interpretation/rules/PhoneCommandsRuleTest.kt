package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.model.ApplicationEntity
import com.halo.engines.interpretation.model.CallIntent
import com.halo.engines.interpretation.model.MediaEntity
import com.halo.engines.interpretation.model.NavigateIntent
import com.halo.engines.interpretation.model.OpenIntent
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.interpretation.model.PlaceEntity
import com.halo.engines.interpretation.model.PlayIntent
import com.halo.engines.interpretation.model.QuestionIntent
import com.halo.engines.interpretation.model.ReminderIntent
import com.halo.engines.interpretation.model.SearchIntent
import com.halo.engines.interpretation.model.SetAlarmIntent
import com.halo.engines.interpretation.model.WebEntity
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationId
import com.halo.sdk.runtime.Source
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCommandsRuleTest {

    private val speechRule = SpeechRule()

    private fun createContext(transcript: String): InterpretationContext {
        val observation = Observation(
            id = ObservationId("obs1"),
            modality = Modality.AUDIO,
            source = Source.MICROPHONE,
            payload = SpeechRecognizedPayload(transcript = transcript, language = "en"),
            timestamp = System.currentTimeMillis()
        )
        return InterpretationContext(
            observation = observation,
            workingMemory = WorkingMemorySnapshot(
                currentGoal = null, activeTask = null, latestObservation = null, latestInterpretation = null, focusedPerson = null, currentLocation = null
            ),
            goals = emptyList(),
            tasks = emptyList(),
            timestamp = Clock.System.now()
        )
    }


    @Test
    fun dogfoodPhonePhrasesStayNativeThroughRegisteredSpeechPipeline() = runBlocking {
        val commands = mapOf(
            "Open Settings" to com.halo.cognition.language.OpenSettingsIntent("general"),
            "open settings" to com.halo.cognition.language.OpenSettingsIntent("general"),
            "Go home" to com.halo.cognition.language.NativeScreenActionIntent(
                com.halo.cognition.language.NativeScreenVerb.HOME, "current screen"),
            "go to the home screen" to com.halo.cognition.language.NativeScreenActionIntent(
                com.halo.cognition.language.NativeScreenVerb.HOME, "current screen"),
            "turn on flashlight" to com.halo.cognition.language.FlashlightIntent(true),
            "Turn on the flashlight" to com.halo.cognition.language.FlashlightIntent(true)
        )
        val pipeline = com.halo.engines.interpretation.internal.RulePipeline(
            com.halo.engines.interpretation.internal.RuleRegistry().apply {
                registerContextRule(ContactRule())
                registerContextRule(ReminderRule())
                registerContextRule(TimeRule())
                registerContextRule(SpeechRule())
            }
        )
        commands.forEach { (spoken, expected) ->
            assertEquals(spoken, expected, speechRule.match(createContext(spoken))?.intent)
            val interpreted = pipeline.execute(createContext(spoken)).interpretation
            assertEquals(spoken, expected, interpreted.intent)
            assertEquals(spoken, 0.99f, interpreted.confidence, 0.001f)
        }
        assertEquals(QuestionIntent,
            pipeline.execute(createContext("How do I turn on the flashlight?")).interpretation.intent)
    }

    @Test
    fun clockLifecyclePhrasesStayDeterministic() = runBlocking {
        val cases = mapOf(
            "Open timers" to "show_timers",
            "Show my alarms" to "show_alarms",
            "Snooze the alarm" to "snooze_alarm",
            "Dismiss the next alarm" to "dismiss_next_alarm"
        )
        cases.forEach { (spoken, action) ->
            assertEquals(com.halo.cognition.language.NativeClockIntent(action),
                speechRule.match(createContext(spoken))?.intent)
        }
    }

    @Test
    fun contactAndCalendarEditorsBypassModelAndNeedUserSave() = runBlocking {
        assertEquals(com.halo.cognition.language.NativeContactEditorIntent("create"),
            speechRule.match(createContext("Create new contact"))?.intent)
        assertEquals(com.halo.cognition.language.NativeContactEditorIntent("choose_or_edit"),
            speechRule.match(createContext("Edit a contact"))?.intent)
        assertEquals(com.halo.cognition.language.NativeCalendarEditorIntent,
            speechRule.match(createContext("Create a calendar event"))?.intent)
    }

    @Test
    fun testOpenApplication() = runBlocking {
        val match1 = speechRule.match(createContext("open YouTube"))
        assertNotNull(match1)
        assertEquals(OpenIntent, match1!!.intent)
        assertTrue(match1.entities.any { it is ApplicationEntity && it.name == "YouTube" })

        val match2 = speechRule.match(createContext("launch Chrome"))
        assertNotNull(match2)
        assertEquals(OpenIntent, match2!!.intent)
        assertTrue(match2.entities.any { it is ApplicationEntity && it.name == "Chrome" })
    }

    @Test
    fun compositePhoneRequestsPreserveActualExecutionIntent() = runBlocking {
        val script = speechRule.match(createContext("Open Gmail then tap Search and type invoice into Search"))
        assertTrue(script?.intent is com.halo.cognition.language.NativeCommandSequenceIntent)

        val goal = speechRule.match(createContext("Open Gmail and find the latest invoice"))
        assertEquals(com.halo.cognition.language.ToolActionIntent(
            com.halo.cognition.language.ToolActionReason.SCREEN_OPERATION), goal?.intent)
        assertTrue(goal?.entities.orEmpty().none { it is ApplicationEntity })

        val searchInApp = speechRule.match(createContext("Search in Gmail for invoice"))
        assertEquals(com.halo.cognition.language.ToolActionIntent(
            com.halo.cognition.language.ToolActionReason.SCREEN_OPERATION), searchInApp?.intent)
    }

    @Test
    fun testCallContact() = runBlocking {
        val match1 = speechRule.match(createContext("call John"))
        assertNotNull(match1)
        assertEquals(CallIntent, match1!!.intent)
        assertTrue(match1.entities.any { it is PersonEntity && it.name == "John" })

        val match2 = speechRule.match(createContext("call +37120000000"))
        assertNotNull(match2)
        assertEquals(CallIntent, match2!!.intent)
    }

    @Test
    fun testSearchWeb() = runBlocking {
        val match1 = speechRule.match(createContext("search for cats"))
        assertNotNull(match1)
        assertEquals(SearchIntent, match1!!.intent)
        assertTrue(match1.entities.any { it is WebEntity && it.query == "cats" })

        val match2 = speechRule.match(createContext("google cats"))
        assertNotNull(match2)
        assertEquals(SearchIntent, match2!!.intent)
        assertTrue(match2.entities.any { it is WebEntity && it.query == "cats" })
    }

    @Test
    fun testNavigation() = runBlocking {
        val match1 = speechRule.match(createContext("navigate to Riga"))
        assertNotNull(match1)
        assertEquals(NavigateIntent, match1!!.intent)
        assertTrue(match1.entities.any { it is PlaceEntity && it.name == "Riga" })

        val match2 = speechRule.match(createContext("directions to Riga"))
        assertNotNull(match2)
        assertEquals(NavigateIntent, match2!!.intent)
        assertTrue(match2.entities.any { it is PlaceEntity && it.name == "Riga" })
    }

    private val reminderRule = ReminderRule()

    @Test
    fun testReminder() = runBlocking {
        val match = reminderRule.match(createContext("remind me in 10 minutes to call John"))
        assertNotNull(match)
        assertEquals(ReminderIntent, match!!.intent)
    }

    @Test
    fun testAlarm() = runBlocking {
        val match = speechRule.match(createContext("set an alarm for 07:30"))
        assertNotNull(match)
        assertEquals(SetAlarmIntent, match!!.intent)
    }

    @Test
    fun testPrecedenceSearchForMusicVsPlayMusic() = runBlocking {
        val searchMatch = speechRule.match(createContext("search for music"))
        assertNotNull(searchMatch)
        assertEquals(SearchIntent, searchMatch!!.intent)
        assertTrue(searchMatch.entities.any { it is WebEntity && it.query == "music" })

        val playMatch = speechRule.match(createContext("play music"))
        assertNotNull(playMatch)
        assertEquals(PlayIntent, playMatch!!.intent)
        assertTrue(playMatch.entities.any { it is MediaEntity })
    }

    @Test
    fun testGenericConversationQuestion() = runBlocking {
        val match = speechRule.match(createContext("tell me about Riga"))
        assertTrue(match == null || match.intent is QuestionIntent)
    }
    @Test fun reminderSlotsKeepContentSeparateFromTime() = runBlocking {
        for ((phrase, time) in listOf(
            "Remind me to call John." to null,
            "Remind me tomorrow to call John." to "tomorrow",
            "Remind me tomorrow morning to call John." to "tomorrow morning",
            "Remind me tomorrow at seven to call John." to "tomorrow at seven",
            "Remind me to call John at seven." to "at seven")) {
            val match = reminderRule.match(createContext(phrase))!!
            val reminder = match.entities.filterIsInstance<com.halo.cognition.language.ReminderEntity>().single()
            assertEquals("call John", reminder.message)
            assertEquals(time, reminder.temporalExpression)
        }
    }
}
