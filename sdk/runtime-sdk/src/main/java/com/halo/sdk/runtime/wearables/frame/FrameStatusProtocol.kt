package com.halo.sdk.runtime.wearables.frame

/** Fixed read-only expressions from Brilliant's Frame Lua API. No caller-provided Lua. */
object FrameStatusProtocol {
    const val SERVICE = "7a230001-5475-a6a4-654c-8431f6ad49c4"
    const val TX = "7a230002-5475-a6a4-654c-8431f6ad49c4"
    const val RX = "7a230003-5475-a6a4-654c-8431f6ad49c4"
    enum class Query(val expression: String) {
        HARDWARE("frame.HARDWARE_VERSION"), FIRMWARE("frame.FIRMWARE_VERSION"), BATTERY("frame.battery_level()")
    }
    fun command(query: Query, token: String): ByteArray {
        require(token.matches(Regex("[a-f0-9]{32}")))
        return "print('~H$token:'..tostring(${query.expression})..':H~')".toByteArray(Charsets.US_ASCII)
    }
    fun firmware(value: String): String = value.also {
        require(it.matches(Regex("[A-Za-z0-9._+\\-]{1,64}"))) { "Invalid Frame firmware response" }
    }
    fun battery(value: String): Int {
        require(value.matches(Regex("[0-9]{1,3}"))) { "Invalid Frame battery response" }
        return value.toInt().also { require(it in 1..100) { "Frame battery outside documented range" } }
    }

    /** Print notifications may contain unrelated output, be split, or omit a newline.
     * The nonce belongs to a single request on a single link. Raw-data packets (0x01)
     * are not Lua replies. No error text from a user script becomes a status value. */
    class Reply(private val token: String) {
        private val prefix = "~H$token:"
        private var buffer = ""
        fun append(bytes: ByteArray): String? {
            require(bytes.size <= 512) { "Frame notification exceeds ATT bounds" }
            if (bytes.firstOrNull() == 1.toByte()) return null
            // Preserve byte boundaries while ignoring unrelated script output.
            buffer += bytes.toString(Charsets.ISO_8859_1)
            val start = buffer.indexOf(prefix)
            if (start < 0) {
                buffer = buffer.takeLast(prefix.length - 1)
                return null
            }
            buffer = buffer.substring(start)
            val end = buffer.indexOf(":H~", prefix.length)
            if (end >= 0) return buffer.substring(prefix.length, end).also {
                require(it.length in 1..64 && it.none(Char::isISOControl)) { "Invalid Frame status payload" }
            }
            require(buffer.length <= prefix.length + 64 + 2) { "Frame status reply too long" }
            return null
        }
    }
}
