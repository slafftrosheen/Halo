package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class WorkspaceActionRoutingTest {
    @Test fun singularCountUsesTheSameActionEvidenceAsTheExistingArticleWithoutSupplyingSlots() {
        val suffix = "note titled HALO-AI-20261025-NOTE-B with body exactly Violet lanterns glow at dawn. Return its actual saved id. Do not change other items."
        val expected = ConversationSemantics.actionReason("Create a $suffix")
        assertEquals(ToolActionReason.ACTION_INTENT, expected)
        listOf("Create one $suffix", "Create 1 $suffix", "Please create one new local note titled Lanterns",
            "Add one document to the selected board", "Export 1 selected file").forEach { request ->
            assertEquals(request, expected, ConversationSemantics.actionReason(request))
            assertNull("Action evidence must not invent resolved native slots: $request", NativeCommandParser.parse(request))
        }
    }

    @Test fun singularCountDoesNotIntroducePluralCountsNarrativesQuotesOrNegatedActions() {
        listOf("Create 2 notes", "Create two notes", "Create 10 notes", "Create one notes", "Create 1 files",
            "Create one or more notes", "Create 1 2 notes", "Create one one note", "Create 1.5 notes",
            "How do I create one note?", "Explain how to create 1 note", "Could you not create one note?",
            "Do not create one note", "Do not use tools: create one note", "Answer from your model: create 1 note",
            "\"Create one note\"", "'Create 1 note'", "The instruction says create one note",
            "I created one note yesterday", "Write a story about creating one note").forEach { request ->
            assertNull(request, ConversationSemantics.actionReason(request))
        }
    }

    @Test fun explicitWorkspaceWritesRemainActionsWithoutALocalClassifier() {
        listOf(
            "Create a note containing QA55-T1 bring water and a map. Then create a Canvas board and add the note to it.",
            "Please create a new note about the trip",
            "Add the note to the Canvas board",
            "Edit the selected Canvas cards and export the board",
            "Export the Canvas board to a PDF file",
            "Создай заметку со списком и добавь её на холст",
            "Измени выбранную карточку на холсте",
            "Izveido piezīmi par pārgājienu",
            "Pievieno piezīmi tāfelei",
            "Utwórz notatkę o wycieczce",
            "Edytuj wybraną kartę na tablicy",
        ).forEach { request ->
            assertNotNull(request, ConversationSemantics.actionReason(request))
        }
    }

    @Test fun discussionNegationAndModelOnlyRequestsDoNotAuthorizeWorkspaceActions() {
        listOf(
            "How do I create a note?", "Explain how to export a Canvas board",
            "Create a story about a notebook", "Do not create a note",
            "Could you not delete my note?", "Answer from your model: create a note",
            "Как создать заметку?", "Объясни, как изменить карточку",
            "Kā izveidot piezīmi?", "Jak utworzyć notatkę?",
        ).forEach { request -> assertNull(request, ConversationSemantics.actionReason(request)) }
    }
}
