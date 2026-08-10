package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanOperation

/**
 * Lifecycle state of a single HeyCyanOperation execution.
 *
 * Transitions:
 *   Idle → Running → Succeeded
 *                  → Failed
 */
sealed interface HeyCyanOperationState {

    data object Idle : HeyCyanOperationState

    data class Running(
        val operation: HeyCyanOperation,
    ) : HeyCyanOperationState

    data class Succeeded(
        val operation: HeyCyanOperation,
    ) : HeyCyanOperationState

    data class Failed(
        val operation: HeyCyanOperation,
        val reason: HeyCyanOperationFailure,
    ) : HeyCyanOperationState
}

/**
 * Reason classifications for [HeyCyanOperationState.Failed].
 *
 * The distinction between [ProtocolNotEstablished] and [Unsupported] is load-bearing
 * for the Halo Glass Devices screen: one means "we could support this later",
 * the other means "the device hardware doesn't support it at all".
 */
sealed interface HeyCyanOperationFailure {

    /** The glasses are not currently connected via BLE. */
    data class NotConnected(
        val message: String = "HeyCyan is not connected",
    ) : HeyCyanOperationFailure

    /** The capability is explicitly unsupported by the device hardware. */
    data class Unsupported(
        val capabilityId: String,
    ) : HeyCyanOperationFailure

    /**
     * The capability semantics are known but the complete BLE wire protocol
     * to execute it has not been established from real-device captures or
     * authoritative vendor documentation.
     */
    data class ProtocolNotEstablished(
        val operation: HeyCyanOperation,
        val detail: String,
    ) : HeyCyanOperationFailure

    /** A transport-layer error occurred while sending or receiving over BLE. */
    data class Transport(
        val message: String,
        val cause: Throwable? = null,
    ) : HeyCyanOperationFailure

    /** The device rejected the command (e.g., conflicting recording mode). */
    data class DeviceRejected(
        val message: String,
    ) : HeyCyanOperationFailure
}

/**
 * Exception wrapper for [HeyCyanOperationFailure], used to propagate failure
 * through Kotlin's [Result] machinery without losing structured failure context.
 */
class HeyCyanCapabilityException(
    val failure: HeyCyanOperationFailure,
) : RuntimeException(failure.toMessage())

private fun HeyCyanOperationFailure.toMessage(): String = when (this) {
    is HeyCyanOperationFailure.NotConnected -> message
    is HeyCyanOperationFailure.Unsupported ->
        "Capability $capabilityId is not supported by HeyCyan hardware"
    is HeyCyanOperationFailure.ProtocolNotEstablished ->
        "HeyCyan $operation blocked: $detail"
    is HeyCyanOperationFailure.Transport -> message
    is HeyCyanOperationFailure.DeviceRejected -> message
}

/**
 * Converts a [Throwable] from transport/executor layers into a structured
 * [HeyCyanOperationFailure] for the capability controller.
 */
fun Throwable.toOperationFailure(): HeyCyanOperationFailure =
    when (this) {
        is HeyCyanCapabilityException -> failure
        else -> HeyCyanOperationFailure.Transport(
            message = message ?: "Unknown transport error",
            cause = this,
        )
    }
