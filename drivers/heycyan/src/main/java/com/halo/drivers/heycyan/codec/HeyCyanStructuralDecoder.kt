package com.halo.drivers.heycyan.codec

import java.util.UUID

/**
 * Structural decoder for observed HeyCyan packet families.
 *
 * This layer performs pattern-based structural parsing without enforcing
 * semantic interpretation. It preserves all bytes and labels fields
 * using neutral terminology consistent with the behavioral corpus.
 */
class HeyCyanStructuralDecoder {

    fun decode(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        val bytes = frame.bytes
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != 0xBC) {
            return DecodedHeyCyanFrame(
                family = HeyCyanPacketFamily.UNKNOWN,
                raw = frame,
                fields = emptyMap()
            )
        }

        val familyByte = bytes[1].toInt() and 0xFF
        val subtypeByte = bytes[2].toInt() and 0xFF

        return when {
            familyByte == 0x73 && subtypeByte == 0x0E -> decodeBC73_0E(frame)
            familyByte == 0x73 && subtypeByte == 0x08 -> decodeBC73_08(frame)
            familyByte == 0x73 && subtypeByte == 0x05 -> decodeBC73_05(frame)
            familyByte == 0x73 && subtypeByte == 0x02 -> decodeBC73_02(frame)
            familyByte == 0x59 && subtypeByte == 0x28 -> decodeBC59_28(frame)
            else -> DecodedHeyCyanFrame(
                family = HeyCyanPacketFamily.UNKNOWN,
                raw = frame,
                fields = emptyMap()
            )
        }
    }

    private fun decodeBC73_0E(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        val bytes = frame.bytes
        val fields = mutableMapOf<String, Int>()
        
        // Template: BC 73 0E 00 ?? ?? 12 01 00 10 XX 02 00 0F 0F 03 00 10 0A YY
        if (bytes.size >= 20) {
            // unknown04/unknown05 — candidate checksum/sequence field; requires offline analysis against corpus
            fields["unknown04"] = bytes[4].toInt() and 0xFF
            fields["unknown05"] = bytes[5].toInt() and 0xFF
            fields["candidateAction"] = bytes[10].toInt() and 0xFF
            fields["unknown19"] = bytes[19].toInt() and 0xFF
        }

        return DecodedHeyCyanFrame(
            family = HeyCyanPacketFamily.BC73_0E,
            raw = frame,
            fields = fields
        )
    }

    private fun decodeBC73_08(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        val bytes = frame.bytes
        val fields = mutableMapOf<String, Int>()

        // Observed in camera and AI interactions. Length varies but prefix is stable.
        if (bytes.size >= 4) {
             // Exact field mapping for BC73_08 still requires correlation.
             // Preserving raw bytes is the priority.
        }

        return DecodedHeyCyanFrame(
            family = HeyCyanPacketFamily.BC73_08,
            raw = frame,
            fields = fields
        )
    }

    private fun decodeBC73_05(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        return DecodedHeyCyanFrame(
            family = HeyCyanPacketFamily.BC73_05,
            raw = frame,
            fields = emptyMap()
        )
    }

    private fun decodeBC73_02(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        return DecodedHeyCyanFrame(
            family = HeyCyanPacketFamily.BC73_02,
            raw = frame,
            fields = emptyMap()
        )
    }

    private fun decodeBC59_28(frame: RawHeyCyanFrame): DecodedHeyCyanFrame {
        return DecodedHeyCyanFrame(
            family = HeyCyanPacketFamily.BC59_28,
            raw = frame,
            fields = emptyMap()
        )
    }
}

data class DecodedHeyCyanFrame(
    val family: HeyCyanPacketFamily,
    val raw: RawHeyCyanFrame,
    val fields: Map<String, Int>
)

enum class HeyCyanPacketFamily {
    BC73_0E,
    BC73_08,
    BC73_05,
    BC73_02,
    BC59_28,
    UNKNOWN
}
