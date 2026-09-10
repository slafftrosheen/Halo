package com.halo.runtime.observer.replay

import com.halo.sdk.runtime.BatteryObservationPayload
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationCreated
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.SystemObservationPayload
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeJsonSerializerTest {

    private val serializer = RuntimeJsonSerializer()

    @Test
    fun `system observation payload is serializable`() {
        val observation = Observation(
            sourceDeviceId = "AA:BB:CC:DD:EE:FF",
            modality = Modality.SYSTEM,
            source = Source.BLUETOOTH,
            payload = SystemObservationPayload("MODE_CHANGED")
        )
        val event = ObservationCreated(observation = observation)

        val json = serializer.serialize(event)

        assertTrue(json.contains("MODE_CHANGED"))
        assertTrue(json.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `battery observation payload is serializable`() {
        val observation = Observation(
            sourceDeviceId = "AA:BB:CC:DD:EE:FF",
            modality = Modality.SYSTEM,
            source = Source.BLUETOOTH,
            payload = BatteryObservationPayload(50)
        )
        val event = ObservationCreated(observation = observation)

        val json = serializer.serialize(event)

        assertTrue(json.contains("50"))
        assertTrue(json.contains("AA:BB:CC:DD:EE:FF"))
    }
}
