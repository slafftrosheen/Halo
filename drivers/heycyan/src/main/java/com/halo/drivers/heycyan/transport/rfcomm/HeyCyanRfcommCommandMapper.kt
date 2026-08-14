package com.halo.drivers.heycyan.transport.rfcomm

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode

/**
 * Maps high-level [HeyCyanCommand] instances to raw [MemoMindPacket]s.
 *
 * Mappings are source-backed from CyanBridge / MemoMindGattClient / SDK forensics:
 *   - GetBattery     -> 0x13
 *   - GetVersion/Info-> 0x10
 *   - GetMacAddress  -> 0x11
 *   - GetMediaCounts -> 0x14
 *   - SetMode(mode)  -> 0x01 [mode.value]
 *   - SetDateTime    -> 0x12
 */
interface HeyCyanRfcommCommandMapper {
    fun map(command: HeyCyanCommand): Result<MemoMindPacket>
}

/**
 * Default implementation of [HeyCyanRfcommCommandMapper].
 */
class DefaultHeyCyanRfcommCommandMapper : HeyCyanRfcommCommandMapper {

    override fun map(command: HeyCyanCommand): Result<MemoMindPacket> = runCatching {
        when (command) {
            HeyCyanCommand.GetBattery -> MemoMindPacket(command = 0x13)
            HeyCyanCommand.GetVersion,
            HeyCyanCommand.GetDeviceInfo -> MemoMindPacket(command = 0x10)
            HeyCyanCommand.GetMacAddress -> MemoMindPacket(command = 0x11)
            HeyCyanCommand.GetMediaCounts -> MemoMindPacket(command = 0x14)
            is HeyCyanCommand.SetMode -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(command.mode.value.toByte()),
            )
            is HeyCyanCommand.TakePhoto -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(HeyCyanMode.PHOTO.value.toByte()),
            )
            is HeyCyanCommand.StartVideo -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x02)
            )
            is HeyCyanCommand.StopVideo -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x03)
            )
            is HeyCyanCommand.StartRecording -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x08)
            )
            is HeyCyanCommand.StopRecording -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x0C)
            )
            is HeyCyanCommand.AiThumbnail -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x06, 0x02, 0x02, 0x02)
            )
            is HeyCyanCommand.PrepareVoiceRouting -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x02, 0x01, 0x0B)
            )
            is HeyCyanCommand.EnterWifiTransferMode -> MemoMindPacket(
                command = 0x01,
                payload = byteArrayOf(0x02, 0x01, 0x04, 0x01)
            )
            else -> throw UnsupportedOperationException("HeyCyan RFCOMM command mapping not established for $command")
        }
    }
}
