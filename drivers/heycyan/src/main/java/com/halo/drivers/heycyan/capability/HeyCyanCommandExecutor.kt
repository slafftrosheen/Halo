package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture
import com.halo.drivers.heycyan.debug.HeyCyanLogger
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog
import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport

/**
 * Executes a low-level [HeyCyanCommand] through the encode → frame → transport pipeline.
 *
 * This is the boundary between the semantic capability layer and the evidence-backed
 * BLE wire protocol. Unsupported or unverified commands fail before transport.
 */
interface HeyCyanCommandExecutor {
    suspend fun execute(command: HeyCyanCommand): Result<Unit>
}

/**
 * Default implementation of [HeyCyanCommandExecutor].
 *
 * No guessed packets are ever transmitted. A command must have an explicit,
 * evidence-backed catalog mapping before it can reach the BLE transport.
 */
class DefaultHeyCyanCommandExecutor(
    private val transport: HeyCyanCommandTransport,
) : HeyCyanCommandExecutor {

    override suspend fun execute(command: HeyCyanCommand): Result<Unit> {
        HeyCyanLogger.i("HALO-TX-TRACE", "HeyCyan command requested: $command")
        HeyCyanProtocolCapture.instance.startOperation(command.toString())
        try {
            val action: Byte
            val payload: ByteArray

            when (command) {
                is HeyCyanCommand.GetBattery, is HeyCyanCommand.RequestBattery -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_BATTERY
                    payload = byteArrayOf()
                }
                is HeyCyanCommand.GetVersion, is HeyCyanCommand.GetDeviceInfo -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_DEVICE_INFO
                    payload = byteArrayOf()
                }
                is HeyCyanCommand.GetMediaCounts -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x04)
                }
                is HeyCyanCommand.GetMacAddress -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_BT_MAC_PROTOCOL
                    payload = byteArrayOf()
                }
                is HeyCyanCommand.SetMode -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, command.mode.value.toByte())
                }
                is HeyCyanCommand.TakePhoto -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x01)
                }
                is HeyCyanCommand.StartVideo -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x02)
                }
                is HeyCyanCommand.StopVideo -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x03)
                }
                is HeyCyanCommand.StartRecording -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x08)
                }
                is HeyCyanCommand.StopRecording -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x0C)
                }
                is HeyCyanCommand.AiThumbnail -> {
                    val q = command.quality.toByte()
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x06, q, q)
                }
                is HeyCyanCommand.EnterWifiTransferMode -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x04, 0x01)
                }
                is HeyCyanCommand.ExitWifiTransferMode -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x09)
                }
                is HeyCyanCommand.PrepareVoiceRouting -> {
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL
                    payload = byteArrayOf(0x02, 0x01, 0x0B)
                }
                is HeyCyanCommand.RouteAudioToGlasses -> {
                    // Vendor LargeDataHandler.speakSoundSwitch(false):
                    // ACTION_SPEAK_SOUND_SWITCH (0x52), payload { 0x02, 0x01 }.
                    action = com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog.ACTION_SPEAK_SOUND_SWITCH
                    payload = byteArrayOf(0x02, 0x01)
                }
                is HeyCyanCommand.RouteAudioToPhone -> {
                    action = HeyCyanCommandCatalog.ACTION_SPEAK_SOUND_SWITCH
                    payload = byteArrayOf(0x02, 0x02)
                }
                is HeyCyanCommand.EnableClassicBluetooth -> {
                    action = HeyCyanCommandCatalog.ACTION_BT_CONNECT
                    payload = byteArrayOf(0x02, 0x01)
                }
                is HeyCyanCommand.SetDateTime -> return Result.failure(
                    com.halo.drivers.heycyan.protocol.ProtocolNotVerifiedException(
                        operation = command.toString(),
                        detail = "Date/time synchronization payload has not been verified; transmission blocked"
                    )
                )
                else -> return Result.failure(
                    com.halo.drivers.heycyan.protocol.ProtocolNotVerifiedException(
                        operation = command.toString(),
                        detail = "No evidence-backed catalog definition for command"
                    )
                )
            }

            val framed = com.halo.drivers.heycyan.codec.HeyCyanWireFrameCodec.encode(action, payload)
            HeyCyanLogger.i("HALO-TX-TRACE", "HeyCyan dispatch transport=${transport::class.simpleName}")
            return transport.send(framed)
        } finally {
            HeyCyanProtocolCapture.instance.endOperation()
        }
    }
}
