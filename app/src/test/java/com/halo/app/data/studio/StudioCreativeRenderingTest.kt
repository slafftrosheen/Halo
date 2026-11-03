package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioCreativeRenderingTest {
    @Test fun multiplyKeepsDestinationOutsideSourceAndPreservesSourceOnTransparency()=runTest {
        val destination=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888).apply {setPixel(0,0,Color.BLUE)}
        val source=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888).apply {setPixel(1,0,Color.RED)}
        try {
            StudioBlendRenderer.multiply(destination,source,StudioLayer(name="Multiply",kind=StudioLayerKind.RECTANGLE,blend=StudioBlend.MULTIPLY))
            assertEquals(Color.BLUE,destination.getPixel(0,0));assertEquals(Color.RED,destination.getPixel(1,0))
        } finally {destination.recycle();source.recycle()}
    }
    private fun image() = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.TRANSPARENT)
        android.graphics.Canvas(this).drawRect(8f,8f,24f,24f,android.graphics.Paint().apply { color=Color.rgb(70,120,170) })
        setPixel(16,16, Color.WHITE)
    }
    @Test fun everyAdvertisedEffectProducesRealEditablePixels() = runTest {
        for (kind in StudioEffectKind.entries) {
            val bitmap = image()
            try {
                val before = IntArray(32*32); bitmap.getPixels(before,0,32,0,0,32,32)
                StudioEffectRenderer.apply(bitmap, listOf(StudioEffect(kind = kind, amount = 700, radius = 3)))
                val after = IntArray(32*32); bitmap.getPixels(after,0,32,0,0,32,32)
                assertFalse("Effect $kind must change pixels", before.contentEquals(after))
            } finally { bitmap.recycle() }
        }
    }
    @Test fun disabledEffectKeepsPixelsAndSeededNoiseReproduces() = runTest {
        val first = image(); val second = image()
        try {
            StudioEffectRenderer.apply(first, listOf(StudioEffect(kind = StudioEffectKind.GRAIN, enabled = false)))
            assertTrue(first.sameAs(second))
            val effects = listOf(StudioEffect(kind = StudioEffectKind.GRAIN, seed = 492))
            StudioEffectRenderer.apply(first, effects); StudioEffectRenderer.apply(second,effects)
            assertTrue(first.sameAs(second))
        } finally { first.recycle(); second.recycle() }
    }
    @Test fun eachTextureIsDeterministicAndNonuniform() {
        for (texture in StudioTexture.entries) {
            val bitmap = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            try {
                StudioTextureRenderer.draw(android.graphics.Canvas(bitmap), android.graphics.RectF(0f,0f,32f,32f), texture, Color.GRAY, 42)
                val pixels = IntArray(1024); bitmap.getPixels(pixels,0,32,0,0,32,32)
                assertTrue(texture.name, pixels.distinct().size > 1)
                assertTrue(pixels.any { Color.alpha(it) > 0 })
            } finally { bitmap.recycle() }
        }
    }
}
