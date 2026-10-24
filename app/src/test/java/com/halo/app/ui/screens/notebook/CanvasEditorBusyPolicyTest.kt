package com.halo.app.ui.screens.notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasEditorBusyPolicyTest {
    private val ready = CanvasEditorUiState(loading = false)
    @Test fun activeContextNeverProjectsBusyOrPendingDraftSelection() {
        assertTrue(ready.toCurrentCanvasContext("board", false) != null)
        assertTrue(ready.toCurrentCanvasContext("board", true) == null)
        listOf(ready.copy(loading = true), ready.copy(missing = true), ready.copy(addingNode = true),
            ready.copy(importing = true), ready.copy(imageToolBusy = true), ready.copy(aiBusy = true),
            ready.copy(exporting = true), ready.copy(transcribing = true), ready.copy(aiDraft = CanvasAiDraft("Review", null, null, "Pending")),
            ready.copy(textDraft = CanvasRecognizedDraft("card", "SKETCH", "Pending", "Original"))).forEach {
            assertTrue(it.toCurrentCanvasContext("board", false) == null)
        }
    }

    @Test fun readyCanvasAcceptsEditing() {
        assertTrue(ready.canEditBoard())
    }

    @Test fun everyMutatingOrUnavailableStateBlocksEditing() {
        listOf(
            ready.copy(loading = true),
            ready.copy(missing = true),
            ready.copy(addingNode = true),
            ready.copy(importing = true),
            ready.copy(imageToolBusy = true),
            ready.copy(aiBusy = true),
            ready.copy(exporting = true),
        ).forEach { assertFalse(it.canEditBoard()) }
    }

    @Test fun newGenerationCannotOverwritePendingAiDraft() {
        val draft = CanvasAiDraft("Review", nodeId = null, original = null, text = "Suggestion")
        val pending = ready.copy(aiDraft = draft)
        assertTrue(pending.canEditBoard()) // Manual workspace controls remain available.
        assertFalse(pending.canRequestAiDraft())
        assertTrue(pending.canRequestAiDraft(replacingDraft = true))
        assertFalse(pending.copy(aiBusy = true).canRequestAiDraft(replacingDraft = true))
    }

    @Test fun clearingBusyStateRestoresEditing() {
        assertTrue(ready.copy(importing = true).copy(importing = false).canEditBoard())
        assertTrue(ready.copy(aiBusy = true).copy(aiBusy = false).canEditBoard())
    }
}
