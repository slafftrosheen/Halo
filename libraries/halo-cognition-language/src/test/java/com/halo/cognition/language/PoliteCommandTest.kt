package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class PoliteCommandTest {
    @Test fun commonSpokenRequestsUseNativeCommands() {
        assertEquals(TimerIntent(300), NativeCommandParser.parse("Could you please set a timer for five minutes?"))
        assertEquals(VolumeIntent(percent = 30), NativeCommandParser.parse("Can you set volume to 30 percent?"))
        assertEquals(NativeOpenApplicationIntent("Spotify"), NativeCommandParser.parse("Would you please open Spotify?"))
        assertEquals(FlashlightIntent(true), NativeCommandParser.parse("Please turn on the flashlight."))
    }
    @Test fun flexiblePhoneRequestsRequireTools() {
        assertEquals(ToolActionReason.ACTION_INTENT, ConversationSemantics.actionReason("Can you change my phone volume to half?"))
        assertEquals(ToolActionReason.SCREEN_OPERATION, ConversationSemantics.actionReason("Could you tap the first item?"))
    }
    @Test fun compositeAppGoalsAreScreenWorkflowsNotWebSearchOrAppNames() {
        listOf(
            "Open Gmail and find invoices",
            "Open Gmail then search for the latest receipt",
            "Find invoice in Gmail",
            "Search in Gmail for the invoice"
        ).forEach { utterance ->
            assertNull("Not an exact native script: $utterance", NativeCommandParser.parse(utterance))
            assertEquals(utterance, ToolActionReason.SCREEN_OPERATION, ConversationSemantics.actionReason(utterance))
        }
        assertNull(ConversationSemantics.actionReason("How do I search in Gmail?"))
    }
    @Test fun conservativeRussianLatvianPolishFallbackKeepsToolsAndQuestionsSeparate() {
        mapOf(
            "Открой Gmail и найди счёт" to ToolActionReason.SCREEN_OPERATION,
            "Найди мои заметки" to ToolActionReason.WORKSPACE_LOOKUP,
            "Включи фонарик" to ToolActionReason.ACTION_INTENT,
            "Atver Gmail un atrodi rēķinu" to ToolActionReason.SCREEN_OPERATION,
            "Atrodi manas piezīmes" to ToolActionReason.WORKSPACE_LOOKUP,
            "Ieslēdz lukturīti" to ToolActionReason.ACTION_INTENT,
            "Otwórz Gmail i znajdź fakturę" to ToolActionReason.SCREEN_OPERATION,
            "Znajdź moje notatki" to ToolActionReason.WORKSPACE_LOOKUP,
            "Włącz latarkę" to ToolActionReason.ACTION_INTENT
        ).forEach { (sentence,expected) ->
            assertEquals(sentence,expected,ConversationSemantics.actionReason(sentence))
        }
        listOf(
            "Как открыть Gmail?", "Почему включается фонарик?",
            "Kā atvērt Gmail?", "Kāpēc ieslēdzas lukturītis?",
            "Jak otworzyć Gmail?", "Dlaczego świeci latarka?"
        ).forEach { sentence ->
            assertTrue(sentence,ConversationSemantics.isExplanation(sentence))
            assertNull(sentence,ConversationSemantics.actionReason(sentence))
        }
        assertNull(ConversationSemantics.actionReason("Не открывай Gmail"))
    }
    @Test fun explanationsAndOptOutRemainConversation() {
        assertTrue(ConversationSemantics.isExplanation(ConversationSemantics.commandText("Can you explain how to change volume?")))
        assertNull(NativeCommandParser.parse("Could you explain how to open Spotify?"))
        assertNull(ConversationSemantics.actionReason("Do not use tools, explain phone settings"))
    }
}
