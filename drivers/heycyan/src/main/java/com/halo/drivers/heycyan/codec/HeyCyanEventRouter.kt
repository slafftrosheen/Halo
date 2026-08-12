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
    private val sdkDecoder: HeyCyanSdkEventDecoder,
    private val structuralDecoder: HeyCyanStructuralDecoder = HeyCyanStructuralDecoder()
) {

    fun decode(
        characteristicUuid: UUID,
        payload: ByteArray,
        timestampMillis: Long = System.currentTimeMillis()
    ): HeyCyanEvent {

        val rawFrame = RawHeyCyanFrame(
            timestampMillis = timestampMillis,
            direction = PacketDirection.RX,
            characteristicUuid = characteristicUuid,
            bytes = payload.copyOf()
        )

        /*
         * The de5bf729 stream has now been observed physically and
         * correlated with button interaction.
         */
        if (characteristicUuid == DE5BF729) {
            val decoded = structuralDecoder.decode(rawFrame)
            if (decoded.actionFamily != HeyCyanActionFamily.UNKNOWN) {
                return HeyCyanEvent.DecodedProprietaryFrame(
                    actionFamily = decoded.actionFamily,
                    action = decoded.action,
                    length = decoded.payloadLength,
                    isValidCrc = decoded.isValidCrc,
                    rawHex = decoded.raw.hex(),
                    fields = decoded.fields
                )
            }
        }

        return sdkDecoder.decode(payload)
            ?: HeyCyanEvent.Unknown(
                characteristicUuid = characteristicUuid.toString(),
                payload = payload.copyOf()
            )
    }

    companion object {
        private val DE5BF729 =
            UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
    }
}
