package com.halo.engines.reasoning.strategy

import com.halo.cognition.language.CurrentTimeIntent
import com.halo.cognition.language.MediaEntity
import com.halo.cognition.language.PlayIntent
import com.halo.cognition.language.QuestionIntent
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
