package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.codec.HeyCyanCommandEncoder
import com.halo.drivers.heycyan.codec.HeyCyanPacketFramer
import com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport

/**
 * Executes a low-level [HeyCyanCommand] through the encode → frame → transport pipeline.
 *
 * This is the boundary between the semantic capability layer and the unresolved
 * BLE wire framing. The [HeyCyanPacketFramer] will return [Result.failure] with
 * [com.halo.drivers.heycyan.protocol.ProtocolNotVerifiedException] for any command
 * whose checksum or exact packet format has not been established from real-device
 * captures or authoritative vendor documentation.
 *
 * No guessed packets are ever transmitted.
 */
interface HeyCyanCommandExecutor {
    suspend fun execute(command: HeyCyanCommand): Result<Unit>
}

/**
 * Default implementation of [HeyCyanCommandExecutor].
 *
 * Pipeline:
 *   HeyCyanCommand
 *       ↓
 *   HeyCyanCommandEncoder   (command → EncodedCommand with commandId + payload)
 *       ↓
 *   HeyCyanPacketFramer     (EncodedCommand → BLE bytes; BLOCKED if checksum unresolved)
 *       ↓
 *   HeyCyanCommandTransport (bytes → BLE GATT write)
 *
 * IMPORTANT PROTOCOL CONSTRAINT:
 *   The [HeyCyanPacketFramer] will always return failure when no checksum implementation
 *   is provided, because the BLE packet framing is not yet established from evidence.
 *   This is intentional — zero guessed packets leave Halo.
 */
class DefaultHeyCyanCommandExecutor(
    private val transport: HeyCyanCommandTransport,
    private val encoder: HeyCyanCommandEncoder,
    private val framer: HeyCyanPacketFramer,
) : HeyCyanCommandExecutor {

    override suspend fun execute(command: HeyCyanCommand): Result<Unit> {
        HeyCyanProtocolCapture.instance.startOperation(command.toString())
        try {
            val encoded = encoder.encode(command)
                .getOrElse { return Result.failure(it) }

            val framed = framer.frame(encoded)
                .getOrElse { return Result.failure(it) }

            val result = transport.send(framed)
            return result
        } finally {
            HeyCyanProtocolCapture.instance.endOperation()
        }
    }
}
