package com.halo.app.ui.canvas.v13

import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkEditor
import com.halo.sdk.runtime.ink.InkLimits
import com.halo.sdk.runtime.ink.InkPoint
import com.halo.sdk.runtime.ink.InkStroke
import com.halo.sdk.runtime.ink.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkSheetRolloverTest {
    private val full = InkDocument(List(InkLimits.MAX_STROKES) { index ->
        InkStroke(InkTool.LEGACY, listOf(InkPoint(index, index)), "existing-$index")
    }, aspectMilli = 1400)

    @Test fun wholeStrokeMovesToNextSheetAndOriginalStaysIntact() {
        var next: InkDocument? = null
        val editor = InkEditor(full, 1400).apply { continueOnNewSheet = { next = it; true } }
        assertTrue(editor.begin(InkTool.FINGER))
        val points = listOf(InkPoint(200, 300), InkPoint(400, 500, timeMs = 12))
        points.forEach { assertTrue(editor.sample(it)) }
        assertFalse(editor.finish())
        assertEquals(full, editor.document)
        val finished = requireNotNull(next)
        assertEquals(points, finished.strokes.single().points)
        assertEquals(finished, InkCodec.decode(InkCodec.encode(finished)))
        assertFalse(editor.limitReached)
    }

    @Test fun decliningRolloverAndCancellingAStrokeDoNotChangeCurrentSheet() {
        var offers = 0
        val editor = InkEditor(full, 1400).apply { continueOnNewSheet = { offers++; false } }
        assertTrue(editor.begin(InkTool.FINGER))
        editor.sample(InkPoint(10, 10))
        editor.cancel()
        assertEquals(0, offers)
        assertTrue(editor.begin(InkTool.FINGER))
        editor.sample(InkPoint(20, 20))
        assertFalse(editor.finish())
        assertTrue(editor.limitReached)
        assertEquals(1, offers)
        assertEquals(full, editor.document)
    }
}
