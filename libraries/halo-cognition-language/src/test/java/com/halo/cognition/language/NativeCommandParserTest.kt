package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class NativeCommandParserTest {

    @Test fun reportedPhysicalCommandsStayNativeRegardlessOfCaseAndWhitespace() {
        val commands = mapOf(
            "Open Settings" to OpenSettingsIntent("general"),
            "open settings" to OpenSettingsIntent("general"),
            "Please  OPEN\u00A0settings!" to OpenSettingsIntent("general"),
            "Go home" to NativeScreenActionIntent(NativeScreenVerb.HOME, "current screen"),
            "go to the home screen" to NativeScreenActionIntent(NativeScreenVerb.HOME, "current screen"),
            "Press Home" to NativeScreenActionIntent(NativeScreenVerb.HOME, "current screen"),
            "turn on flashlight" to FlashlightIntent(true),
            "Turn on the flashlight" to FlashlightIntent(true),
            "turn  ON  the flashlight." to FlashlightIntent(true),
            "Turn off the flashlight" to FlashlightIntent(false)
        )
        commands.forEach { (command, expected) ->
            assertEquals(command, expected, NativeCommandParser.parse(command))
        }
        listOf(
            "Explain how to open settings",
            "How do I turn on the flashlight?",
            "What is the home screen?",
            "Open Settings and explain what it does"
        ).forEach { assertNull(it, NativeCommandParser.parse(it)) }
    }

    @Test fun clockLifecycleIsExplicitAndDoesNotAssumeAnOwnedTimer() {
        assertEquals(NativeClockIntent("show_alarms"), NativeCommandParser.parse("Show my alarms"))
        assertEquals(NativeClockIntent("show_timers"), NativeCommandParser.parse("Open timers"))
        assertEquals(NativeClockIntent("dismiss_next_alarm"), NativeCommandParser.parse("Dismiss the next alarm"))
        assertEquals(NativeClockIntent("snooze_alarm"), NativeCommandParser.parse("Snooze the alarm"))
        assertEquals(NativeClockIntent("dismiss_expired_timers"), NativeCommandParser.parse("Dismiss expired timers"))
        listOf("cancel my active timer", "dismiss all alarms", "How do you snooze an alarm?")
            .forEach { assertNull(it, NativeCommandParser.parse(it)) }
    }

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

    @Test fun scriptSeparatorsDoNotCorruptDictatedValues() {
        assertEquals(
            NativeCommandSequenceIntent(listOf(
                NativeOpenApplicationIntent("Gmail"),
                NativeScreenActionIntent(NativeScreenVerb.TAP, "Search"),
                NativeScreenActionIntent(NativeScreenVerb.ENTER_TEXT, "Search", "fish and chips")
            )),
            NativeCommandParser.parse("Open Gmail then tap Search and type fish and chips into Search")
        )
        assertEquals(
            NativeCommandSequenceIntent(listOf(
                NativeOpenApplicationIntent("Gmail"),
                NativeScreenActionIntent(NativeScreenVerb.ENTER_TEXT, "Search", "click and then buy")
            )),
            NativeCommandParser.parse("Open Gmail then type \"click and then buy\" into Search")
        )
        assertEquals(NativeOpenApplicationIntent("Research and Development"),
            NativeCommandParser.parse("Open Research and Development"))
        assertNull("A compound app goal must not turn into an app name",
            NativeCommandParser.parse("Open Gmail and check my latest invoice"))
    }

    @Test fun globalBackDoesNotRequireSemanticAppSupportOrModel() {
        assertEquals(NativeScreenActionIntent(NativeScreenVerb.BACK, "current screen"),
            NativeCommandParser.parse("Go back"))
        assertEquals(NativeScreenActionIntent(NativeScreenVerb.BACK, "current screen"),
            NativeCommandParser.parse("Press back"))
        assertEquals(
            NativeCommandSequenceIntent(listOf(
                NativeOpenApplicationIntent("Gmail"),
                NativeScreenActionIntent(NativeScreenVerb.BACK, "current screen")
            )),
            NativeCommandParser.parse("Open Gmail then go back")
        )
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
