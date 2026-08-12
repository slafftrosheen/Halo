package com.halo.drivers.heycyan.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A probe for characterizing the BC 59 candidate audio stream.
 * 
 * Instead of immediately attempting to decode the stream as standard Opus,
 * this probe collects statistical information about the payloads to scientifically
 * prove the nature of the framing (e.g. fixed transport padding around a 
 * variable-length compressed payload).
 */
data class AudioStreamPacket(
    val timestampMs: Long,
    val payload: ByteArray,
    val length: Int,
    val firstBytes: ByteArray,
    val trailingZeroCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioStreamPacket) return false
        if (timestampMs != other.timestampMs) return false
        if (!payload.contentEquals(other.payload)) return false
        if (length != other.length) return false
        if (!firstBytes.contentEquals(other.firstBytes)) return false
        if (trailingZeroCount != other.trailingZeroCount) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + length
        result = 31 * result + firstBytes.contentHashCode()
        result = 31 * result + trailingZeroCount
        return result
    }
}

interface AudioStreamProbe {
    fun onPacket(packet: AudioStreamPacket)
    fun dumpStatistics()
}

object HeyCyanAudioStreamProbe : AudioStreamProbe {
    private val _packetCountFlow = MutableStateFlow(0)
    val packetCountFlow = _packetCountFlow.asStateFlow()
    
    private val _statusFlow = MutableStateFlow("Inactive")
    val statusFlow = _statusFlow.asStateFlow()

    private var packetCount = 0
    private val payloadSizes = mutableMapOf<Int, Int>()
    private val byte0Frequencies = mutableMapOf<Byte, Int>()
    private val byte1Frequencies = mutableMapOf<Byte, Int>()
    private val byte2Frequencies = mutableMapOf<Byte, Int>()
    
    private var minTrailingZeroes = Int.MAX_VALUE
    private var maxTrailingZeroes = Int.MIN_VALUE
    private var sumTrailingZeroes = 0L

    private var previousTimestamp = 0L
    private var minInterval = Long.MAX_VALUE
    private var maxInterval = Long.MIN_VALUE
    private var sumInterval = 0L
    private var intervalCount = 0

    // Decoder validation probe
    private val decoderProbe = HeyCyanOpusDecoderProbe()

    override fun onPacket(packet: AudioStreamPacket) {
        packetCount++
        _packetCountFlow.value = packetCount
        _statusFlow.value = "Receiving Packets"
        
        payloadSizes[packet.length] = (payloadSizes[packet.length] ?: 0) + 1
        
        if (packet.firstBytes.isNotEmpty()) {
            val b0 = packet.firstBytes[0]
            byte0Frequencies[b0] = (byte0Frequencies[b0] ?: 0) + 1
        }
        if (packet.firstBytes.size > 1) {
            val b1 = packet.firstBytes[1]
            byte1Frequencies[b1] = (byte1Frequencies[b1] ?: 0) + 1
        }
        if (packet.firstBytes.size > 2) {
            val b2 = packet.firstBytes[2]
            byte2Frequencies[b2] = (byte2Frequencies[b2] ?: 0) + 1
        }

        val zeroes = packet.trailingZeroCount
        if (zeroes < minTrailingZeroes) minTrailingZeroes = zeroes
        if (zeroes > maxTrailingZeroes) maxTrailingZeroes = zeroes
        sumTrailingZeroes += zeroes

        if (previousTimestamp != 0L) {
            val interval = packet.timestampMs - previousTimestamp
            if (interval > 0) { // filter out identical timestamps if batched
                if (interval < minInterval) minInterval = interval
                if (interval > maxInterval) maxInterval = interval
                sumInterval += interval
                intervalCount++
            }
        }
        previousTimestamp = packet.timestampMs
        
        // Feed the raw payload directly to the Opus decoder validation probe
        // Multiply ms by 1000 to get microseconds for MediaCodec
        decoderProbe.decodePacket(packet.payload, packet.timestampMs * 1000)
        
        // Dump stats every 10 packets to get a live view
        if (packetCount % 10 == 0) {
            dumpStatistics()
        }
    }

    override fun dumpStatistics() {
        val meanZeroes = if (packetCount > 0) sumTrailingZeroes / packetCount else 0
        val meanInterval = if (intervalCount > 0) sumInterval / intervalCount else 0

        val stats = buildString {
            appendLine("=== BC 59 Audio Stream Statistics ===")
            appendLine("Packets:        $packetCount")
            
            append("Payload sizes:  ")
            payloadSizes.forEach { (size, count) -> 
                append("$size bytes ($count/$packetCount)  ")
            }
            appendLine()

            appendLine("byte[0]:")
            byte0Frequencies.forEach { (b, count) -> 
                appendLine("  %02X = $count".format(b)) 
            }
            
            appendLine("byte[1]:")
            byte1Frequencies.forEach { (b, count) -> 
                appendLine("  %02X = $count".format(b)) 
            }
            
            appendLine("byte[2]:")
            byte2Frequencies.entries.sortedByDescending { it.value }.take(10).forEach { (b, count) -> 
                appendLine("  %02X = $count".format(b)) 
            }

            appendLine("Trailing zeroes:")
            appendLine("  min = ${if (minTrailingZeroes == Int.MAX_VALUE) 0 else minTrailingZeroes}")
            appendLine("  max = ${if (maxTrailingZeroes == Int.MIN_VALUE) 0 else maxTrailingZeroes}")
            appendLine("  mean = $meanZeroes")

            appendLine("Interval (ms):")
            appendLine("  min = ${if (minInterval == Long.MAX_VALUE) 0 else minInterval}")
            appendLine("  max = ${if (maxInterval == Long.MIN_VALUE) 0 else maxInterval}")
            appendLine("  mean = $meanInterval")
            
            appendLine("Decoder Probe:")
            appendLine("  Successful frames decoded: ${decoderProbe.successfulDecodes}")
            appendLine("  Failed decoding attempts:  ${decoderProbe.failedDecodes}")
            appendLine("  Total PCM output (bytes):  ${decoderProbe.decodedByteCount}")
            appendLine("=====================================")
        }
        HeyCyanLogger.i("AudioProbe", stats)
    }
}
