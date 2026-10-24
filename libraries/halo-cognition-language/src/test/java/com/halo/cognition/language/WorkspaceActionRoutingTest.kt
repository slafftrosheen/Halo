package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class WorkspaceActionRoutingTest {
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
