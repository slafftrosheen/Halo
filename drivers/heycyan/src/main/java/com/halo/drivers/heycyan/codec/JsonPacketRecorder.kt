package com.halo.drivers.heycyan.codec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records BlePackets to a JSON Lines (.jsonl) file on disk.
 */
class JsonPacketRecorder(private val directory: File) : PacketRecorder {

    private val sessionFile: File by lazy {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        File(directory, "heycyan-capture-\$timestamp.jsonl")
    }

    override suspend fun save(packet: BlePacket): Unit = withContext(Dispatchers.IO) {
        val hexPayload = packet.payload.joinToString("") { "%02X".format(it) }
        val json = """{"timestamp":${packet.timestamp},"direction":"${packet.direction}","characteristic":"${packet.characteristic}","payload":"$hexPayload"}"""
        
        FileOutputStream(sessionFile, true).bufferedWriter().use { writer ->
            writer.appendLine(json)
        }
    }
}
