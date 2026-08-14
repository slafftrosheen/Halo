package com.halo.drivers.heycyan.codec

/**
 * Verified wire frame codec for the HeyCyan BLE protocol.
 *
 * Frame Format (6-byte header + N-byte payload):
 * +--------+--------+-------------+-------------+----------------+
 * | 0xBC   | Action | Length LE   | CRC16 LE    | Payload        |
 * | 1 byte | 1 byte | 2 bytes     | 2 bytes     | N bytes        |
 * +--------+--------+-------------+-------------+----------------+
 *
 * CRC16 Algorithm (com.oudmon.ble.base.communication.utils.CRC16):
 * - Polynomial: 0xA001
 * - Initial value: 0xFFFF
 * - Serialization: Little-Endian
 */
object HeyCyanWireFrameCodec {

    const val FRAME_HEADER_BYTE: Byte = 0xBC.toByte()
    const val HEADER_SIZE = 6

    // Action Constants
    const val ACTION_SYNC_TIME: Byte = 0x40.toByte()
    const val ACTION_GLASSES_CONTROL: Byte = 0x41.toByte()
    const val ACTION_GLASSES_BATTERY: Byte = 0x42.toByte()
    const val ACTION_DEVICE_INFO: Byte = 0x43.toByte()
    const val ACTION_DEVICE_AI_VOICE: Byte = 0x44.toByte()
    const val ACTION_DEVICE_HEART_BEAT: Byte = 0x45.toByte()
    const val ACTION_DEVICE_WEAR: Byte = 0x46.toByte()
    const val ACTION_DEVICE_WEAR_SUPPORT: Byte = 0x47.toByte()
    const val ACTION_BT_MAC_PROTOCOL: Byte = 0x48.toByte()
    const val ACTION_OTA_SOC: Byte = 0xFC.toByte()
    const val ACTION_GPT_UPLOAD: Byte = 0x59.toByte()
    const val ACTION_PICTURE_THUMBNAILS: Byte = 0xFD.toByte()
    const val ACTION_VOICE_STATUS: Byte = 0x4C.toByte()
    const val ACTION_BT_CONNECT: Byte = 0x4D.toByte()
    const val ACTION_VOLUME_CONTROL: Byte = 0x51.toByte()
    const val ACTION_SPEAK_SOUND_SWITCH: Byte = 0x52.toByte()
    const val ACTION_DEVICE_DATA_REPORTING: Byte = 0x73.toByte()

    // Verified Golden Vector Payloads & Frames
    val PAYLOAD_PHOTO = byteArrayOf(0x02, 0x01, 0x01)
    val PAYLOAD_VIDEO_START = byteArrayOf(0x02, 0x01, 0x02)
    val PAYLOAD_VIDEO_STOP = byteArrayOf(0x02, 0x01, 0x03)
    val PAYLOAD_WIFI_TRANSFER = byteArrayOf(0x02, 0x01, 0x04, 0x01)
    val PAYLOAD_VOICE_ROUTING = byteArrayOf(0x02, 0x01, 0x0B)
    val PAYLOAD_MEDIA_COUNT = byteArrayOf(0x02, 0x04)
    val PAYLOAD_RECORD_START = byteArrayOf(0x02, 0x01, 0x08)
    val PAYLOAD_RECORD_STOP = byteArrayOf(0x02, 0x01, 0x0C)
    val PAYLOAD_AI_THUMBNAIL = byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02, 0x02)

    val GOLDEN_PHOTO = byteArrayOf(0xBC.toByte(), 0x41, 0x03, 0x00, 0x10, 0x50.toByte(), 0x02, 0x01, 0x01)
    val GOLDEN_VIDEO_START = byteArrayOf(0xBC.toByte(), 0x41, 0x03, 0x00, 0x50, 0x51, 0x02, 0x01, 0x02)
    val GOLDEN_VIDEO_STOP = byteArrayOf(0xBC.toByte(), 0x41, 0x03, 0x00, 0x91.toByte(), 0x91.toByte(), 0x02, 0x01, 0x03)
    val GOLDEN_WIFI_TRANSFER = byteArrayOf(0xBC.toByte(), 0x41, 0x04, 0x00, 0x93.toByte(), 0x5C.toByte(), 0x02, 0x01, 0x04, 0x01)
    val GOLDEN_VOICE_ROUTING = byteArrayOf(0xBC.toByte(), 0x41, 0x03, 0x00, 0x90.toByte(), 0x57.toByte(), 0x02, 0x01, 0x0B)
    val GOLDEN_MEDIA_COUNT = byteArrayOf(0xBC.toByte(), 0x41, 0x02, 0x00, 0x01, 0x13, 0x02, 0x04)

    /**
     * Calculates vendor CRC16 for the given payload.
     * Uses polynomial 0xA001 with initial value 0xFFFF.
     */
    fun calculateCrc16(payload: ByteArray): Int {
        var crc = 0xFFFF
        val polynomial = 0xA001

        for (byte in payload) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (i in 0 until 8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor polynomial
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Encodes an action byte and payload into a verified HeyCyan wire frame.
     */
    fun encode(action: Byte, payload: ByteArray): ByteArray {
        val length = payload.size
        val crc = calculateCrc16(payload)
        val frame = ByteArray(HEADER_SIZE + length)

        frame[0] = FRAME_HEADER_BYTE
        frame[1] = action
        frame[2] = (length and 0xFF).toByte()
        frame[3] = ((length ushr 8) and 0xFF).toByte()
        frame[4] = (crc and 0xFF).toByte()
        frame[5] = ((crc ushr 8) and 0xFF).toByte()

        System.arraycopy(payload, 0, frame, HEADER_SIZE, length)
        return frame
    }

    /**
     * Decodes a raw byte array into a [WireFrameDecodeResult] result.
     */
    fun decode(bytes: ByteArray): WireFrameDecodeResult {
        if (bytes.size < HEADER_SIZE) {
            return WireFrameDecodeResult.Invalid("Frame smaller than header size (${bytes.size} < $HEADER_SIZE)")
        }
        if (bytes[0] != FRAME_HEADER_BYTE) {
            return WireFrameDecodeResult.Invalid("Invalid start byte: 0x${"%02X".format(bytes[0])}, expected 0xBC")
        }

        val action = bytes[1]
        val payloadLength = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        val crc16 = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)

        if (bytes.size < HEADER_SIZE + payloadLength) {
            return WireFrameDecodeResult.Incomplete(
                expectedSize = HEADER_SIZE + payloadLength,
                actualSize = bytes.size
            )
        }

        val payload = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLength)
        val expectedCrc = calculateCrc16(payload)
        val isValidCrc = (crc16 == expectedCrc)

        return WireFrameDecodeResult.Success(
            action = action,
            payloadLength = payloadLength,
            crc16 = crc16,
            payload = payload,
            isValidCrc = isValidCrc
        )
    }

    /**
     * Validates whether a frame byte array has a matching valid CRC16.
     */
    fun validateCrc(bytes: ByteArray): Boolean {
        return when (val result = decode(bytes)) {
            is WireFrameDecodeResult.Success -> result.isValidCrc
            else -> false
        }
    }
}

sealed interface WireFrameDecodeResult {
    data class Success(
        val action: Byte,
        val payloadLength: Int,
        val crc16: Int,
        val payload: ByteArray,
        val isValidCrc: Boolean
    ) : WireFrameDecodeResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            if (action != other.action) return false
            if (payloadLength != other.payloadLength) return false
            if (crc16 != other.crc16) return false
            if (!payload.contentEquals(other.payload)) return false
            if (isValidCrc != other.isValidCrc) return false
            return true
        }

        override fun hashCode(): Int {
            var result = action.toInt()
            result = 31 * result + payloadLength
            result = 31 * result + crc16
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + isValidCrc.hashCode()
            return result
        }
    }

    data class Incomplete(val expectedSize: Int, val actualSize: Int) : WireFrameDecodeResult
    data class Invalid(val reason: String) : WireFrameDecodeResult
}
