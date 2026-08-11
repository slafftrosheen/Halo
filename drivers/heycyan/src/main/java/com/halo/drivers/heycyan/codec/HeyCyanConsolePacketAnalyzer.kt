package com.halo.drivers.heycyan.codec

import com.halo.sdk.runtime.RuntimeLogger
import java.util.UUID

class HeyCyanConsolePacketAnalyzer(
    private val logger: RuntimeLogger
) {

    private val counters = mutableMapOf<UUID, Int>()

    fun onRx(
        characteristicUuid: UUID,
        payload: ByteArray
    ) {
        val count = (counters[characteristicUuid] ?: 0) + 1
        counters[characteristicUuid] = count

        val hex = payload.joinToString(" ") {
            "%02X".format(it)
        }

        logger.info(
            "HeyCyanRX",
            "#$count [$characteristicUuid] ${payload.size}B: $hex"
        )
    }
}
