package com.halo.drivers.heycyan.codec

import java.time.Instant

/**
 * Model to group a high-level operation with its associated TX and RX frames.
 *
 * This supports forensic correlation between user actions (e.g. PHOTO)
 * and the resulting BLE wire traffic.
 */
data class HeyCyanTransaction(
    val operation: String,
    val txFrames: List<RawHeyCyanFrame>,
    val rxFrames: List<RawHeyCyanFrame>,
    val startedAtMillis: Long,
    val endedAtMillis: Long
) {
    fun dump(): String = buildString {
        appendLine("==================================================")
        appendLine("HEYCYAN TRANSACTION")
        appendLine("operation: $operation")
        appendLine("start: ${Instant.ofEpochMilli(startedAtMillis)}")
        appendLine("end:   ${Instant.ofEpochMilli(endedAtMillis)}")
        appendLine()
        
        appendLine("TX:")
        txFrames.forEachIndexed { i, frame ->
            appendLine("  %02d %d %s %s".format(i, frame.timestampMillis, frame.characteristicUuid, frame.hex()))
        }
        appendLine()
        
        appendLine("RX:")
        rxFrames.forEachIndexed { i, frame ->
            appendLine("  %02d %d %s %s".format(i, frame.timestampMillis, frame.characteristicUuid, frame.hex()))
        }
        appendLine("==================================================")
    }
}
