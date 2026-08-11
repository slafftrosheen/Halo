package com.halo.drivers.heycyan.codec

import com.halo.sdk.runtime.RuntimeLogger
import java.util.UUID

/**
 * Controlled RX packet analyzer for listening to HeyCyan protocol traffic.
 *
 * This analyzer is specifically for what you were doing today: watching RX
 * while physically pressing the buttons. It logs packets through the
 * RuntimeLogger to avoid leaving println() as the production logging path.
 */
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

    fun dumpStatistics() {
        logger.info("HeyCyanRX", "RX characteristic statistics:")

        counters
            .entries
            .sortedByDescending { it.value }
            .forEach { (uuid, count) ->
                logger.info("HeyCyanRX", " $uuid -> $count packets")
            }
    }
}