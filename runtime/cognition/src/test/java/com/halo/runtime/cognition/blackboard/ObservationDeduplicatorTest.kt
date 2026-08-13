package com.halo.runtime.cognition.blackboard

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationDeduplicatorTest {

    @Test
    fun `deduplicator emits only on state change`() = runTest {
        val deduplicator = ObservationDeduplicator()

        val obs1 = Observation(
            modality = Modality.SENSOR,
            source = Source.ROBOT,
            sourceDeviceId = "bot_1",
            freshness = Freshness.FRESH,
            payload = RangeObservationPayload(100, true)
        )

        val obs2 = obs1.copy() // Identical
        
        val obs3 = obs1.copy(payload = RangeObservationPayload(150, true)) // Value change

        val obs4 = obs3.copy() // Identical to obs3

        val obs5 = obs3.copy(freshness = Freshness.STALE) // Freshness change

        val input = listOf(obs1, obs2, obs3, obs4, obs5)
        
        val output = deduplicator.deduplicate(input.asFlow()).toList()

        assertEquals(3, output.size)
        assertEquals(obs1.payload, output[0].payload)
        assertEquals(obs3.payload, output[1].payload)
        assertEquals(obs5.freshness, output[2].freshness)
    }
}
