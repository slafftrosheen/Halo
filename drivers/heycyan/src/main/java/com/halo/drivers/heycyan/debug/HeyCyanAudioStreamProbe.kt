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
    fun onVoiceActivationStarted()
    fun onVoiceActivationEnded()
    fun dumpStatistics()
}

object HeyCyanAudioStreamProbe : AudioStreamProbe {
    private val _totalPacketCountFlow = MutableStateFlow(0)
    val totalPacketCountFlow = _totalPacketCountFlow.asStateFlow()

    private val _sessionPacketCountFlow = MutableStateFlow(0)
    val sessionPacketCountFlow = _sessionPacketCountFlow.asStateFlow()
    
    private val _statusFlow = MutableStateFlow("Inactive")
    val statusFlow = _statusFlow.asStateFlow()

    private var totalPacketCount = 0
    private var sessionPacketCount = 0
    
    // Statistical collectors
    private var lastPayloadSize = 0
    private var lastIntervalMs = 0L
    
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

    fun getSessionPackets() = sessionPacketCount
    fun getTotalPackets() = totalPacketCount
    fun getLastPayloadSize() = lastPayloadSize
    fun getLastIntervalMs() = lastIntervalMs
    fun getDecoderStatus() = if (decoderProbe.failedDecodes > 0) "Degraded" else "Healthy"

    override fun onVoiceActivationStarted() {
        sessionPacketCount = 0
        _sessionPacketCountFlow.value = 0
        _statusFlow.value = "Listening"
        HeyCyanLogger.i("AudioProbe", "Voice activation session started. Stats reset.")
    }

    override fun onVoiceActivationEnded() {
        _statusFlow.value = "Stopped"
        HeyCyanLogger.i("AudioProbe", "Voice activation session ended. Final session packets: $sessionPacketCount")
        dumpStatistics()
    }

    override fun onPacket(packet: AudioStreamPacket) {
        totalPacketCount++
        sessionPacketCount++
        lastPayloadSize = packet.length
        
        _totalPacketCountFlow.value = totalPacketCount
        _sessionPacketCountFlow.value = sessionPacketCount
        
        // Transition to Receiving if we were Listening or Stopped
        if (_statusFlow.value != "Error") {
            _statusFlow.value = "Receiving"
        }
        
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
            lastIntervalMs = interval
            if (interval > 0) { 
                if (interval < minInterval) minInterval = interval
                if (interval > maxInterval) maxInterval = interval
                sumInterval += interval
                intervalCount++
            }
        }
        previousTimestamp = packet.timestampMs
        
        decoderProbe.decodePacket(packet.payload, packet.timestampMs * 1000)
        
        if (sessionPacketCount % 20 == 0) {
            dumpStatistics()
        }
    }

    fun onError(message: String) {
        _statusFlow.value = "Error"
        HeyCyanLogger.i("AudioProbe", "Audio stream ERROR: $message")
    }

    fun reset() {
        _statusFlow.value = "Inactive"
        totalPacketCount = 0
        sessionPacketCount = 0
        _totalPacketCountFlow.value = 0
        _sessionPacketCountFlow.value = 0
        payloadSizes.clear()
        byte0Frequencies.clear()
        byte1Frequencies.clear()
        byte2Frequencies.clear()
        minTrailingZeroes = Int.MAX_VALUE
        maxTrailingZeroes = Int.MIN_VALUE
        sumTrailingZeroes = 0
        previousTimestamp = 0
        minInterval = Long.MAX_VALUE
        maxInterval = Long.MIN_VALUE
        sumInterval = 0
        intervalCount = 0
    }

    override fun dumpStatistics() {
        val meanZeroes = if (totalPacketCount > 0) sumTrailingZeroes / totalPacketCount else 0
        val meanInterval = if (intervalCount > 0) sumInterval / intervalCount else 0

        val stats = buildString {
            appendLine("=== BC 59 Audio Stream Statistics ===")
            appendLine("Session Packets: $sessionPacketCount")
            appendLine("Total Packets:   $totalPacketCount")
            
            append("Payload sizes:  ")
            payloadSizes.forEach { (size, count) -> 
                append("$size bytes ($count/$totalPacketCount)  ")
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
