package com.halo.app.nativeactions

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class HaloWidgetVisualTest {
    private fun capture(width: Int, height: Int, dark: Boolean, scale: Float = 1f, sizes: List<android.util.SizeF> = emptyList()) {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        val context: Context = activity
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(android.content.res.Configuration(context.resources.configuration).apply { fontScale = scale }, context.resources.displayMetrics)
        context.getSharedPreferences("halo_appearance",Context.MODE_PRIVATE).edit().putString("mode",if(dark) "DARK" else "LIGHT").commit()
        context.getSharedPreferences("halo_palettes",Context.MODE_PRIVATE).edit().putString(if(dark) "dark.accent" else "light.accent", "LILAC").commit()
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height) }
        if (sizes.isNotEmpty()) options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(sizes))
        val remote = HaloNativeWidget.views(context,options) { key -> context.getString(requireNotNull(com.halo.app.language.uiCatalogue[key]) { "Missing widget translation: $key" }) }
        val view = remote.apply(context,FrameLayout(context))
        activity.setContentView(view, android.view.ViewGroup.LayoutParams(width,height))
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
        assertNotNull(view.findViewById<View>(R.id.halo_widget_orb))
        view.findViewById<android.widget.TextView>(R.id.halo_widget_title)?.takeIf { it.isShown }?.let {
            assertEquals("Halo's name must not wrap in a small widget", 1, it.lineCount)
        }
        listOf(R.id.halo_widget_talk,R.id.halo_widget_open,R.id.halo_widget_capture,R.id.halo_widget_study).forEach { id ->
            view.findViewById<View>(id)?.takeIf { it.isShown }?.let { control ->
                val bounds = android.graphics.Rect(); control.getDrawingRect(bounds); (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(control,bounds)
                assertTrue("Visible widget action clips outside host: $bounds / $height", bounds.bottom <= height)
                assertTrue("Visible widget action clips horizontally: $bounds / $width", bounds.left >= 0 && bounds.right <= width)
                assertTrue("Widget action is smaller than 48dp: $bounds", bounds.height() >= 48)
                if (control is android.widget.TextView && control.text.none { it.isWhitespace() }) {
                    assertEquals("A single-word widget label must stay whole: ${control.text}", 1, control.lineCount)
                }
            }
        }
        if (width == 150 && height == 164 && scale == 1f) {
            assertTrue("The existing narrow widget must retain Talk", view.findViewById<View>(R.id.halo_widget_talk).isShown)
            assertTrue("The existing narrow widget must retain Capture", view.findViewById<View>(R.id.halo_widget_capture).isShown)
        }
        view.captureRoboImage()
        controller.pause().stop().destroy()
    }
    @Test fun existingNarrowLauncherWidget() = capture(150,164,false)
    @Test fun compactLight() = capture(180,240,false)
    @Test fun wideDark() = capture(320,240,true)
    @Test fun existingShortWidget() = capture(320,164,false)
    @Test fun minimumNarrowWidget() = capture(120,96,false)
    @Test fun launcherSuppliedSizesKeepTheSmallestLayoutUsable() = capture(120,96,false,
        sizes = listOf(android.util.SizeF(120f,96f), android.util.SizeF(320f,240f)))
    @Test fun minimumShortWidgetLargeText() = capture(320,96,true,2f)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp-mdpi") fun narrowLargeLatvian() = capture(120,180,true,2f)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp-mdpi") fun compactLargeLatvian() = capture(180,240,true,2f)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp-mdpi") fun wideLargeRussian() = capture(320,480,false,2f)
}
