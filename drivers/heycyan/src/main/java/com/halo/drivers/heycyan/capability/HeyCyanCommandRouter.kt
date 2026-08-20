package com.halo.drivers.heycyan.capability

import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.drivers.heycyan.transport.HeyCyanExecutionFailure
import com.halo.drivers.heycyan.transport.HeyCyanOperationRoute
import com.halo.drivers.heycyan.transport.HeyCyanTransportRoutes
import com.halo.drivers.heycyan.transport.HeyCyanTransportType
import com.halo.drivers.heycyan.transport.RetrySafety
import com.halo.drivers.heycyan.transport.rfcomm.HeyCyanRfcommSession

/**
 * Interface for executing commands via BLE transport.
 */
interface HeyCyanBleExecutor {
    suspend fun execute(operation: HeyCyanOperation): Result<Unit>
}

/**
 * Interface for executing commands via RFCOMM transport.
 */
interface HeyCyanRfcommExecutor {
    suspend fun execute(operation: HeyCyanOperation): Result<Unit>
}

/**
 * Unified Command Router.
 *
 * Dispatches [HeyCyanOperation] to the preferred transport (BLE_GATT or RFCOMM).
 *
 * Safety rules:
 * 1. Differentiates transport failures from device rejection / unsupported status.
 * 2. Never falls back on [HeyCyanExecutionFailure.DeviceRejected] or [HeyCyanExecutionFailure.Unsupported].
 * 3. Never falls back for [RetrySafety.NOT_IDEMPOTENT] operations (e.g., TakePhoto) if the primary transport timed out after sending.
 *    Returns [HeyCyanExecutionFailure.UnknownOutcome] instead to prevent duplicate physical actions.
 * 4. Only falls back automatically for [RetrySafety.IDEMPOTENT] operations (e.g., GetBattery, GetDeviceInfo).
 */
class HeyCyanCommandRouter(
    private val bleExecutor: HeyCyanBleExecutor?,
    private val rfcommExecutor: HeyCyanRfcommExecutor?,
    private val routes: Map<HeyCyanOperation, HeyCyanOperationRoute> = HeyCyanTransportRoutes.operationRoutes,
) {
    suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        val route = routes[operation]
            ?: return Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Unsupported("No transport route defined for $operation")
                )
            )

        // Attempt preferred transport
        val primaryResult = executeTransport(route.preferred, operation)
        if (primaryResult.isSuccess) {
            return primaryResult
        }

        val primaryError = primaryResult.exceptionOrNull()
        if (primaryError is HeyCyanCapabilityException) {
            val failure = primaryError.failure
            // Do not fallback on device rejection or unsupported
            if (failure is HeyCyanOperationFailure.DeviceRejected ||
                failure is HeyCyanOperationFailure.Unsupported ||
                failure is HeyCyanOperationFailure.ProtocolNotEstablished
            ) {
                return primaryResult
            }
        }

        // NON-IDEMPOTENT guard: timeout/ambiguous outcome on non-idempotent operation MUST NOT fallback
        if (route.retrySafety == RetrySafety.NOT_IDEMPOTENT) {
            return Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Transport(
                        message = "Command transmitted over ${route.preferred} but confirmation timed out. Automatic fallback prevented to avoid duplicate physical action.",
                        cause = primaryError,
                    )
                )
            )
        }

        // IDEMPOTENT fallback check
        val fallbackType = route.fallback
        if (fallbackType != null && route.retrySafety == RetrySafety.IDEMPOTENT) {
            val fallbackResult = executeTransport(fallbackType, operation)
            if (fallbackResult.isSuccess) {
                return fallbackResult
            }
        }

        return primaryResult
    }

    private suspend fun executeTransport(
        transportType: HeyCyanTransportType,
        operation: HeyCyanOperation,
    ): Result<Unit> = when (transportType) {
        HeyCyanTransportType.BLE_GATT -> {
            bleExecutor?.execute(operation)
                ?: Result.failure(
                    HeyCyanCapabilityException(
                        HeyCyanOperationFailure.NotConnected("BLE transport unavailable")
                    )
                )
        }
        HeyCyanTransportType.RFCOMM -> {
            rfcommExecutor?.execute(operation)
                ?: Result.failure(
                    HeyCyanCapabilityException(
                        HeyCyanOperationFailure.NotConnected("RFCOMM transport unavailable")
                    )
                )
        }
        HeyCyanTransportType.WIFI -> {
            Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Unsupported("Wi-Fi transport is for media sync only, not command routing")
                )
            )
        }
    }
}
