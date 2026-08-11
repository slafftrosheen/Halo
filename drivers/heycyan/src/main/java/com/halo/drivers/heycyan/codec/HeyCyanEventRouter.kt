package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.util.UUID

/**
 * Characteristic-aware event router for HeyCyan devices.
 *
 * This router receives both characteristic UUID and payload, allowing it to
 * distinguish between different protocol families on different characteristics.
 */
class HeyCyanEventRouter(
    private val sdkDecoder: HeyCyanSdkEventDecoder
) {

    fun decode(
        characteristicUuid: UUID,
        payload: ByteArray
    ): HeyCyanEvent {

        /*
         * The de5bf729 stream has now been observed physically and
         * correlated with button interaction.
         *
         * Do NOT pretend it follows the SDK's [eventType][payload]
         * structure until the actual SDK decoder for this channel
         * has been traced.
         */
        if (characteristicUuid == DE5BF729) {
            return decodeDe5bf729(characteristicUuid, payload)
        }

        return sdkDecoder.decode(payload)
            ?: HeyCyanEvent.Unknown(
                characteristicUuid = characteristicUuid.toString(),
                payload = payload.copyOf()
            )
    }

    private fun decodeDe5bf729(
        characteristicUuid: UUID,
        payload: ByteArray
    ): HeyCyanEvent {

        /*
         * Physical observation:
         *
         * BC 59 28 00 ...
         *
         * is emitted on this characteristic during button interaction.
         *
         * We intentionally preserve the complete packet until the SDK
         * implementation is traced for this protocol family.
         */
        return HeyCyanEvent.Unknown(
            characteristicUuid = characteristicUuid.toString(),
            payload = payload.copyOf()
        )
    }

    companion object {
        private val DE5BF729 =
            UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
    }
}