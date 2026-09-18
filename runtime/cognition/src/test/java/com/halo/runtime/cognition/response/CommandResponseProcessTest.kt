package com.halo.runtime.cognition.response

import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence as IclConfidence
import com.halo.icl.core.Provenance
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.blackboard.Confidence
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.runtime.cognition.blackboard.KnowledgeId
import com.halo.runtime.cognition.blackboard.KnowledgeSource
import com.halo.runtime.cognition.blackboard.PlanKnowledge
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ExecutionFinished
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandResponseProcessTest {

    @Test
    fun `visual only command publishes response without tts`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val process = CommandResponseProcess(
            eventBus = eventBus,
            blackboard = blackboard,
            scope = this
        )
        process.start()
        runCurrent()

        val (plan, task) = createPlan(ResponseDelivery.VISUAL_ONLY)
        publishPlan(blackboard, plan)

        eventBus.publish(
            ExecutionResultPublished(
                executionId = "exec-visual",
                planId = plan.id.toString(),
                task = task,
                outcome = Outcome.Success("settings")
            )
        )
        eventBus.publish(
            ExecutionFinished(
                id = "exec-visual",
                planId = plan.id.toString()
            )
        )
        runCurrent()

        val response = eventBus.published.filterIsInstance<AssistantResponsePublished>().single()
        assertEquals("Opening settings.", response.text)
        assertEquals(ResponseDelivery.VISUAL_ONLY, response.delivery)
        assertFalse(eventBus.published.any { it is TtsRequested })

        process.stop()
    }

    @Test
    fun `speech command publishes response and tts`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val process = CommandResponseProcess(
            eventBus = eventBus,
            blackboard = blackboard,
            scope = this
        )
        process.start()
        runCurrent()

        val (plan, task) = createPlan(ResponseDelivery.SPEECH_AND_VISUAL)
        publishPlan(blackboard, plan)

        eventBus.publish(
            ExecutionResultPublished(
                executionId = "exec-speech",
                planId = plan.id.toString(),
                task = task,
                outcome = Outcome.Success("settings")
            )
        )
        eventBus.publish(
            ExecutionFinished(
                id = "exec-speech",
                planId = plan.id.toString()
            )
        )
        runCurrent()

        val response = eventBus.published.filterIsInstance<AssistantResponsePublished>().single()
        assertEquals(ResponseDelivery.SPEECH_AND_VISUAL, response.delivery)
        assertTrue(
            eventBus.published.filterIsInstance<TtsRequested>()
                .any { it.text == response.text }
        )

        process.stop()
    }

    private suspend fun publishPlan(
        blackboard: DefaultBlackboard,
        plan: ExecutionPlan
    ) {
        blackboard.publish(
            Knowledge(
                id = KnowledgeId(plan.id.toString()),
                payload = PlanKnowledge(plan),
                confidence = Confidence(1f, 1f, 1f),
                source = KnowledgeSource.RULE,
                createdAt = Clock.System.now()
            )
        )
    }

    private fun createPlan(delivery: ResponseDelivery): Pair<ExecutionPlan, DeviceTask> {
        val goal = Goal(
            id = CognitiveId(),
            created = Clock.System.now(),
            confidence = IclConfidence(1f),
            provenance = Provenance("test", null),
            edges = emptyList(),
            type = GoalType.DEVICE,
            payload = DevicePayload(action = "settings", target = "")
        )
        val task = DeviceTask(action = "settings")
        return ExecutionPlan(
            goal = goal,
            tasks = listOf(task),
            responseDelivery = delivery
        ) to task
    }

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        private val stream = MutableSharedFlow<HaloEvent>(extraBufferCapacity = 64)

        override suspend fun publish(event: HaloEvent) {
            published += event
            stream.emit(event)
        }

        override fun events(): Flow<HaloEvent> = stream.asSharedFlow()

        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> =
            stream.filter { type.isInstance(it) }.map { type.cast(it) }
    }
}
