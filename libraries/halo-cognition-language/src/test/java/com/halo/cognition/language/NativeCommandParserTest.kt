package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class NativeCommandParserTest {
    @Test fun resolvedCommands() {
        val cases = mapOf(
            "Set a timer for five minutes" to TimerIntent(300),
            "Lower volume" to VolumeIntent(direction = "down"),
            "Turn off the flashlight" to FlashlightIntent(false),
            "Mute the phone" to RingerIntent("silent"),
            "Set brightness to forty percent" to BrightnessIntent(40),
            "Open Bluetooth settings" to OpenSettingsIntent("bluetooth"),
            "Share hello" to ShareIntent("hello"),
            "Copy to clipboard Hello" to ClipboardWriteIntent("Hello"),
            "Read clipboard" to ClipboardReadIntent,
            "Show battery level" to BatteryStatusIntent,
            "Show network status" to NetworkStatusIntent,
            "Show device info" to DeviceInfoIntent)
        cases.forEach { (command, intent) -> assertEquals(command, intent, NativeCommandParser.parse(command)) }
    }
    @Test fun deterministicSequenceIsBounded() {
        assertTrue(NativeCommandParser.parse("Open Spotify and set volume to 30 percent") is NativeCommandSequenceIntent)
        assertNull(NativeCommandParser.parse("Open Spotify and make something nice"))
        assertNull(NativeCommandParser.parse("Open A and open B and open C and open D and open E"))
    }
    @Test fun explicitScreenWorkflowKeepsEveryStepAndNeverTurnsIntoAnAppName() {
        val parsed = NativeCommandParser.parse("Open Gmail then tap Search and type invoice into Search")
        assertEquals(
            NativeCommandSequenceIntent(listOf(
                NativeOpenApplicationIntent("Gmail"),
                NativeScreenActionIntent(NativeScreenVerb.TAP, "Search"),
                NativeScreenActionIntent(NativeScreenVerb.ENTER_TEXT, "Search", "invoice")
            )),
            parsed
        )
        assertEquals(NativeScreenActionIntent(NativeScreenVerb.TAP, "Compose"),
            NativeCommandParser.parse("Tap Compose"))
        assertEquals(NativeScreenActionIntent(NativeScreenVerb.ENTER_TEXT, "Subject", "Hello"),
            NativeCommandParser.parse("Type Hello into Subject"))
        assertNull(NativeCommandParser.parse("Open Gmail and find all invoices"))
        assertNull(NativeCommandParser.parse("Open Gmail then delete everything"))
        assertNull(NativeCommandParser.parse("Open A then tap X then tap Y then tap Z then tap W"))
        assertNull(NativeCommandParser.parse("How can I tap Compose?"))
        assertNull(NativeCommandParser.parse("Tap the first item"))
        assertNull(NativeCommandParser.parse("Type hello into it"))
        assertNull(NativeCommandParser.parse("Open Gmail then tap the first result"))
    }
    @Test fun scrollingIsNativeAndCannotInventATarget() {
        assertEquals(NativeScreenActionIntent(NativeScreenVerb.SCROLL_FORWARD, "current screen"),
            NativeCommandParser.parse("Scroll down"))
        assertEquals(NativeScreenActionIntent(NativeScreenVerb.SCROLL_BACKWARD, "current screen"),
            NativeCommandParser.parse("Scroll up"))
        assertEquals(
            NativeCommandSequenceIntent(listOf(
                NativeOpenApplicationIntent("Gmail"),
                NativeScreenActionIntent(NativeScreenVerb.SCROLL_FORWARD, "current screen"),
                NativeScreenActionIntent(NativeScreenVerb.TAP, "Compose")
            )),
            NativeCommandParser.parse("Open Gmail then scroll down then tap Compose")
        )
        assertNull(NativeCommandParser.parse("Scroll until you find the first result"))
    }

    @Test fun invalidNativeSlotsRequestClarification() {
        listOf("Set a timer for zero minutes", "Set volume to 101 percent", "Set brightness to 0 percent", "Set a timer for 25 hours", "Set a timer").forEach {
            assertTrue(it, NativeCommandParser.parse(it) is NativeClarificationIntent)
        }
    }
    @Test fun rejectsExplanationsCompoundAmbiguousAndOutOfRange() {
        listOf("Explain how to set a timer for five minutes", "Spotify",
            "Copy " + "x".repeat(8001)).forEach {
            assertNull(it, NativeCommandParser.parse(it))
        }
    }
}
