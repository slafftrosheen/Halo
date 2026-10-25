package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class SelectedCanvasWriteRoutingTest {
    private val exact = "Change only the selected text card on this Canvas to exactly: Silver kites rest at noon. They fly west at dusk."

    @Test fun actualSelectedCardReplacementIsPositiveActionEvidenceWithoutInventedNativeSlots() {
        assertEquals(ToolActionReason.ACTION_INTENT, ConversationSemantics.actionReason(exact))
        assertNull("The selection and board must still be resolved by canonical context/tools", NativeCommandParser.parse(exact))
    }

    @Test fun changeVerbAndOnlyModifierHaveBoundedNaturalWorkspaceVariants() {
        listOf(
            "Change the selected card to Silver kites rest at noon.",
            "Change one selected text card to Silver kites rest at noon.",
            "Change only the selected text card on this Canvas to exactly: Silver kites rest at noon.",
            "Update only the selected text card on this Canvas to exactly: Silver kites rest at noon.",
            "Please edit only the selected text card on this board.",
            "Can you change only this selected text card to Silver kites rest at noon?",
            "Could you please change only my selected text card on this Canvas?",
            "Change only the selected note to Silver kites rest at noon.",
        ).forEach { request ->
            assertEquals(request, ToolActionReason.ACTION_INTENT, ConversationSemantics.actionReason(request))
            assertNull("No executable IDs or content slots may be manufactured: $request", NativeCommandParser.parse(request))
        }
    }

    @Test fun discussionsQuotedRequestsNegationAndModelOnlyStayConversation() {
        listOf(
            "How do I change only the selected text card on this Canvas?",
            "Explain how to change only the selected text card on this Canvas.",
            "Can you explain how to change only the selected text card?",
            "Write a story about changing only the selected text card.",
            "Do not change only the selected text card on this Canvas.",
            "Could you not change only the selected text card?",
            "Don't use tools; change only the selected text card.",
            "Answer from your model: change only the selected text card.",
            "\"Change only the selected text card on this Canvas\"",
            "The instruction says: Change only the selected text card on this Canvas.",
            "I changed the selected text card yesterday.",
            "Change is hard when writing a story about a Canvas.",
            "Change only the selected text color to silver.",
            "Change only the selected phone wallpaper.",
        ).forEach { request -> assertNull(request, ConversationSemantics.actionReason(request)) }
    }

    @Test fun expansionDoesNotBecomeUnboundedFillerCountsOrPartialObjectNames() {
        listOf(
            "Change only the selected existing local text card to silver.",
            "Change whatever seems useful about the selected text card.",
            "Change two selected text cards.",
            "Change 2 selected text cards.",
            "Change one selected text cards.",
            "Change only the selected text cardboard.",
            "Change only the selected text cardigan.",
        ).forEach { request -> assertNull(request, ConversationSemantics.actionReason(request)) }
    }
}
