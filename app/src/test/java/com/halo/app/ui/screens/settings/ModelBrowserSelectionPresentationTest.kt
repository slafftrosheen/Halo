package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test

class ModelBrowserSelectionPresentationTest {
    private val conversation = AiModelRef("conversation-provider", "conversation-model")
    private val actions = AiRoleBinding(AiModelRole.AGENTIC, "actions-provider", "actions-model", AiRoleSelection.EXPLICIT)

    @Test fun actionsMarkerUsesExactRoleBindingAndNeverTheConversationDefault() {
        val selected = modelBrowserSelectionPresentation(AiModelRole.AGENTIC, actions, conversation)
        assertEquals(AiModelRef("actions-provider", "actions-model"), selected.target)
        assertEquals("Selected", selected.marker)
        assertNotEquals(conversation, selected.target)
        assertEquals(AiRoleSelection.EXPLICIT, selected.mode)
    }

    @Test fun genericAndConversationFiltersKeepTheExistingDefaultMarker() {
        for (role in listOf(null, AiModelRole.CHAT)) {
            val selected = modelBrowserSelectionPresentation(role, actions, conversation)
            assertEquals(conversation, selected.target)
            assertEquals("Conversation default", selected.marker)
            assertNull(selected.mode)
        }
    }

    @Test fun inheritanceIsExplicitAndAutomaticOrOffNeverPretendsToChooseACandidate() {
        val inherited = modelBrowserSelectionPresentation(AiModelRole.AGENTIC,
            actions.copy(selection = AiRoleSelection.INHERIT_DEFAULT), conversation)
        assertEquals(conversation, inherited.target)
        assertEquals("Uses conversation default", inherited.marker)
        for (mode in listOf(AiRoleSelection.AUTOMATIC, AiRoleSelection.DISABLED)) {
            val result = modelBrowserSelectionPresentation(AiModelRole.AGENTIC, actions.copy(selection = mode), conversation)
            assertNull(result.target); assertNull(result.marker); assertEquals(mode, result.mode)
        }
    }

    @Test fun missingOrWrongRoleAndIncompleteIdentifiersCannotMarkACardSelected() {
        assertNull(modelBrowserSelectionPresentation(AiModelRole.VISION, actions, conversation).target)
        assertNull(modelBrowserSelectionPresentation(AiModelRole.AGENTIC, null, conversation).target)
        assertNull(modelBrowserSelectionPresentation(AiModelRole.AGENTIC, actions.copy(modelId = null), conversation).target)
        assertNull(modelBrowserSelectionPresentation(null, null, AiModelRef("", "")).target)
    }
}
