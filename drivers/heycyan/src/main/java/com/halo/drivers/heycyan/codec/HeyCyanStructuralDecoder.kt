package com.halo.drivers.heycyan.codec

/**
 * Structural decoder for HeyCyan protocol frames.
 *
 * Architecture:
 * LAYER 1: Generic Wire Frame Parsing via [HeyCyanWireFrameCodec]
 *          Extracts action byte, payload length LE, CRC16 LE, payload, and CRC validity.
 *
 * LAYER 2: Action-Specific Classification via [HeyCyanActionFamily]
 *          Classifies frame action and preserves uninterpreted raw payload bytes.
 */
class HeyCyanStructuralDecoder {

    fun decode(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        val result = HeyCyanWireFrameCodec.decode(frame.bytes)
        if (result !is WireFrameDecodeResult.Success) {
            return DecodedHeyCyanFrame(
                actionFamily = HeyCyanActionFamily.UNKNOWN,
                action = 0x00,
                payloadLength = 0,
                crc16 = 0,
                isValidCrc = false,
                payload = byteArrayOf(),
                raw = frame,
                fields = emptyMap()
            )
        }

        val action = result.action
        val payload = result.payload
        val family = HeyCyanActionFamily.fromActionByte(action)

        val fields = mutableMapOf<String, Int>()
        fields["action"] = action.toInt() and 0xFF
        fields["payloadLength"] = result.payloadLength
        fields["crc16"] = result.crc16
        fields["isValidCrc"] = if (result.isValidCrc) 1 else 0

        // Extract raw payload byte offsets safely without enforcing unverified semantics
        if (payload.isNotEmpty()) {
            fields["payloadByte0"] = payload[0].toInt() and 0xFF
        }
        if (payload.size >= 5) {
            fields["payloadByte4"] = payload[4].toInt() and 0xFF
        }
        if (payload.size >= 14) {
            fields["payloadByte13"] = payload[13].toInt() and 0xFF
        }

        return DecodedHeyCyanFrame(
            actionFamily = family,
            action = action,
            payloadLength = result.payloadLength,
            crc16 = result.crc16,
            isValidCrc = result.isValidCrc,
            payload = payload,
            raw = frame,
            fields = fields
        )
    }
}

data class DecodedHeyCyanFrame(
    val actionFamily: HeyCyanActionFamily,
    val action: Byte,
    val payloadLength: Int,
    val crc16: Int,
    val isValidCrc: Boolean,
    val payload: ByteArray,
    val raw: RawHeyCyanFrame,
    val fields: Map<String, Int>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedHeyCyanFrame) return false
        if (actionFamily != other.actionFamily) return false
        if (action != other.action) return false
        if (payloadLength != other.payloadLength) return false
        if (crc16 != other.crc16) return false
        if (isValidCrc != other.isValidCrc) return false
        if (!payload.contentEquals(other.payload)) return false
        if (raw != other.raw) return false
        if (fields != other.fields) return false
        return true
    }

    override fun hashCode(): Int {
        var result = actionFamily.hashCode()
        result = 31 * result + action.toInt()
        result = 31 * result + payloadLength
        result = 31 * result + crc16
        result = 31 * result + isValidCrc.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + raw.hashCode()
        result = 31 * result + fields.hashCode()
        return result
    }
}

/**
 * Action classification for HeyCyan wire frames based on vendor action byte.
 */
enum class HeyCyanActionFamily(val actionByte: Byte?) {
    DEVICE_DATA_REPORTING(HeyCyanWireFrameCodec.ACTION_DEVICE_DATA_REPORTING),
    GLASSES_CONTROL(HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL),
    DEVICE_INFO(HeyCyanWireFrameCodec.ACTION_DEVICE_INFO),
    AUDIO_STREAM(HeyCyanWireFrameCodec.ACTION_GPT_UPLOAD),
    UNKNOWN(null);

    companion object {
        fun fromActionByte(action: Byte): HeyCyanActionFamily =
            entries.firstOrNull { it.actionByte == action } ?: UNKNOWN
    }
}
