package com.halo.engines.planning

import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.cognition.language.DayRelation
import com.halo.cognition.language.TemporalValue
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningParameterTransportTest {

    @Test
    fun `device payload parameters and target survive planning`() {
        val planner = PlanningEngine(NoopEventBus, DefaultBlackboard())
        val temporalValue = TemporalValue.TimeOfDay(LocalTime.of(6, 0), DayRelation.TOMORROW)
        val goal = Goal(
            id = CognitiveId(),
            created = Clock.System.now(),
            confidence = Confidence(1f),
            provenance = Provenance("test", null),
            edges = emptyList(),
            type = GoalType.DEVICE,
            payload = DevicePayload(
                action = "set_alarm",
                target = "clock",
                parameters = mapOf("temporal_value" to temporalValue),
            ),
        )

        val task = planner.createPlan(goal).tasks.single() as DeviceTask

        assertEquals(temporalValue, task.parameters["temporal_value"])
        assertEquals("clock", task.parameters["target"])
    }

    private object NoopEventBus : EventBus {
        override suspend fun publish(event: HaloEvent) = Unit
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
}
