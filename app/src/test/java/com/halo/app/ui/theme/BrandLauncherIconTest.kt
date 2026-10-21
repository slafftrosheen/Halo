package com.halo.app.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import com.halo.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandLauncherIconTest {
    @Test fun adaptiveIconInflatesWithNightInkBackground() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        val background = (icon as AdaptiveIconDrawable).background
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        background.setBounds(0, 0, 108, 108)
        background.draw(Canvas(bitmap))
        assertEquals(0xff060330.toInt(), bitmap.getPixel(54, 54))
    }
}
