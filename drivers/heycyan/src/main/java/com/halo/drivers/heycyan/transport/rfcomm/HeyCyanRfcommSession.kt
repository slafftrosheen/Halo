package com.halo.drivers.heycyan.transport.rfcomm

import com.halo.drivers.heycyan.codec.HeyCyanEventDecoder
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Response received over RFCOMM for a command.
 */
data class MemoMindResponse(
    val packet: MemoMindPacket,
    val receivedAtMillis: Long,
)

/**
 * High-level command session over RFCOMM channel.
 * Serializes request/response cycles using a Mutex to guarantee correlation.
 */
interface HeyCyanRfcommSession {
    suspend fun request(
        command: HeyCyanCommand,
        timeout: Duration = 5.seconds,
    ): Result<MemoMindResponse>
}

/**
 * Default implementation of [HeyCyanRfcommSession].
 *
 * Single stream reader feeds packetDecoder. Incoming packets update [HeyCyanDeviceStateStore]
 * via [HeyCyanEventDecoder] and resolve pending correlated requests.
 */
class DefaultHeyCyanRfcommSession(
    private val transport: HeyCyanRfcommTransport,
    private val mapper: HeyCyanRfcommCommandMapper = DefaultHeyCyanRfcommCommandMapper(),
    private val encoder: MemoMindPacketEncoder = MemoMindPacketEncoder(),
    private val decoder: MemoMindPacketDecoder = MemoMindPacketDecoder(),
    private val eventDecoder: HeyCyanEventDecoder = HeyCyanEventDecoder(),
    private val stateStore: HeyCyanDeviceStateStore? = null,
    scope: CoroutineScope? = null,
) : HeyCyanRfcommSession {

    private val sessionScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestMutex = Mutex()
    private val incomingPackets = MutableSharedFlow<MemoMindPacket>(extraBufferCapacity = 64)
    private var readerJob: Job? = null

    init {
        startReader()
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = sessionScope.launch {
            transport.incoming.collect { bytes ->
                val packets = decoder.feed(bytes)
                for (packet in packets) {
                    incomingPackets.emit(packet)

                    // Pass response to event decoder & state store
                    val event = mapPacketToEvent(packet)
                    if (event != null && stateStore != null) {
                        stateStore.apply(event)
                    }
                }
            }
        }
    }

    override suspend fun request(
        command: HeyCyanCommand,
        timeout: Duration,
    ): Result<MemoMindResponse> = requestMutex.withLock {
        runCatching {
            val mappedPacket = mapper.map(command).getOrThrow()
            val encodedBytes = encoder.encode(mappedPacket)

            // Send command frame
            transport.write(encodedBytes).getOrThrow()

            // Wait for matching response packet or timeout
            val responsePacket = withTimeoutOrNull(timeout) {
                incomingPackets.first { incoming ->
                    incoming.command == mappedPacket.command
                }
            } ?: throw IllegalStateException("Timeout waiting for RFCOMM response to command 0x${mappedPacket.command.toString(16)}")

            MemoMindResponse(
                packet = responsePacket,
                receivedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun mapPacketToEvent(packet: MemoMindPacket): HeyCyanEvent? {
        // Construct raw frame for HeyCyanEventDecoder compatibility
        val totalLen = 4 + packet.payload.size
        val raw = ByteArray(totalLen)
        raw[0] = 0xAA.toByte()
        raw[1] = totalLen.toByte()
        raw[2] = packet.command.toByte()
        packet.payload.copyInto(raw, destinationOffset = 3)
        raw[totalLen - 1] = 0x00.toByte()

        val decoded = eventDecoder.decode(payload = raw)
        return decoded.event
    }
}
