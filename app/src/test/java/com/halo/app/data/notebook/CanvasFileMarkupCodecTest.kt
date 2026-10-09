package com.halo.app.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasFileMarkupCodecTest {
    private val first = CanvasSketchCodec.encode(listOf(
        listOf(CanvasSketchPoint(10, 20), CanvasSketchPoint(300, 400))
    ))
    private val second = CanvasSketchCodec.encode(listOf(
        listOf(CanvasSketchPoint(1000, 1000))
    ))

    @Test fun legacyTitleSurvivesNewPageMarks() {
        val updated = CanvasFileMarkupCodec.merge("contract.pdf", mapOf(2 to first, 4 to second))
        val decoded = CanvasFileMarkupCodec.decode(updated)
        assertEquals("contract.pdf", decoded.title)
        assertEquals(mapOf(2 to first, 4 to second), decoded.pages)
    }

    @Test fun clearRemovesOnePageAndRestoresLegacyWhenEmpty() {
        val updated = CanvasFileMarkupCodec.merge("image.jpg", mapOf(1 to first))
        assertEquals("image.jpg", CanvasFileMarkupCodec.merge(updated, mapOf(1 to "")))
    }

    @Test fun editsPreserveOtherPageInk() {
        val firstSave = CanvasFileMarkupCodec.merge("notes.pdf", mapOf(1 to first, 3 to second))
        val secondSave = CanvasFileMarkupCodec.merge(firstSave, mapOf(3 to first))
        assertEquals(first, CanvasFileMarkupCodec.decode(secondSave).pages[1])
        assertEquals(first, CanvasFileMarkupCodec.decode(secondSave).pages[3])
    }

    @Test fun corruptMarkupIsReadOnlyInsteadOfSilentlyOverwritten() {
        val corrupt = "halo-markup:v1|broken:payload:xyz"
        assertEquals(emptyMap<Int, String>(), CanvasFileMarkupCodec.decode(corrupt).pages)
        assertTrue(runCatching { CanvasFileMarkupCodec.merge(corrupt, mapOf(1 to first)) }.isFailure)
    }

    @Test fun rejectsUnsupportedPageNumbersAndInvalidInk() {
        assertTrue(runCatching { CanvasFileMarkupCodec.merge("doc.pdf", mapOf(0 to first)) }.isFailure)
        assertTrue(runCatching { CanvasFileMarkupCodec.merge("doc.pdf", mapOf(1 to "garbage")) }.isFailure)
    }

    @Test fun boundedPageCount() {
        val tooMany = (1..CanvasFileMarkupCodec.MAX_PAGES + 1).associateWith { first }
        assertTrue(runCatching { CanvasFileMarkupCodec.merge("doc.pdf", tooMany) }.isFailure)
    }
}
