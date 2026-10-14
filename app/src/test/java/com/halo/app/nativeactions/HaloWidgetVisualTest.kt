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
    private fun capture(width: Int, height: Int, dark: Boolean, scale: Float = 1f) {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        val context: Context = activity
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(android.content.res.Configuration(context.resources.configuration).apply { fontScale = scale }, context.resources.displayMetrics)
        context.getSharedPreferences("halo_appearance",Context.MODE_PRIVATE).edit().putString("mode",if(dark) "DARK" else "LIGHT").commit()
        context.getSharedPreferences("halo_palettes",Context.MODE_PRIVATE).edit().putString(if(dark) "dark.accent" else "light.accent", "LILAC").commit()
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height) }
        val remote = HaloNativeWidget.views(context,options) { key -> context.getString(requireNotNull(com.halo.app.language.uiCatalogue[key]) { "Missing widget translation: $key" }) }
        val view = remote.apply(context,FrameLayout(context))
        activity.setContentView(view, android.view.ViewGroup.LayoutParams(width,height))
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
        assertNotNull(view.findViewById<View>(R.id.halo_widget_orb))
        listOf(R.id.halo_widget_talk,R.id.halo_widget_open,R.id.halo_widget_capture,R.id.halo_widget_study).forEach { id ->
            view.findViewById<View>(id)?.takeIf { it.visibility == View.VISIBLE }?.let { control ->
                val bounds = android.graphics.Rect(); control.getDrawingRect(bounds); (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(control,bounds)
                assertTrue("Visible widget action clips outside host: $bounds / $height", bounds.bottom <= height)
            }
        }
        view.captureRoboImage()
        controller.pause().stop().destroy()
    }
    @Test fun compactLight() = capture(180,240,false)
    @Test fun wideDark() = capture(320,240,true)
    @Test fun existingShortWidget() = capture(320,164,false)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp-mdpi") fun compactLargeLatvian() = capture(180,240,true,2f)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp-mdpi") fun wideLargeRussian() = capture(320,480,false,2f)
}
