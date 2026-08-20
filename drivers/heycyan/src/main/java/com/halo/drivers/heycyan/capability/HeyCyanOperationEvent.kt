package com.halo.drivers.heycyan.capability

import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

/**
 * Observable operation events emitted by [HeyCyanCapabilityController].
 *
 * These events are transport-agnostic — consumers (Timeline, Metrics, Runtime
 * Observation, Halo Glass Activity) do not need to know anything about HeyCyan
 * BLE or GATT to consume them.
 */
sealed interface HeyCyanOperationEvent {

    data class Started(
        val operation: HeyCyanOperation,
        val timestamp: Long,
    ) : HeyCyanOperationEvent

    data class Completed(
        val operation: HeyCyanOperation,
        val timestamp: Long,
    ) : HeyCyanOperationEvent

    data class Failed(
        val operation: HeyCyanOperation,
        val timestamp: Long,
        val reason: HeyCyanOperationFailure,
    ) : HeyCyanOperationEvent
}
