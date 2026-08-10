package com.halo.drivers.heycyan.protocol

import com.halo.drivers.heycyan.codec.HeyCyanPacketFramer

class HeyCyanDeviceController(
    private val commandCatalog: HeyCyanCommandCatalog = HeyCyanCommandCatalog,
    private val packetFramer: HeyCyanPacketFramer = HeyCyanPacketFramer(),
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

    suspend fun executeCommand(
        command: HeyCyanCommand
    ): Result<Unit> =
        execute(command)

    private suspend fun execute(
        command: HeyCyanCommand
    ): Result<Unit> {
        val definition = commandCatalog.definition(command)
            ?: return Result.failure(
                ProtocolNotVerifiedException(
                    operation = command.toString(),
                    detail = "No evidence-backed catalog definition"
                )
            )

        if (definition.status == ProtocolDefinitionStatus.BLOCKED) {
            return Result.failure(
                ProtocolNotVerifiedException(
                    operation = command.toString(),
                    detail = "Command is explicitly BLOCKED"
                )
            )
        }

        val framed = packetFramer
            .frame(definition)
            .getOrElse { return Result.failure(it) }

        return transport.send(framed)
    }
}

interface HeyCyanCommandTransport {
    suspend fun send(bytes: ByteArray): Result<Unit>
}