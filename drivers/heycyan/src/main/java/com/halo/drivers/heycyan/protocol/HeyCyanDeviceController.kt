package com.halo.drivers.heycyan.protocol

import com.halo.drivers.heycyan.codec.EncodedCommand
import com.halo.drivers.heycyan.codec.HeyCyanCommandEncoder
import com.halo.drivers.heycyan.codec.HeyCyanPacketFramer

class HeyCyanDeviceController(
    private val commandEncoder: HeyCyanCommandEncoder,
    private val packetFramer: HeyCyanPacketFramer,
    private val transport: HeyCyanCommandTransport,
) {

    suspend fun getBattery(): Result<Unit> =
        execute(HeyCyanCommand.GetBattery)

    suspend fun getVersion(): Result<Unit> =
        execute(HeyCyanCommand.GetVersion)

    suspend fun getMediaCounts(): Result<Unit> =
        execute(HeyCyanCommand.GetMediaCounts)

    suspend fun getMacAddress(): Result<Unit> =
        execute(HeyCyanCommand.GetMacAddress)

    suspend fun setMode(
        mode: HeyCyanMode
    ): Result<Unit> =
        execute(HeyCyanCommand.SetMode(mode))

    private suspend fun execute(
        command: HeyCyanCommand
    ): Result<Unit> {

        val encoded = commandEncoder
            .encode(command)
            .getOrElse { return Result.failure(it) }

        val framed = packetFramer
            .frame(encoded)
            .getOrElse { return Result.failure(it) }

        return transport.send(framed)
    }
}

interface HeyCyanCommandTransport {
    suspend fun send(bytes: ByteArray): Result<Unit>
}