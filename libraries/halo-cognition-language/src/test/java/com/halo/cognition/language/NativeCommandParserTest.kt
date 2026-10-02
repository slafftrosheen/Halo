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
    @Test fun invalidNativeSlotsRequestClarification() {
        listOf("Set a timer for zero minutes", "Set volume to 101 percent", "Set brightness to 0 percent", "Set a timer for 25 hours", "Set a timer").forEach {
            assertTrue(it, NativeCommandParser.parse(it) is NativeClarificationIntent)
        }
    }
    @Test fun rejectsExplanationsCompoundAmbiguousAndOutOfRange() {
        listOf("Explain how to set a timer for five minutes", "Spotify",
            "Turn the flashlight on and lower volume", "Copy " + "x".repeat(8001)).forEach {
            assertNull(it, NativeCommandParser.parse(it))
        }
    }
}
