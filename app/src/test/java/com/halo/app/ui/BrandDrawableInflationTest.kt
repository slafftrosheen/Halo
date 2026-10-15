package com.halo.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.halo.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Inflation/draw smoke test for the adaptive vector gradient and wordmark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class BrandDrawableInflationTest {
    @Test fun brandVectorsInflateAndDrawVisiblePixels() {
        val resources = RuntimeEnvironment.getApplication().resources
        listOf(
            R.drawable.ic_launcher_foreground,
            R.drawable.ic_launcher_monochrome,
            R.drawable.halo_wordmark,
        ).plus(R.drawable::class.java.fields.filter { it.name.startsWith("halo_fx_static_") }.map { it.getInt(null) }).forEach { id ->
            val drawable = resources.getDrawable(id, null)
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            var visible = false
            for (y in 0 until 256 step 8) {
                for (x in 0 until 256 step 8) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 0) visible = true
                }
            }
            assertTrue("Brand vector must draw non-transparent pixels: " + id, visible)
        }
    }
}
