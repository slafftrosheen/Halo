package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.util.UUID

/**
 * Synthetic-only protocol fixtures for offline codec tests.
 *
 * These definitions are deliberately test-scoped. They are not production
 * protocol knowledge and must never be supplied to discovery or runtime code.
 * Promotion requires a reproducible real-device capture or authoritative vendor
 * documentation, followed by reviewed evidence references.
 */
object SyntheticHeyCyanProtocolFixtures {

    private val BOOT_CAPTURE = "boot-synthetic-001"
    private val BUTTON_CAPTURE = "button-synthetic-001"

    private val NOTIFY_CHARACTERISTIC = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val WRITE_CHARACTERISTIC = UUID.fromString("0000abce-0000-1000-8000-00805f9b34fb")

    /** Returns event definitions backed only by synthetic fixture captures. */
    fun eventDecoderDefinitions(): List<HeyCyanEventDecoder.Definition> = listOf(
        readyDefinition(),
        batteryDefinition(),
        singleTapDefinition(),
        doubleTapDefinition(),
        longPressDefinition()
    )

    /** Returns command definitions backed only by synthetic fixture captures. */
    fun commandEncoderDefinitions(): List<HeyCyanCommandEncoder.Definition> = listOf(
        requestBatteryDefinition()
    )

    private fun readyDefinition() = HeyCyanEventDecoder.Definition(
        id = "ready-hypothesis-001",
        evidence = listOf(PacketEvidenceReference(BOOT_CAPTURE, packetSequence = 0L)),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size == 1 &&
                payload[0] == 0x01.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.Ready }
    )

    private fun batteryDefinition() = HeyCyanEventDecoder.Definition(
        id = "battery-hypothesis-001",
        evidence = listOf(PacketEvidenceReference(BOOT_CAPTURE, packetSequence = 1L)),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 3 &&
                payload[0] == 0x02.toByte()
        },
        decode = { payload, _, _ ->
            val level = payload[1].toInt() and 0xFF
            val isCharging = payload[2] != 0x00.toByte()
            HeyCyanEvent.BatteryUpdated(level = level, isCharging = isCharging)
        }
    )

    private fun singleTapDefinition() = HeyCyanEventDecoder.Definition(
        id = "button-single-tap-hypothesis-001",
        evidence = listOf(PacketEvidenceReference(BUTTON_CAPTURE, packetSequence = 0L)),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 2 &&
                payload[0] == 0x03.toByte() &&
                payload[1] == 0x01.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.ButtonPressed(ButtonType.SINGLE_TAP) }
    )

    private fun doubleTapDefinition() = HeyCyanEventDecoder.Definition(
        id = "button-double-tap-hypothesis-001",
        evidence = listOf(PacketEvidenceReference(BUTTON_CAPTURE, packetSequence = 1L)),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 2 &&
                payload[0] == 0x03.toByte() &&
                payload[1] == 0x02.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.ButtonPressed(ButtonType.DOUBLE_TAP) }
    )

    private fun longPressDefinition() = HeyCyanEventDecoder.Definition(
        id = "button-long-press-hypothesis-001",
        evidence = listOf(PacketEvidenceReference(BUTTON_CAPTURE, packetSequence = 2L)),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 2 &&
                payload[0] == 0x03.toByte() &&
                payload[1] == 0x03.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.ButtonPressed(ButtonType.LONG_PRESS) }
    )

    private fun requestBatteryDefinition() = HeyCyanCommandEncoder.Definition(
        command = HeyCyanCommand.RequestBattery,
        characteristic = WRITE_CHARACTERISTIC,
        payload = byteArrayOf(0x0A.toByte()),
        evidence = listOf(PacketEvidenceReference(BUTTON_CAPTURE, packetSequence = 3L))
    )
}
