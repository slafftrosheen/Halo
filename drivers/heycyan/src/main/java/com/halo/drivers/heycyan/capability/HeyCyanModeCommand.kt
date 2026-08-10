package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanMode

/**
 * Represents the semantic payload of a mode-set command.
 *
 * The mode byte value is source-backed from CyanBridge / HeyCyanSmartGlassesSDK:
 *
 *   PHOTO      → 0x01
 *   VIDEO      → 0x02
 *   VIDEO_STOP → 0x03
 *   AUDIO      → 0x04
 *   AUDIO_STOP → 0x05
 *   AI_PHOTO   → 0x06
 *   TRANSFER   → 0x07
 *
 * This is the known boundary:
 *
 *   SetMode(PHOTO) → command semantic = 0x01   ← KNOWN
 *   BLE wire framing / checksum                ← UNRESOLVED; behind HeyCyanPacketFramer
 *
 * Do NOT construct a full BLE packet from this class.
 */
data class HeyCyanModeCommand(
    val mode: HeyCyanMode,
) {
    /**
     * The single-byte mode payload whose value is source-derived.
     * This is NOT a complete BLE packet.
     */
    val payload: ByteArray
        get() = byteArrayOf(mode.value.toByte())
}
