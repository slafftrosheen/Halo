package com.halo.sdk.runtime.wearables.media

/** Conservative file signatures for the library. Unknown/raw recordings are kept
 * as original binary files instead of claiming a playable audio container. */
object DeviceMediaFormat {
    fun mime(header: ByteArray): String {
        fun ascii(offset: Int, text: String) = header.size >= offset + text.length &&
            text.indices.all { header[offset + it] == text[it].code.toByte() }
        if (header.size >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte()) return "image/jpeg"
        if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))) return "image/png"
        if (ascii(0, "RIFF") && ascii(8, "WAVE")) return "audio/wav"
        if (ascii(0, "OggS") && header.toString(Charsets.ISO_8859_1).contains("OpusHead")) return "audio/ogg"
        if (ascii(4, "ftyp")) {
            val brand = header.copyOfRange(8, minOf(header.size, 12)).toString(Charsets.US_ASCII)
            if (brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")) return "image/heif"
            if (brand == "qt  ") return "video/quicktime"
            if (brand in setOf("isom", "iso2", "mp41", "mp42", "avc1", "M4V ")) return "video/mp4"
        }
        return "application/octet-stream"
    }
}
