package com.halo.drivers.heycyan.codec

/** Byte-safe hexadecimal conversion used by capture persistence and inspection. */
object HexCodec {
    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex payload must contain an even number of characters" }

        return ByteArray(hex.length / 2) { index ->
            val high = hexDigit(hex[index * 2])
            val low = hexDigit(hex[index * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    private fun hexDigit(value: Char): Int = when (value) {
        in '0'..'9' -> value - '0'
        in 'a'..'f' -> value - 'a' + 10
        in 'A'..'F' -> value - 'A' + 10
        else -> throw IllegalArgumentException("Invalid hexadecimal character: $value")
    }
}