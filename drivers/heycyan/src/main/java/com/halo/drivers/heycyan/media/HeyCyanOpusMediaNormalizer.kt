package com.halo.drivers.heycyan.media

import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * Normalizes audio files (.opus) transferred from HeyCyan glasses into standard,
 * playable Ogg/Opus containers.
 *
 * Background:
 * HeyCyan glasses do not write full Ogg containers onto their local HTTP storage.
 * Instead, they dump raw Opus packets (typically fixed 40-byte slices, or length-prefixed frames)
 * directly into `.opus` files.
 *
 * Normalization behavior:
 * 1. If bytes already begin with the Ogg magic header `OggS`, returned unchanged.
 * 2. Attempts parsing as length-prefixed frames (u16 LE/BE or u8).
 * 3. Fallback: uses the vendor-confirmed fixed 40-byte packet format only.
 * 4. Assembles standard RFC 7845 Ogg/Opus pages:
 *    - Page 0: OpusHead identification (1 channel, 48 kHz, pre-skip 0)
 *    - Page 1: OpusTags comment header (Vendor: "Halo")
 *    - Page 2+: Audio data pages with standard lacing, granule progression, and Ogg CRC32 checksums.
 * 5. If unparseable, returns original raw bytes gracefully.
 */
object HeyCyanOpusMediaNormalizer {

    sealed interface NormalizationResult {
        val bytes: ByteArray
        data class AlreadyOgg(override val bytes: ByteArray) : NormalizationResult
        data class Wrapped(override val bytes: ByteArray, val packetFormat: String) : NormalizationResult
        data class Unsupported(override val bytes: ByteArray, val reason: String) : NormalizationResult
    }

    private val OGG_MAGIC = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())

    private val oggCrcTable: IntArray = run {
        val table = IntArray(256)
        for (i in 0 until 256) {
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and 0x80000000.toInt()) != 0) {
                    (r shl 1) xor 0x04C11DB7
                } else {
                    r shl 1
                }
            }
            table[i] = r
        }
        table
    }

    fun isAlreadyOgg(raw: ByteArray): Boolean {
        if (raw.size < 4) return false
        return raw[0] == OGG_MAGIC[0] &&
            raw[1] == OGG_MAGIC[1] &&
            raw[2] == OGG_MAGIC[2] &&
            raw[3] == OGG_MAGIC[3]
    }

    fun normalize(raw: ByteArray, packetDurationMs: Int = 40): ByteArray {
        return normalizeDetailed(raw, packetDurationMs).bytes
    }

    fun normalizeDetailed(raw: ByteArray, packetDurationMs: Int = 40): NormalizationResult {
        if (raw.isEmpty()) return NormalizationResult.Unsupported(raw, "Empty recording")
        if (isAlreadyOgg(raw)) return NormalizationResult.AlreadyOgg(raw)

        val parsed = parseLengthPrefixedPackets(raw, littleEndian = true)?.let { it to "u16-le" }
            ?: parseLengthPrefixedPackets(raw, littleEndian = false)?.let { it to "u16-be" }
            ?: parseLengthPrefixedPackets1B(raw)?.let { it to "u8" }
            ?: parseFixed40BytePackets(raw)?.let { it to "fixed-40" }

        if (parsed == null || parsed.first.isEmpty()) {
            return NormalizationResult.Unsupported(raw, "Packet boundaries could not be established")
        }

        return try {
            NormalizationResult.Wrapped(
                bytes = buildOggOpusFromPackets(parsed.first, packetDurationMs),
                packetFormat = parsed.second,
            )
        } catch (error: Throwable) {
            NormalizationResult.Unsupported(raw, error.message ?: "Ogg/Opus wrapping failed")
        }
    }

    private fun parseLengthPrefixedPackets(raw: ByteArray, littleEndian: Boolean): List<ByteArray>? {
        var i = 0
        val out = ArrayList<ByteArray>()
        while (i + 2 <= raw.size) {
            val b0 = raw[i].toInt() and 0xFF
            val b1 = raw[i + 1].toInt() and 0xFF
            val len = if (littleEndian) (b0 or (b1 shl 8)) else ((b0 shl 8) or b1)
            i += 2
            if (len <= 0 || len > 2000) return null
            if (i + len > raw.size) return null
            out.add(raw.copyOfRange(i, i + len))
            i += len
        }
        if (i != raw.size) return null
        return if (out.size >= 2) out else null
    }

    private fun parseLengthPrefixedPackets1B(raw: ByteArray): List<ByteArray>? {
        var i = 0
        val out = ArrayList<ByteArray>()
        while (i + 1 <= raw.size) {
            val len = raw[i].toInt() and 0xFF
            i += 1
            if (len <= 0 || len > 255) return null
            if (i + len > raw.size) return null
            out.add(raw.copyOfRange(i, i + len))
            i += len
        }
        if (i != raw.size) return null
        return if (out.size >= 2) out else null
    }

    private fun parseFixed40BytePackets(raw: ByteArray): List<ByteArray>? {
        val packetSize = 40
        if (raw.size < packetSize || raw.size % packetSize != 0) return null
        val out = ArrayList<ByteArray>(raw.size / packetSize)
        var i = 0
        while (i < raw.size) {
            out.add(raw.copyOfRange(i, i + packetSize))
            i += packetSize
        }
        return out
    }

    private fun buildOggOpusFromPackets(packets: List<ByteArray>, packetDurationMs: Int): ByteArray {
        val serial = SecureRandom().nextInt()
        var seq = 0
        var granulePos: Long = 0

        val out = ByteArrayOutputStream()

        val opusHead = buildOpusHead(channels = 1, preSkip = 0)
        val opusTags = buildOpusTags(vendor = "Halo")

        // Header page 0: BOS (Beginning of Stream)
        writeOggPage(out, serial, seq++, granulePosition = 0, headerType = 0x02, packets = listOf(opusHead))
        // Header page 1: Tags
        writeOggPage(out, serial, seq++, granulePosition = 0, headerType = 0x00, packets = listOf(opusTags))

        // Audio pages
        val samplesPerPacket48k = (packetDurationMs * 48_000L) / 1000L
        val maxSegments = 255
        var idx = 0
        while (idx < packets.size) {
            val pagePackets = ArrayList<ByteArray>()
            var segCount = 0
            var localGranule = granulePos

            while (idx < packets.size) {
                val p = packets[idx]
                var neededSeg = (p.size + 254) / 255
                if (p.size % 255 == 0) neededSeg += 1
                if (segCount + neededSeg > maxSegments) break
                pagePackets.add(p)
                segCount += neededSeg
                localGranule += samplesPerPacket48k
                idx++
            }

            granulePos = localGranule
            val isLast = idx >= packets.size
            val headerType = if (isLast) 0x04 else 0x00 // 0x04 = EOS
            writeOggPage(out, serial, seq++, granulePosition = granulePos, headerType = headerType, packets = pagePackets)
        }

        return out.toByteArray()
    }

    private fun buildOpusHead(channels: Int, preSkip: Int): ByteArray {
        val b = ByteArrayOutputStream()
        b.write("OpusHead".toByteArray(Charsets.US_ASCII))
        b.write(1) // version
        b.write(channels and 0xFF)
        // pre-skip LE16
        b.write(preSkip and 0xFF)
        b.write((preSkip shr 8) and 0xFF)
        // input sample rate LE32 (48k)
        val sr = 48_000
        writeLe32(b, sr)
        // output gain LE16
        b.write(0)
        b.write(0)
        // channel mapping family (0 = mono/stereo)
        b.write(0)
        return b.toByteArray()
    }

    private fun buildOpusTags(vendor: String): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        val b = ByteArrayOutputStream()
        b.write("OpusTags".toByteArray(Charsets.US_ASCII))
        writeLe32(b, vendorBytes.size)
        b.write(vendorBytes)
        // user comment list length = 0
        writeLe32(b, 0)
        return b.toByteArray()
    }

    private fun writeOggPage(
        out: ByteArrayOutputStream,
        serial: Int,
        seq: Int,
        granulePosition: Long,
        headerType: Int,
        packets: List<ByteArray>,
    ) {
        val segmentTable = ByteArrayOutputStream()
        val payload = ByteArrayOutputStream()

        for (p in packets) {
            var remaining = p.size
            var offset = 0
            while (remaining > 0) {
                val seg = minOf(255, remaining)
                segmentTable.write(seg)
                payload.write(p, offset, seg)
                offset += seg
                remaining -= seg
            }
            if (p.size % 255 == 0) {
                segmentTable.write(0)
            }
        }

        val segBytes = segmentTable.toByteArray()
        check(segBytes.size <= 255) { "Ogg page has too many segments: ${segBytes.size}" }
        val payloadBytes = payload.toByteArray()

        val header = ByteArrayOutputStream()
        header.write(OGG_MAGIC)
        header.write(0) // version
        header.write(headerType and 0xFF)
        writeLe64(header, granulePosition)
        writeLe32(header, serial)
        writeLe32(header, seq)
        // checksum placeholder at offset 22
        writeLe32(header, 0)
        header.write(segBytes.size)
        header.write(segBytes)

        val pageBytes = header.toByteArray() + payloadBytes
        val crc = oggCrc(pageBytes)

        // Patch checksum at byte offset 22 (from start of OggS)
        pageBytes[22] = (crc and 0xFF).toByte()
        pageBytes[23] = ((crc shr 8) and 0xFF).toByte()
        pageBytes[24] = ((crc shr 16) and 0xFF).toByte()
        pageBytes[25] = ((crc shr 24) and 0xFF).toByte()

        out.write(pageBytes)
    }

    private fun writeLe32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeLe64(out: ByteArrayOutputStream, v: Long) {
        out.write((v and 0xFF).toInt())
        out.write(((v shr 8) and 0xFF).toInt())
        out.write(((v shr 16) and 0xFF).toInt())
        out.write(((v shr 24) and 0xFF).toInt())
        out.write(((v shr 32) and 0xFF).toInt())
        out.write(((v shr 40) and 0xFF).toInt())
        out.write(((v shr 48) and 0xFF).toInt())
        out.write(((v shr 56) and 0xFF).toInt())
    }

    private fun oggCrc(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            val idx = ((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF
            crc = (crc shl 8) xor oggCrcTable[idx]
        }
        return crc
    }
}
