package com.halo.app.nativeactions

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.R
import com.halo.app.language.uiCatalogue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Actual Android RemoteViews/font measurement; no widget binding or click execution. */
@RunWith(AndroidJUnit4::class)
class WidgetPhysicalLayoutTest {
    @Test fun translatedWidgetsFitAtNormalAndLargeFontSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val sizes = listOf(120 to 96, 180 to 240, 240 to 96, 240 to 160, 320 to 240)
        for (language in listOf("en", "lv", "ru")) for (scale in listOf(1f, 1.5f, 2f)) {
            val configuration = Configuration(base.resources.configuration).apply {
                fontScale = scale
                setLocale(Locale.forLanguageTag(language))
            }
            val context = base.createConfigurationContext(configuration)
            for ((width, height) in sizes) {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                }
                val remote = HaloNativeWidget.views(context, options) { key ->
                    uiCatalogue[key]?.let(context::getString) ?: key
                }
                instrumentation.runOnMainSync {
                    val root = remote.apply(context, FrameLayout(context))
                    val density = context.resources.displayMetrics.density
                    val w = (width * density).toInt()
                    val h = (height * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                    for (id in listOf(R.id.halo_widget_talk, R.id.halo_widget_capture, R.id.halo_widget_study)) {
                        val button = root.findViewById<TextView>(id) ?: continue
                        if (button.visibility != View.VISIBLE) continue
                        val label = "$language font=$scale ${width}x$height id=$id"
                        assertTrue("Small target: $label", button.width >= 48 * density - 1 && button.height >= 48 * density - 1)
                        val bounds = android.graphics.Rect(0, 0, button.width, button.height)
                        (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(button, bounds)
                        assertTrue("Clipped target: $label $bounds", bounds.left >= 0 && bounds.top >= 0 && bounds.right <= w && bounds.bottom <= h)
                        val layout = button.layout
                        assertTrue("Clipped text: $label", layout != null &&
                            layout.getLineBottom(layout.lineCount - 1) <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                    }
                }
            }
        }
    }
}
