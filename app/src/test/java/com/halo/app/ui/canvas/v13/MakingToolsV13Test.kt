package com.halo.app.ui.canvas.v13

import com.halo.app.ui.notes.v13.NoteDrawing
import com.halo.app.ui.scan.v13.PageKind
import com.halo.app.ui.scan.v13.ScanAction
import com.halo.app.ui.scan.v13.ScanNext
import com.halo.app.ui.studio.v13.SmudgeQueue
import com.halo.app.ui.translate.v13.FaceToFace
import com.halo.app.ui.translate.v13.Side
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkLimits
import com.halo.sdk.runtime.ink.InkPoint
import com.halo.sdk.runtime.ink.InkStroke
import com.halo.sdk.runtime.ink.InkTool
import org.junit.Assert.*
import org.junit.Test

class MakingToolsV13Test {
    private fun stroke(n: Int = 4) = InkStroke(InkTool.STYLUS, (0 until n).map { InkPoint(it % 1000, 10, it) })
    private fun sheets(n: Int) = DrawingSheets("g", (1..n).map { "s$it" })

    @Test fun theNextStrokeAfterAFullSheetLandsOnSheetTwo() {
        val full = InkDocument(List(InkLimits.MAX_STROKES) { stroke() })
        assertEquals(1.0, InkSheets.fullness(full), 0.0)
        assertTrue(InkSheets.shouldPrepareNext(full))
        val p = InkSheets.place(full, stroke(), sheets(1))
        assertTrue(p is InkSheets.Placement.NextSheet)
        assertEquals(1, (p as InkSheets.Placement.NextSheet).next.strokes.size)
        InkLimits.requireValid(p.next)
        assertTrue(InkSheets.place(InkDocument(), stroke(), sheets(1)) is InkSheets.Placement.SameSheet)
        assertTrue(InkSheets.place(full, stroke(), sheets(InkSheets.MAX_SHEETS)) is InkSheets.Placement.Full)
    }

    @Test fun oldSixtyFourLineSketchesOpenAsValidDocuments() {
        val v1 = List(64) { i -> listOf(i to 0, i to 1000) }
        val doc = InkSheets.upgradeV1(v1)
        assertEquals(64, doc.strokes.size)
        InkLimits.requireValid(doc)
    }

    @Test fun smudgeNeverStopsAndAppliesEveryTwelve() {
        var applied = 0
        val q = SmudgeQueue { applied += it.size }
        repeat(30) { q.add(listOf(0f to 0f, 1f to 1f)); assertTrue(q.canDraw) }
        assertEquals(24, applied); assertEquals(6, q.pendingCount)
        q.flush(); assertEquals(30, applied)
    }

    @Test fun scanOffersFitThePage() {
        assertEquals(PageKind.FORM, ScanNext.kind("Name:\nDate of birth:\nPhone:"))
        assertEquals(PageKind.RECEIPT, ScanNext.kind("Maize 1,20 €\nPiens 0,99 €\nKOPĀ 2,19 €"))
        assertEquals(ScanAction.READ_IT_TO_ME, ScanNext.offers(PageKind.TEXT, false, false).first())
        assertTrue(ScanNext.offers(PageKind.OBJECT, kids = true, approvedKidsSource = false).isEmpty())
        assertTrue(ScanNext.offers(PageKind.TEXT, false, false).size <= 5)
    }

    @Test fun noteDrawingRefsRoundTrip() {
        val r = NoteDrawing.ref("board-1", "node-9")
        assertEquals("board-1" to "node-9", NoteDrawing.parse(r))
        assertNull(NoteDrawing.parse("note:abc"))
        assertNull(NoteDrawing.parse("canvas-sketch:a/b/c"))
    }

    @Test fun faceToFaceOneSideAtATime() {
        val f = FaceToFace("lv", "en")
        assertTrue(f.hold(Side.FAR)); assertFalse(f.hold(Side.NEAR))
        val t = f.release("Two to Tartu") { s, from, to -> "$from>$to:$s" }
        assertEquals("en>lv:Two to Tartu", t!!.translated)
        assertNull(f.listening)
    }
}
