package com.halo.engines.reasoning.strategy

import com.halo.cognition.language.CurrentTimeIntent
import com.halo.cognition.language.MediaEntity
import com.halo.cognition.language.PlayIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.SetAlarmIntent
import com.halo.cognition.language.TimeEntity
import com.halo.engines.reasoning.service.GoalManager
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.MediaPayload
import com.halo.runtime.cognition.blackboard.Interpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultReasoningStrategyTest {

    private lateinit var goalManager: GoalManager
    private lateinit var strategy: DefaultReasoningStrategy

    @Before
    fun setup() {
        goalManager = GoalManager()
        strategy = DefaultReasoningStrategy(goalManager)
    }

    @Test
    fun testPlayMusicMapsToGenericPlayAction() {
        val interpretation = Interpretation(
            transcript = "play music",
            intent = PlayIntent,
            entities = listOf(MediaEntity("Music.", 1.0f))
        )
        val goal = strategy.generateGoal(interpretation)

        assertEquals(GoalType.MEDIA, goal.type)
        assertTrue(goal.payload is MediaPayload)
        val payload = goal.payload as MediaPayload
        assertEquals("", payload.query)
        assertEquals("play", payload.action)
    }

    @Test
    fun testPlaySomeMusicMapsToGenericPlayAction() {
        val interpretation = Interpretation(
            transcript = "play some music",
            intent = PlayIntent,
            entities = listOf(MediaEntity("Some Music.", 1.0f))
        )
        val goal = strategy.generateGoal(interpretation)

        assertEquals(GoalType.MEDIA, goal.type)
        assertTrue(goal.payload is MediaPayload)
        val payload = goal.payload as MediaPayload
        assertEquals("", payload.query)
        assertEquals("play", payload.action)
    }

    @Test
    fun testPlayDaftPunkPreservesQuery() {
        val interpretation = Interpretation(
            transcript = "play Daft Punk",
            intent = PlayIntent,
            entities = listOf(MediaEntity("Daft Punk", 1.0f))
        )
        val goal = strategy.generateGoal(interpretation)

        assertEquals(GoalType.MEDIA, goal.type)
        assertTrue(goal.payload is MediaPayload)
        val payload = goal.payload as MediaPayload
        assertEquals("Daft Punk", payload.query)
        assertEquals("play", payload.action)
    }

    @Test
    fun testPlayOneMoreTimePreservesQuery() {
        val interpretation = Interpretation(
            transcript = "play One More Time",
            intent = PlayIntent,
            entities = listOf(MediaEntity("One More Time", 1.0f))
        )
        val goal = strategy.generateGoal(interpretation)

        assertEquals(GoalType.MEDIA, goal.type)
        assertTrue(goal.payload is MediaPayload)
        val payload = goal.payload as MediaPayload
        assertEquals("One More Time", payload.query)
        assertEquals("play", payload.action)
    }

    @Test
    fun testAlarmPrefersNormalizedTimeEntityOverHigherConfidenceRawPhrase() {
        val interpretation = Interpretation(
            transcript = "set an alarm for tomorrow at seven",
            intent = SetAlarmIntent,
            entities = listOf(
                TimeEntity("tomorrow at seven", 0.99f),
                TimeEntity("tomorrow 07:00", 0.98f)
            )
        )

        val goal = strategy.generateGoal(interpretation)

        assertEquals(GoalType.DEVICE, goal.type)
        val payload = goal.payload as DevicePayload
        assertEquals("set_alarm", payload.action)
        assertEquals("tomorrow 07:00", payload.target)
    }

    @Test
    fun testAlarmKeepsDirectNumericClockExpression() {
        val interpretation = Interpretation(
            transcript = "set an alarm for 07:30",
            intent = SetAlarmIntent,
            entities = listOf(TimeEntity("07:30", 0.99f))
        )

        val goal = strategy.generateGoal(interpretation)

        val payload = goal.payload as DevicePayload
        assertEquals("07:30", payload.target)
    }

    @Test
    fun testTimeQuestionMapsToDeviceTimePayload() {
        val interpretation = Interpretation(
            transcript = "what time is it",
            intent = CurrentTimeIntent
        )
        val goal = strategy.generateGoal(interpretation)

        assertEquals(GoalType.DEVICE, goal.type)
        assertTrue(goal.payload is DevicePayload)
        val payload = goal.payload as DevicePayload
        assertEquals("time", payload.action)
    }
}
