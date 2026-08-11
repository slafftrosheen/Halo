package com.halo.drivers.heycyan.rfcomm

import java.util.UUID

/**
 * Candidate RFCOMM channels observed in the external HeyCyan research source.
 *
 * These identifiers are transport candidates only. They do not promote any
 * command or frame meaning into Halo's production protocol definitions.
 */
data class HeyCyanRfcommChannel(
    val name: String,
    val uuid: UUID,
    val required: Boolean
) {
    companion object {
        val PRIMARY = HeyCyanRfcommChannel(
            name = "primary",
            uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"),
            required = false
        )

        val EXTRA = HeyCyanRfcommChannel(
            name = "extra",
            uuid = UUID.fromString("00002026-0000-1000-8000-00805f9b34fb"),
            required = false
        )

        val RECORD = HeyCyanRfcommChannel(
            name = "record",
            uuid = UUID.fromString("00002024-0000-1000-8000-00805f9b34fb"),
            required = false
        )

        val CANDIDATES = listOf(PRIMARY, EXTRA, RECORD)
    }
}

enum class RfcommDirection {
    TX,
    RX
}

data class RfcommChunk(
    val channel: String,
    val direction: RfcommDirection,
    val bytes: ByteArray
) {
    init {
        require(channel.isNotBlank()) { "RFCOMM channel must not be blank" }
    }

    fun copyBytes(): ByteArray = bytes.copyOf()
}

/**
 * Reassembles complete RFCOMM control frames without interpreting group/opcode.
 * Incomplete or non-frame bytes remain observable as raw chunks.
 */
class RfcommControlFrameAssembler {
    private val pending = ArrayList<Byte>()

    fun append(bytes: ByteArray): List<ByteArray> {
        pending.ensureCapacity(pending.size + bytes.size)
        bytes.forEach { pending += it }
        val frames = mutableListOf<ByteArray>()

        while (pending.size >= 4) {
            val start = findFramePrefix()
            if (start < 0) {
                pending.clear()
                break
            }
            if (start > 0) repeat(start) { pending.removeAt(0) }
            if (pending.size < 4) break

            val frameLength = ((pending[2].toInt() and 0xFF) shl 8) or
                (pending[3].toInt() and 0xFF)
            if (frameLength < 4) {
                pending.removeAt(0)
                continue
            }
            if (pending.size < frameLength) break

            frames += ByteArray(frameLength) { pending.removeAt(0) }
        }

        return frames
    }

    fun reset() {
        pending.clear()
    }

    private fun findFramePrefix(): Int {
        for (index in 0 until pending.size - 1) {
            if (pending[index].toInt() and 0xFF == 0xFA &&
                pending[index + 1].toInt() and 0xFF == 0x00
            ) {
                return index
            }
        }
        return -1
    }
}
