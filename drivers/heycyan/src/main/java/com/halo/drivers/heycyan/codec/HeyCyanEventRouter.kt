package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.util.UUID

/**
 * Characteristic-aware event router for HeyCyan devices.
 *
 * This router receives both characteristic UUID and payload, allowing it to
 * distinguish between different protocol families on different characteristics.
 */
class HeyCyanEventRouter(
    private val responseDecoder: HeyCyanResponseDecoder,
    private val structuralDecoder: HeyCyanStructuralDecoder = HeyCyanStructuralDecoder()
) {

    fun decode(
        characteristicUuid: UUID,
        payload: ByteArray,
        timestampMillis: Long = System.currentTimeMillis()
    ): HeyCyanEvent {

        val rawFrame = RawHeyCyanFrame(
            timestampMillis = timestampMillis,
            direction = PacketDirection.RX,
            characteristicUuid = characteristicUuid,
            bytes = payload.copyOf()
        )

        /*
         * The de5bf729 stream has now been observed physically and
         * correlated with button interaction.
         */
        if (characteristicUuid == DE5BF729) {
            val decoded = structuralDecoder.decode(rawFrame)
            if (decoded.actionFamily != HeyCyanActionFamily.UNKNOWN) {
                if (decoded.actionFamily == HeyCyanActionFamily.AUDIO_STREAM) {
                    val p = decoded.payload
                    var trailingZeroes = 0
                    for (i in p.indices.reversed()) {
                        if (p[i] == 0.toByte()) trailingZeroes++ else break
                    }
                    val packet = com.halo.drivers.heycyan.debug.AudioStreamPacket(
                        timestampMs = timestampMillis,
                        payload = p,
                        length = p.size,
                        firstBytes = if (p.size >= 4) p.copyOfRange(0, 4) else p,
                        trailingZeroCount = trailingZeroes
                    )
                    com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.onPacket(packet)
                    return HeyCyanEvent.RawAudioFrame(p)
                }

                if (decoded.actionFamily == HeyCyanActionFamily.DEVICE_DATA_REPORTING) {
                    if (decoded.payload.isNotEmpty()) {
                        when (decoded.payload[0]) {
                            0x02.toByte() -> return HeyCyanEvent.AiImageRecognition
                            0x03.toByte() -> return HeyCyanEvent.VoiceActivationStarted
                            0x04.toByte() -> return HeyCyanEvent.OtaProgress(decoded.payload.copyOfRange(1, decoded.payload.size))
                            // 0x05 BatteryUpdated is already decoded in responseDecoder, but if reported here we'd handle it.
                            0x0A.toByte() -> return HeyCyanEvent.VoiceActivationEnded
                            0x0C.toByte() -> return HeyCyanEvent.VoicePlaybackPause
                            0x0D.toByte() -> return HeyCyanEvent.UnbindApp
                            0x0E.toByte() -> return HeyCyanEvent.MemoryInsufficient
                            0x12.toByte() -> return HeyCyanEvent.VolumeChange(decoded.payload.copyOfRange(1, decoded.payload.size))
                        }
                    }
                }

                return HeyCyanEvent.DecodedProprietaryFrame(
                    actionFamily = decoded.actionFamily,
                    action = decoded.action,
                    length = decoded.payloadLength,
                    isValidCrc = decoded.isValidCrc,
                    rawHex = decoded.raw.hex(),
                    fields = decoded.fields
                )
            }
        }

        val decoded = responseDecoder.decode(payload)
        return when (decoded) {
            is HeyCyanResponse.Battery -> HeyCyanEvent.BatteryUpdated(decoded.level, decoded.charging)
            is HeyCyanResponse.ClassBluetooth -> HeyCyanEvent.WiFiIpReceived("") // Map as needed or remove
            is HeyCyanResponse.VoiceState -> {
                if (decoded.payload.isNotEmpty()) {
                    when (decoded.payload[0]) {
                        0x02.toByte() -> HeyCyanEvent.AiImageRecognition
                        0x03.toByte() -> HeyCyanEvent.VoiceActivationStarted
                        0x04.toByte() -> HeyCyanEvent.OtaProgress(decoded.payload.copyOfRange(1, decoded.payload.size))
                        0x0A.toByte() -> HeyCyanEvent.VoiceActivationEnded
                        0x0C.toByte() -> HeyCyanEvent.VoicePlaybackPause
                        0x0D.toByte() -> HeyCyanEvent.UnbindApp
                        0x0E.toByte() -> HeyCyanEvent.MemoryInsufficient
                        0x12.toByte() -> HeyCyanEvent.VolumeChange(decoded.payload.copyOfRange(1, decoded.payload.size))
                        else -> HeyCyanEvent.Unknown(characteristicUuid.toString(), payload.copyOf())
                    }
                } else {
                    HeyCyanEvent.Unknown(characteristicUuid.toString(), payload.copyOf())
                }
            }
            null, is HeyCyanResponse.Unknown -> HeyCyanEvent.Unknown(
                characteristicUuid = characteristicUuid.toString(),
                payload = payload.copyOf()
            )
            else -> HeyCyanEvent.Unknown(characteristicUuid.toString(), payload.copyOf())
        }
    }

    companion object {
        private val DE5BF729 =
            UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
    }
}
