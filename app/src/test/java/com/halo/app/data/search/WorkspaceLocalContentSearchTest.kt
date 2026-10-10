package com.halo.app.data.search

import com.halo.app.data.notebook.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceLocalContentSearchTest {
    @Test fun multilingualTextIsCaseAndUnicodeNormalizationAware() {
        assertTrue(WorkspaceLocalContentSearch.matches("Русский ответ", "РУССКИЙ"))
        assertTrue(WorkspaceLocalContentSearch.matches("Latviešu atbilde", "LATVIEŠU"))
        assertTrue(WorkspaceLocalContentSearch.matches("ābols", "a\u0304bols"))
    }
    @Test fun boardAndSourceFiltersReturnOnlyActualPlainTextHits() {
        val rows = listOf(CanvasSearchRow("board", "node", "Board", "NOTE", "note", "NOTE", "Transcript", "Exact words", "meeting"))
        val hit = WorkspaceLocalContentSearch.cards(rows, WorkspaceSearchQuery("meeting", canvasId = "board", sourceTypes = setOf(WorkspaceObjectType.NOTE))).single()
        assertEquals("node", hit.nodeId)
        assertTrue(WorkspaceLocalContentSearch.cards(rows, WorkspaceSearchQuery("meeting", canvasId = "other")).isEmpty())
        assertTrue(WorkspaceLocalContentSearch.cards(rows, WorkspaceSearchQuery("meeting", sourceTypes = setOf(WorkspaceObjectType.FILE))).isEmpty())
    }
    @Test fun encodedPayloadsAndCredentialsNeverBecomeSearchHits() {
        val rows = listOf(CanvasSearchRow("board", "node", "Board", "TEXT", null, null, null, "sketch:v1|10,20"))
        assertTrue(WorkspaceLocalContentSearch.cards(rows, WorkspaceSearchQuery("sketch")).isEmpty())
        assertFalse(WorkspaceLocalContentSearch.safe("password: super-secret-credential"))
        assertEquals("Source unavailable", CanvasFileMarkupCodec.displayTitle("halo-markup:v1|corrupt"))
    }
}
