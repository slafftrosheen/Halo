package com.halo.runtime.cognition.ui

import com.halo.sdk.runtime.ScreenContextNode
import com.halo.sdk.runtime.ScreenContextSnapshot
import org.junit.Assert.*
import org.junit.Test

class UiSearchFieldHintsTest {
    private fun field(hint: String, typed: String = "") = ScreenContextNode(
        text = typed, contentDescription = null, className = "android.widget.EditText",
        viewId = "com.example:id/search_field", clickable = true, editable = true,
        bounds = null, enabled = true, token = "fresh-token", labelHint = hint
    )

    @Test fun editableFieldCanBeLocatedByHintButNotByItsTypedContents() {
        val input = field("Search", "private-message-123")
        val snapshot = ScreenContextSnapshot(
            packageName = "com.example", activityName = null, title = null, webUri = null,
            nodes = listOf(input), capturedAtMs = 123L
        )
        assertEquals(input, UiTargetSearch.find(snapshot, "search", editableOnly = true).candidates.single())
        assertTrue("Do not search private field values",
            UiTargetSearch.find(snapshot, "private-message-123").candidates.isEmpty())
    }

    @Test fun deterministicFindRefusesSubstringAndDuplicateControls() {
        val search = field("Search")
        val decorative = ScreenContextNode(
            text = "Search everything", contentDescription = null, className = "TextView",
            viewId = "com.example:id/text_title", clickable = true,
            editable = false, bounds = null, token = "other-token"
        )
        val snap = ScreenContextSnapshot(
            packageName = "com.example", activityName = null, title = null, webUri = null,
            nodes = listOf(search, decorative), capturedAtMs = 123L
        )
        assertEquals(listOf(search), UiTargetSearch.findExact(snap, " Search "))
        assertEquals(emptyList<ScreenContextNode>(), UiTargetSearch.findExact(snap, "Search every"))
        assertEquals(2, UiTargetSearch.findExact(snap.copy(nodes = listOf(search, search.copy(token = "duplicate"))), "search").size)
        assertEquals(emptyList<ScreenContextNode>(), UiTargetSearch.findExact(
            snap.copy(nodes = listOf(search.copy(sensitive = true))), "search"))
        assertEquals(emptyList<ScreenContextNode>(), UiTargetSearch.findExact(
            snap.copy(nodes = listOf(search.copy(token = null))), "search"))
        assertEquals(emptyList<ScreenContextNode>(), UiTargetSearch.findExact(
            snap.copy(nodes = listOf(search.copy(labelHint = "Name", text = "search"))), "search"))
    }

    @Test fun exactFieldHintRanksAheadOfSubstringLabel() {
        val field = field("Search")
        val decorative = ScreenContextNode(
            text = "Search everywhere", contentDescription = null, className = "android.widget.TextView",
            viewId = "com.example:id/title", clickable = false, editable = false, bounds = null
        )
        val snapshot = ScreenContextSnapshot(
            packageName = "com.example", activityName = null, title = null, webUri = null,
            nodes = listOf(decorative, field), capturedAtMs = 123L
        )
        val result = UiTargetSearch.find(snapshot, "Search")
        assertEquals(field, result.candidates.first())
        assertFalse(result.ambiguous)
    }
}
