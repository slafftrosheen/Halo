package com.halo.drivers.heycyan.codec

import com.halo.sdk.runtime.RuntimeLogger
import java.util.UUID

/**
 * Enhanced console analyzer for HeyCyan protocol discovery.
 *
 * Logs frames with structural decoding info to support real-time correlation
 * between physical actions and BLE traffic.
 */
class HeyCyanConsolePacketAnalyzer(
    private val logger: RuntimeLogger,
    private val structuralDecoder: HeyCyanStructuralDecoder = HeyCyanStructuralDecoder()
) {

    private val counters = mutableMapOf<UUID, Int>()

    fun onRx(
        characteristicUuid: UUID,
        payload: ByteArray
    ) {
        val count = (counters[characteristicUuid] ?: 0) + 1
        counters[characteristicUuid] = count

        val rawFrame = RawHeyCyanFrame(
            timestampMillis = System.currentTimeMillis(),
            direction = PacketDirection.RX,
            characteristicUuid = characteristicUuid,
            bytes = payload.copyOf()
        )

        val decoded = structuralDecoder.decode(rawFrame)
        
        val hex = payload.joinToString("") { "%02X".format(it) }
        
        if (decoded.family != HeyCyanPacketFamily.UNKNOWN) {
            val fieldsStr = decoded.fields.entries.joinToString(", ") { "${it.key}=0x${"%02X".format(it.value)}" }
            logger.info(
                "HeyCyanRX",
                "Frame #$count [${decoded.family}] len=${payload.size} raw=$hex fields={$fieldsStr} confidence=OBSERVED"
            )
        } else {
            logger.info(
                "HeyCyanRX",
                "Unknown #$count [$characteristicUuid] len=${payload.size} raw=$hex"
            )
        }
    }
}
