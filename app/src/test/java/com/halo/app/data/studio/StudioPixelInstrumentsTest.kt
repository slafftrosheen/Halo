package com.halo.app.data.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class StudioPixelInstrumentsTest {
    @Test fun geometrySupportsFeatherAndCombinedSelections() = runTest {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            val a = StudioArtPoint(.125f, .125f)
            val b = StudioArtPoint(.875f, .875f)
            val rectangle = requireNotNull(studioPixelMask(bitmap, StudioSelectionRequest.Marquee(a, b, false), 0, 32))
            val ellipse = requireNotNull(studioPixelMask(bitmap, StudioSelectionRequest.Marquee(a, b, true), 0, 32))
            val separated = ellipse.combined(rectangle, StudioSelectionCombine.SUBTRACT)
            assertEquals(255, rectangle.at(5, 5))
            assertEquals(0, ellipse.at(5, 5))
            assertEquals(255, separated.at(5, 5))
            assertEquals(0, separated.at(16, 16))
            val polygon = StudioSelectionRequest.Lasso(listOf(a, StudioArtPoint(.875f, .125f), StudioArtPoint(.5f, .875f)))
            val lasso = requireNotNull(studioPixelMask(bitmap, polygon, 0, 32))
            assertEquals(255, lasso.at(16, 16))
            assertEquals(0, lasso.at(26, 26))
            assertEquals(255, lasso.combined(separated, StudioSelectionCombine.ADD).at(5, 5))
            val feathered = requireNotNull(studioPixelMask(bitmap, StudioSelectionRequest.Marquee(a, b, false), 3, 32))
            assertTrue(feathered.at(4, 16) in 1..254)
            assertFalse(ellipse.combined(null, StudioSelectionCombine.SUBTRACT).hasSelection)
        } finally { bitmap.recycle() }
    }

    @Test fun wandDistinguishesConnectedPixelsAndSubjectUsesForeground() = runTest {
        val bitmap = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            for (y in 5..23) for (x in 10..20) bitmap.setPixel(x, y, Color.RED)
            for (y in 7..11) for (x in 2..4) bitmap.setPixel(x, y, Color.RED)
            val wand = StudioSelectionRequest.Wand(StudioArtPoint(.5f, .5f), 0, true)
            val connected = requireNotNull(studioPixelMask(bitmap, wand, 0, 30))
            val all = requireNotNull(studioPixelMask(bitmap, wand.copy(contiguous = false), 0, 30))
            assertEquals(255, connected.at(15, 15))
            assertEquals(0, connected.at(3, 8))
            assertEquals(255, all.at(3, 8))
            val subject = requireNotNull(studioPixelMask(bitmap, StudioSelectionRequest.Subject(0), 0, 30))
            assertEquals(255, subject.at(15, 15))
            assertEquals(0, subject.at(3, 8))
            assertEquals(0, subject.at(0, 0))
            val reselected = requireNotNull(studioPixelMask(bitmap,
                StudioSelectionRequest.Subject(0, StudioArtPoint(.1f, .28f)), 0, 30))
            assertEquals(255, reselected.at(3, 8))
            assertEquals(0, reselected.at(15, 15))
        } finally { bitmap.recycle() }
    }

    @Test fun longSmudgeDraftAppliesTheStrokesAfterTheFirstBatch() = runTest {
        val bitmap = Bitmap.createBitmap(64, 24, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            for (y in 0 until bitmap.height) for (x in 0..31) bitmap.setPixel(x, y, Color.RED)
            val stroke = StudioSmudgeStroke(listOf(StudioArtPoint(.33f, .5f), StudioArtPoint(.75f, .5f)), 100, 8, true)
            val previous = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            try {
                smudgeStudioPixels(previous, List(STUDIO_SMUDGE_BATCH) { stroke }, null, 64)
                val draft = List(STUDIO_SMUDGE_BATCH + 2) { stroke }
                val extra = StudioSmudgeStroke(listOf(StudioArtPoint(.48f, .3f), StudioArtPoint(.75f, .85f)), 95, 12, true)
                assertTrue(smudgeStudioBatches(bitmap, draft + extra, null, 64))
                assertFalse(previous.pixels().contentEquals(bitmap.pixels()))
            } finally { previous.recycle() }
        } finally { bitmap.recycle() }
    }

    @Test fun smudgeBlendsPigmentInsideTheSelectionOnly() = runTest {
        val bitmap = Bitmap.createBitmap(64, 24, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            for (y in 0 until bitmap.height) for (x in 0..31) bitmap.setPixel(x, y, Color.RED)
            val mask = StudioPixelMask(bitmap.width, bitmap.height, ByteArray(64 * 24) {
                if (it % 64 in 23..42) -1 else 0
            })
            val original = bitmap.pixels()
            val stroke = StudioSmudgeStroke(listOf(StudioArtPoint(.33f, .5f), StudioArtPoint(.75f, .5f)), 100, 8, true)
            assertTrue(smudgeStudioPixels(bitmap, listOf(stroke), mask, 64))
            val updated = bitmap.pixels()
            assertTrue(updated.indices.any { it % 64 in 23..42 && updated[it] != original[it] })
            assertTrue(updated.indices.all { it % 64 in 23..42 || updated[it] == original[it] })
        } finally { bitmap.recycle() }
    }
}
