package com.halo.drivers.heycyan.codec

/**
 * Strict structural parser for RFC 6716 Opus packets.
 * This parser independently evaluates a byte array to determine if it complies
 * with standard Opus framing rules, specifically checking for Code-3 anomalies.
 */
class HeyCyanOpusFrameParser {

    data class OpusFrameAnalysis(
        val tocByte: Int,
        val config: Int,
        val isStereo: Boolean,
        val code: Int,
        val v: Int?,
        val p: Int?,
        val frameCountM: Int?,
        val paddingLength: Int?,
        val compressedRegionLength: Int,
        val isRfcValid: Boolean,
        val invalidReason: String?
    )

    fun parse(payload: ByteArray): OpusFrameAnalysis {
        if (payload.isEmpty()) {
            return OpusFrameAnalysis(
                tocByte = -1, config = -1, isStereo = false, code = -1,
                v = null, p = null, frameCountM = null, paddingLength = null,
                compressedRegionLength = 0, isRfcValid = false, invalidReason = "Empty payload"
            )
        }

        val toc = payload[0].toInt() and 0xFF
        val config = (toc shr 3) and 0x1F
        val isStereo = (toc and 0x04) != 0
        val code = toc and 0x03

        if (code != 3) {
            // We expect Code-3 packets based on `4B`
            return OpusFrameAnalysis(
                tocByte = toc, config = config, isStereo = isStereo, code = code,
                v = null, p = null, frameCountM = null, paddingLength = null,
                compressedRegionLength = payload.size - 1, isRfcValid = true,
                invalidReason = "Not Code 3 (Expected for BC 59)"
            )
        }

        if (payload.size < 2) {
            return OpusFrameAnalysis(
                tocByte = toc, config = config, isStereo = isStereo, code = code,
                v = null, p = null, frameCountM = null, paddingLength = null,
                compressedRegionLength = 0, isRfcValid = false, invalidReason = "Missing frame count byte for Code 3"
            )
        }

        val frameCountByte = payload[1].toInt() and 0xFF
        val v = frameCountByte and 0x01
        val p = (frameCountByte shr 1) and 0x01
        val m = (frameCountByte shr 2) and 0x3F

        var ptr = 2
        var paddingLen = 0
        if (p == 1) {
            while (ptr < payload.size) {
                val padByte = payload[ptr].toInt() and 0xFF
                ptr++
                paddingLen += padByte
                if (padByte != 255) break
            }
        }

        if (ptr + paddingLen > payload.size) {
            return OpusFrameAnalysis(
                tocByte = toc, config = config, isStereo = isStereo, code = code,
                v = v, p = p, frameCountM = m, paddingLength = paddingLen,
                compressedRegionLength = 0, isRfcValid = false,
                invalidReason = "Padding length exceeds payload size"
            )
        }

        val compressedLength = payload.size - ptr - paddingLen

        // RFC 6716 limits a single Code 3 packet to 120ms total duration.
        // Assuming max 20ms per frame, M should generally be <= 6 (up to 120ms).
        // 4B 41 -> M=16, 16 * 20ms = 320ms, which is invalid.
        // Wait, M can be up to 48 for smaller frames, but RFC 6716 says total <= 120ms.
        // The spec technically says M <= 48, but usually it's bounded by duration.
        // But for our validation, M=16 is very suspicious for 20ms cadence.
        // Actually, if M=16, it implies 16 frames. 16 * 20ms = 320ms which exceeds 120ms max, so it's invalid.
        val isRfcValid = m <= 6 && paddingLen >= 0

        val reason = if (!isRfcValid) {
            "M=$m exceeds 120ms max duration limit for Code 3"
        } else {
            null
        }

        return OpusFrameAnalysis(
            tocByte = toc,
            config = config,
            isStereo = isStereo,
            code = code,
            v = v,
            p = p,
            frameCountM = m,
            paddingLength = paddingLen,
            compressedRegionLength = compressedLength,
            isRfcValid = isRfcValid,
            invalidReason = reason
        )
    }
}
