package com.halo.sdk.runtime.ink

import org.junit.Assert.*
import org.junit.Test

class InkEncodingCapacityTest {
    private fun points() = List(InkLimits.MAX_POINTS_PER_STROKE) { index ->
        InkPoint(1000 - index % 2, 1000, 3_000_000 + index, 1000, 1571, -3142, 65535)
    }

    private fun largeDocument() = InkDocument(List(3) { index ->
        InkStroke(InkTool.STYLUS, points(), "stroke-$index")
    })

    @Test fun wireCapacityRejectsContactBeforeChangingDocumentOrRedo() {
        val original = largeDocument()
        assertNotNull(InkCodec.decode(InkCodec.encode(original)))
        val editor = InkEditor(original)
        assertTrue(editor.clear())
        assertTrue(editor.undo())
        assertTrue(editor.canRedo)
        assertFalse(editor.canUndo)

        assertTrue(editor.begin(InkTool.STYLUS))
        points().forEach { assertTrue(editor.sample(it)) }
        // This contact is within the point limits but cannot fit the persisted format.
        InkLimits.requireValid(editor.preview())
        assertFalse(editor.finish())
        assertTrue(editor.limitReached)
        assertFalse(editor.active)
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(editor.canRedo)
        assertTrue(editor.redo())
        assertTrue(editor.document.strokes.isEmpty())
    }

    @Test fun exactWireBoundaryAndOneCharacterOverflow() {
        val small = largeDocument()
        val base = InkCodec.encode(small)
        // Add maximum-width points (40 characters including separators) and pad the last
        // stroke ID to reach the exact boundary independently of the length estimator.
        val remaining = InkLimits.MAX_ENCODED_CHARS - base.length
        val pointCount = (remaining - 4) / 40
        val idLength = remaining - (4 + pointCount * 40 - 1)
        val boundary = small.copy(strokes = small.strokes + InkStroke(
            InkTool.STYLUS, List(pointCount) { InkPoint(1000, 1000, 3_000_000, 1000, 1571, -3142, 65535) },
            "x".repeat(idLength)))
        val encoded = InkCodec.encode(boundary)
        assertEquals(InkLimits.MAX_ENCODED_CHARS, encoded.length)
        assertEquals(boundary, InkCodec.decode(encoded))
        val overflow = boundary.copy(strokes = boundary.strokes.dropLast(1) +
            boundary.strokes.last().copy(strokeId = "x".repeat(idLength + 1)))
        assertThrows(IllegalArgumentException::class.java) { InkCodec.encode(overflow) }
    }

    @Test fun predictedSizeMatchesAllToolsOptionalAxesAndSignedValues() {
        val documents = listOf(InkDocument(), InkDocument(aspectMilli = 0), InkDocument(
            InkTool.entries.mapIndexed { index, tool ->
                InkStroke(tool, if (tool == InkTool.LEGACY) listOf(InkPoint(0, 1000)) else listOf(
                    InkPoint(0, 1), InkPoint(1000, 999, 3_600_000, 0, 0, -3142, 65535),
                    InkPoint(1, 0, 3_600_000, 1000, 1571, 3142, 0)), "id_$index")
            }, 8000))
        documents.forEach { assertEquals(InkCodec.encode(it).length, InkCodec.encodedLength(it)) }
    }
}
