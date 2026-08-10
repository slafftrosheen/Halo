package com.halo.drivers.heycyan.transport

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanOperation

/**
 * Result of executing a low-level command across a transport channel.
 */
sealed interface HeyCyanCommandResult {

    data class Acknowledged(
        val command: HeyCyanCommand,
    ) : HeyCyanCommandResult

    data class Response(
        val command: HeyCyanCommand,
        val event: HeyCyanEvent?,
    ) : HeyCyanCommandResult

    data class Rejected(
        val command: HeyCyanCommand,
        val reason: String,
    ) : HeyCyanCommandResult
}

/**
 * Retry safety classification for transport routing and fallback rules.
 */
enum class RetrySafety {
    /** Safe to retry/fallback automatically (e.g., GetBattery, GetVersion, GetMediaCounts). */
    IDEMPOTENT,

    /** NOT safe to retry automatically to prevent duplicate physical actions (e.g., TakePhoto, StartVideo). */
    NOT_IDEMPOTENT,

    /** Unknown retry safety — do not retry automatically. */
    UNKNOWN,
}

/**
 * Execution failures for unified routing and transport dispatch.
 */
sealed interface HeyCyanExecutionFailure {

    data class TransportUnavailable(
        val transport: HeyCyanTransportType,
    ) : HeyCyanExecutionFailure

    data class TransportFailure(
        val transport: HeyCyanTransportType,
        val message: String,
    ) : HeyCyanExecutionFailure

    data class DeviceRejected(
        val operation: HeyCyanOperation,
        val reason: String,
    ) : HeyCyanExecutionFailure

    data class Unsupported(
        val operation: HeyCyanOperation,
    ) : HeyCyanExecutionFailure

    data class ProtocolUnavailable(
        val operation: HeyCyanOperation,
        val transport: HeyCyanTransportType,
    ) : HeyCyanExecutionFailure

    /**
     * Non-idempotent operation was transmitted over transport, but confirmation timed out.
     * MUST NOT automatically fallback or retry to prevent duplicate physical actions.
     */
    data class UnknownOutcome(
        val operation: HeyCyanOperation,
        val transport: HeyCyanTransportType,
        val reason: String,
    ) : HeyCyanExecutionFailure
}
