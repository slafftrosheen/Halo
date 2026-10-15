package com.halo.app.ui.screens.notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasEditorBusyPolicyTest {
    private val ready = CanvasEditorUiState(loading = false)

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

    @Test fun clearingBusyStateRestoresEditing() {
        assertTrue(ready.copy(importing = true).copy(importing = false).canEditBoard())
        assertTrue(ready.copy(aiBusy = true).copy(aiBusy = false).canEditBoard())
    }
}
