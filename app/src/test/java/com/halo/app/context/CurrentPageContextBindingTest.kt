package com.halo.app.context

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CurrentPageContextBindingTest {
    @Test fun explicitBindingReadsExactVersionAndUsesCanonicalSelection() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val binding = CurrentPageContextBinding({ "canvas:7" }, { id, version -> calls += id to version })
        assertEquals("canvas:7", binding.bind("notebook/canvas/{canvasId}", "board", "canvas:7"))
        assertEquals(listOf("board" to "canvas:7"), calls)
    }
    @Test fun aNewExplicitSelectionRefreshesRevisionAfterEditingTheSamePage() = runBlocking {
        var currentVersion = "canvas:1"
        val selected = mutableListOf<String>()
        val binding = CurrentPageContextBinding({ currentVersion }, { _, version -> selected += version })
        binding.bind("notebook/canvas/{canvasId}", "board")
        currentVersion = "canvas:2"
        binding.bind("notebook/canvas/{canvasId}", "board")
        assertEquals(listOf("canvas:1", "canvas:2"), selected)
    }
    @Test fun unsupportedRoutesMismatchedIdsAndStaleVersionsNeverSelect() = runBlocking {
        val binding = CurrentPageContextBinding({ "new" }, { _, _ -> fail("No selection may be published") })
        listOf("study", "notebook/study/session", "studio", "home", "notebook", "notebook/note/other").forEach {
            assertTrue(it, runCatching { binding.bind(it, "note", null) }.isFailure)
        }
        assertTrue(runCatching { binding.bind("notebook/note/note", "note", "old") }.isFailure)
    }
    @Test fun cancelledPageCannotPublishAfterSuspendedSourceRead() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var selected = false
        val binding = CurrentPageContextBinding({ entered.complete(Unit); awaitCancellation() }, { _, _ -> selected = true })
        val job = launch { binding.bind("notebook/note/{noteId}", "note", null) }
        entered.await(); job.cancelAndJoin()
        assertFalse(selected)
    }
}
