package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.HeyCyanBleEvent
import com.halo.drivers.heycyan.HeyCyanEventDecoder
import java.util.UUID

/**
 * Wraps HeyCyanEventDecoder as a typed response decoder that can be used
 * by HeyCyanCommandDispatcher to await specific event types.
 *
 * The raw event decoder returns HeyCyanBleEvent for any notification.
 * This decoder adds the ability to filter by expected response type
 * and validate response payloads against command expectations.
 */
class HeyCyanResponseDecoder(
    private val eventDecoder: HeyCyanEventDecoder = HeyCyanEventDecoder()
) {

    /**
     * Decodes a raw BLE notification payload into a typed event.
     */
    fun decode(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        return eventDecoder.decode(payload, characteristic, timestamp)
    }

    /**
     * Checks whether a decoded event matches the expected response type
     * for a given command.
     */
    fun matchesExpectedResponse(
        event: HeyCyanBleEvent,
        expectedEventType: Int
    ): Boolean {
        return when (expectedEventType) {
            0x01 -> event is HeyCyanBleEvent.Battery
            0x02 -> event is HeyCyanBleEvent.MediaCounts
            0x03 -> event is HeyCyanBleEvent.Version
            0x04 -> event is HeyCyanBleEvent.ModeChanged
            0x05 -> event is HeyCyanBleEvent.WiFiCredentials
            0x06 -> event is HeyCyanBleEvent.AIImageChunk
            0xFF -> event is HeyCyanBleEvent.Error
            else -> false
        }
    }

    /**
     * Maps a command ID to the expected response event type.
     *
     * Returns -1 if no response is expected (fire-and-forget commands).
     */
    fun expectedResponseType(commandId: Byte): Int {
        return when (commandId.toInt() and 0xFF) {
            // Mode command → ModeChanged event
            0x01 -> 0x04
            // Query commands → specific response events
            0x10 -> 0x03 // GET_VERSION → Version
            0x11 -> -1   // GET_MAC → Unknown response format
            0x12 -> -1   // SET_DATETIME → No specific response
            0x13 -> 0x01 // GET_BATTERY → Battery
            0x14 -> 0x02 // GET_MEDIA → MediaCounts
            else -> -1
        }
    }
}
