package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class HaloScreenNavigationTest {
    @Test fun primaryPagesResolveWithoutAModelInEverySupportedLanguage() {
        val commands = mapOf(
            "notebook" to listOf("open workspace", "открой рабочее пространство", "atver darbvietu", "otwórz obszar roboczy"),
            "screen" to listOf("open halo screen", "открой экран halo", "atver halo ekrānu", "otwórz ekran halo"),
            "studio" to listOf("open studio", "открой студию", "atver studiju", "otwórz studio"),
            "file_tools" to listOf("open file tools", "открой инструменты файлов", "atver failu rīkus", "otwórz narzędzia plików"),
            "study" to listOf("open study", "открой обучение", "atver mācības", "otwórz naukę"),
            "notebook/conversations" to listOf("open halo conversations", "открой беседы halo", "atver halo sarunas", "otwórz rozmowy halo"),
        )
        commands.forEach { (screen, phrases) -> phrases.forEach { phrase ->
            assertEquals(phrase, NativeHaloScreenIntent(screen), NativeCommandParser.parse(phrase))
        } }
    }

    @Test fun registeredPhrasesAreUniqueAndUseTheSameClosedScreenContract() {
        val phrases = NativeHaloScreenDestination.entries.flatMap { it.phrases.toList() }
        assertEquals(phrases.size, phrases.toSet().size)
        NativeHaloScreenDestination.entries.forEach { destination ->
            assertEquals(destination, NativeHaloScreenDestination.fromWire(destination.wireId))
            destination.phrases.forEach { phrase ->
                assertEquals(phrase, NativeHaloScreenIntent(destination.wireId), NativeCommandParser.parse(phrase))
            }
        }
    }

    @Test fun discussionNegationAndObjectRoutesDoNotBecomeNavigation() {
        listOf("explain how to open workspace", "do not open studio", "как открыть обучение", "kā atvērt darbvietu", "jak otworzyć studio",
            "open studio and delete a layer", "open halo canvas/guessed-id", "open intent://halo").forEach {
            assertFalse(it, NativeCommandParser.parse(it) is NativeHaloScreenIntent)
        }
        listOf("canvas/guessed-id", "workspace_handoff", "phone_control_sandbox", "settings/voice/speech-lab", "unknown").forEach {
            assertNull(it, NativeHaloScreenDestination.fromWire(it))
        }
        assertEquals(OpenSettingsIntent("general"), NativeCommandParser.parse("open settings"))
    }
}
