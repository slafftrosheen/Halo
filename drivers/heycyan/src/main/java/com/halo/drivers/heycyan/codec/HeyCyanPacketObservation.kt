package com.halo.drivers.heycyan.codec

import java.util.UUID

/**
 * Observation of a single BLE packet from the HeyCyan glasses.
 *
 * This data class preserves raw packet evidence for forensic analysis
 * and correlation with physical actions.
 */
data class HeyCyanPacketObservation(
    val characteristicUuid: UUID,
    val payload: ByteArray,
    val timestampMillis: Long = System.currentTimeMillis()
) {

    val length: Int
        get() = payload.size

    val firstByte: Int?
        get() = payload.firstOrNull()?.toInt()?.and(0xFF)

    val prefix: ByteArray
        get() = payload.take(4).toByteArray()

    fun hex(): String =
        payload.joinToString(" ") { "%02X".format(it) }
}