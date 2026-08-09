package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.util.UUID

/**
 * Evidence-gated decoder and encoder definitions for the HeyCyan BLE protocol.
 *
 * **IMPORTANT – Evidence status as of PR-013/014:**
 * All definitions below carry [AnalysisConfidence.HYPOTHESIS] status. They are
 * backed by synthetic test fixture captures only, not real hardware. Every
 * definition will be updated or removed once a matching observation from a real
 * capture is committed to this repository.
 *
 * See [docs/protocols/heycyan/BLE_PROTOCOL.md] and [CAPTURE_ANALYSIS.md] for
 * the evidence workflow.
 *
 * Evidence notation per definition:
 *   - captureId references one of the JSON fixtures in test/resources/captures/
 *   - packetSequence is the 0-based monotonic sequence within that fixture
 */
object HeyCyanProtocolDefinitions {

    // ---------------------------------------------------------------------------
    // Synthetic evidence references
    // ---------------------------------------------------------------------------

    private val BOOT_CAPTURE = "boot-synthetic-001"
    private val BUTTON_CAPTURE = "button-synthetic-001"

    /**
     * The notify/indicate characteristic used in synthetic fixtures.
     * Real value must come from a GATT discovery capture.
     */
    private val NOTIFY_CHARACTERISTIC = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")

    /**
     * The write characteristic used in synthetic fixtures.
     * Real value must come from a GATT discovery capture.
     */
    private val WRITE_CHARACTERISTIC = UUID.fromString("0000abce-0000-1000-8000-00805f9b34fb")

    // ---------------------------------------------------------------------------
    // Event decoder definitions  (PR-013)
    // ---------------------------------------------------------------------------

    /**
     * Returns all event decoder [HeyCyanEventDecoder.Definition]s that are
     * supported by synthetic capture evidence.
     *
     * Confidence: [AnalysisConfidence.HYPOTHESIS] — pending real captures.
     */
    fun eventDecoderDefinitions(): List<HeyCyanEventDecoder.Definition> = listOf(
        readyDefinition(),
        batteryDefinition(),
        singleTapDefinition(),
        doubleTapDefinition(),
        longPressDefinition()
    )

    // ---------------------------------------------------------------------------
    // Command encoder definitions  (PR-014)
    // ---------------------------------------------------------------------------

    /**
     * Returns all command encoder [HeyCyanCommandEncoder.Definition]s supported
     * by synthetic capture evidence.
     *
     * Confidence: [AnalysisConfidence.HYPOTHESIS] — pending real captures.
     */
    fun commandEncoderDefinitions(): List<HeyCyanCommandEncoder.Definition> = listOf(
        requestBatteryDefinition()
    )

    // ---------------------------------------------------------------------------
    // Individual definitions
    // ---------------------------------------------------------------------------

    /**
     * **HYPOTHESIS** — Ready notification.
     *
     * Synthetic observation: boot-synthetic-001, packet 0 → payload `01`.
     * Interpretation: the device sends a single byte `0x01` after the GATT
     * connection and CCCD subscriptions complete, signalling readiness.
     *
     * Evidence: [boot-synthetic-001, seq=0].
     */
    private fun readyDefinition() = HeyCyanEventDecoder.Definition(
        id = "ready-hypothesis-001",
        evidence = listOf(
            PacketEvidenceReference(
                captureId = BOOT_CAPTURE,
                packetSequence = 0L
            )
        ),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size == 1 &&
                payload[0] == 0x01.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.Ready }
    )

    /**
     * **HYPOTHESIS** — Battery level notification.
     *
     * Synthetic observation: boot-synthetic-001, packet 1 → payload `02 5A 00`.
     *   Byte 0: `0x02` → packet type identifier (battery).
     *   Byte 1: `0x5A` = 90 → battery percentage.
     *   Byte 2: `0x00` → charging flag (0 = not charging, non-zero = charging).
     *
     * Evidence: [boot-synthetic-001, seq=1].
     */
    private fun batteryDefinition() = HeyCyanEventDecoder.Definition(
        id = "battery-hypothesis-001",
        evidence = listOf(
            PacketEvidenceReference(
                captureId = BOOT_CAPTURE,
                packetSequence = 1L
            )
        ),
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

    /**
     * **HYPOTHESIS** — Single tap button event.
     *
     * Synthetic observation: button-synthetic-001, packet 0 → payload `03 01`.
     *   Byte 0: `0x03` → button event type.
     *   Byte 1: `0x01` → SINGLE_TAP.
     *
     * Evidence: [button-synthetic-001, seq=0].
     */
    private fun singleTapDefinition() = HeyCyanEventDecoder.Definition(
        id = "button-single-tap-hypothesis-001",
        evidence = listOf(
            PacketEvidenceReference(
                captureId = BUTTON_CAPTURE,
                packetSequence = 0L
            )
        ),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 2 &&
                payload[0] == 0x03.toByte() &&
                payload[1] == 0x01.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.ButtonPressed(ButtonType.SINGLE_TAP) }
    )

    /**
     * **HYPOTHESIS** — Double tap button event.
     *
     * Synthetic observation: button-synthetic-001, packet 1 → payload `03 02`.
     *   Byte 0: `0x03` → button event type.
     *   Byte 1: `0x02` → DOUBLE_TAP.
     *
     * Evidence: [button-synthetic-001, seq=1].
     */
    private fun doubleTapDefinition() = HeyCyanEventDecoder.Definition(
        id = "button-double-tap-hypothesis-001",
        evidence = listOf(
            PacketEvidenceReference(
                captureId = BUTTON_CAPTURE,
                packetSequence = 1L
            )
        ),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 2 &&
                payload[0] == 0x03.toByte() &&
                payload[1] == 0x02.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.ButtonPressed(ButtonType.DOUBLE_TAP) }
    )

    /**
     * **HYPOTHESIS** — Long press button event.
     *
     * Synthetic observation: button-synthetic-001, packet 2 → payload `03 03`.
     *   Byte 0: `0x03` → button event type.
     *   Byte 1: `0x03` → LONG_PRESS.
     *
     * Evidence: [button-synthetic-001, seq=2].
     */
    private fun longPressDefinition() = HeyCyanEventDecoder.Definition(
        id = "button-long-press-hypothesis-001",
        evidence = listOf(
            PacketEvidenceReference(
                captureId = BUTTON_CAPTURE,
                packetSequence = 2L
            )
        ),
        matches = { payload, characteristic ->
            characteristic == NOTIFY_CHARACTERISTIC &&
                payload.size >= 2 &&
                payload[0] == 0x03.toByte() &&
                payload[1] == 0x03.toByte()
        },
        decode = { _, _, _ -> HeyCyanEvent.ButtonPressed(ButtonType.LONG_PRESS) }
    )

    /**
     * **HYPOTHESIS** — RequestBattery command.
     *
     * Synthetic observation: button-synthetic-001, packet 3 (TX) → payload `0A`.
     *   Single byte `0x0A` written to the write characteristic triggers a
     *   battery status notification response.
     *
     * Evidence: [button-synthetic-001, seq=3].
     */
    private fun requestBatteryDefinition() = HeyCyanCommandEncoder.Definition(
        command = HeyCyanCommand.RequestBattery,
        characteristic = WRITE_CHARACTERISTIC,
        payload = byteArrayOf(0x0A.toByte()),
        evidence = listOf(
            PacketEvidenceReference(
                captureId = BUTTON_CAPTURE,
                packetSequence = 3L
            )
        )
    )
}
