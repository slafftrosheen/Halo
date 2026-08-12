package com.halo.drivers.heycyan.protocol

import com.halo.drivers.heycyan.codec.HeyCyanWireFrameCodec

class HeyCyanDeviceController(
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
        val action: Byte
        val payload: ByteArray

        when (command) {
            is HeyCyanCommand.GetBattery -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_BATTERY
                payload = byteArrayOf()
            }
            is HeyCyanCommand.GetVersion, is HeyCyanCommand.GetDeviceInfo -> {
                action = HeyCyanCommandCatalog.ACTION_DEVICE_INFO
                payload = byteArrayOf()
            }
            is HeyCyanCommand.GetMediaCounts -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                payload = byteArrayOf(0x02, 0x04)
            }
            is HeyCyanCommand.GetMacAddress -> {
                action = HeyCyanCommandCatalog.ACTION_BT_MAC_PROTOCOL
                payload = byteArrayOf()
            }
            is HeyCyanCommand.SetMode -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                payload = byteArrayOf(0x02, 0x01, command.mode.value.toByte())
            }
            is HeyCyanCommand.TakePhoto -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                payload = byteArrayOf(0x02, 0x01, 0x01)
            }
            is HeyCyanCommand.ToggleVideo -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                payload = byteArrayOf(0x02, 0x01, 0x02)
            }
            is HeyCyanCommand.EnterWifiTransferMode -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                payload = byteArrayOf(0x02, 0x01, 0x03)
            }
            is HeyCyanCommand.PrepareVoiceRouting -> {
                action = HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                payload = byteArrayOf(0x02, 0x01, 0x05)
            }
            is HeyCyanCommand.SetDateTime -> {
                action = HeyCyanCommandCatalog.ACTION_SYNC_TIME
                payload = byteArrayOf()
            }
            else -> return Result.failure(
                ProtocolNotVerifiedException(
                    operation = command.toString(),
                    detail = "No evidence-backed catalog definition for command"
                )
            )
        }

        val framed = HeyCyanWireFrameCodec.encode(action, payload)
        return transport.send(framed)
    }
}

interface HeyCyanCommandTransport {
    suspend fun send(bytes: ByteArray): Result<Unit>
}