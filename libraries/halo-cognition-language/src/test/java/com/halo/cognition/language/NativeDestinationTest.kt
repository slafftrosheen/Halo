package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class NativeDestinationTest {
    @Test fun `all destination languages share one typed command`() {
        NativeDestination.entries.forEach { destination -> destination.phrases.forEach { phrase ->
            assertEquals(phrase, NativeDestinationIntent(destination), NativeCommandParser.parse(phrase))
        } }
        NativeGlobalAction.entries.forEach { action -> action.phrases.forEach { phrase ->
            assertEquals(phrase, NativeGlobalIntent(action), NativeCommandParser.parse(phrase))
        } }
        NativeObservationKind.entries.forEach { kind -> kind.phrases.forEach { phrase ->
            assertEquals(phrase, NativeObservationIntent(kind), NativeCommandParser.parse(phrase))
        } }
    }
    @Test fun `arbitrary intent components and explanatory requests do not execute`() {
        assertNull(NativeDestination.fromWire("android.intent.action.CALL"))
        assertNull(NativeCommandParser.parse("explain how to take a photo"))
        assertFalse(NativeCommandParser.parse("open intent://arbitrary") is NativeDestinationIntent)
    }
    @Test fun `settings languages route to the same canonical section`() {
        listOf("open mobile network settings", "atver mobilā tīkla iestatījumus", "открой настройки мобильной сети").forEach {
            assertEquals(OpenSettingsIntent("mobile_network"), NativeCommandParser.parse(it))
        }
    }
}
