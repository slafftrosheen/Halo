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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Actual Android RemoteViews/font measurement; no widget binding or click execution. */
@RunWith(AndroidJUnit4::class)
class WidgetPhysicalLayoutTest {
    @Test fun shortWidgetReapplyRestoresDecorativeIconsAndTextPaddingAfterResize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            fontScale = 1f
            setLocale(Locale.ENGLISH)
        })
        instrumentation.runOnMainSync {
            var root: View? = null
            val density = context.resources.displayMetrics.density
            for (width in listOf(240, 400, 240)) {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 96)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 96)
                }
                val remote = HaloNativeWidget.views(context, options) { key -> uiCatalogue[key]?.let(context::getString) ?: key }
                if (root == null) root = remote.apply(context, FrameLayout(context)) else remote.reapply(context, root)
                val view = requireNotNull(root)
                val w = (width*density).toInt()
                val h = (96*density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                for ((id, icon) in listOf(R.id.halo_widget_talk to R.id.halo_widget_talk_icon,
                    R.id.halo_widget_capture to R.id.halo_widget_capture_icon)) {
                    val text = view.findViewById<TextView>(id)
                    assertTrue(effectivelyVisible(text, view))
                    assertEquals(if (width == 400) View.VISIBLE else View.GONE, view.findViewById<View>(icon).visibility)
                    assertEquals(((if (width == 400) 44 else 4)*density).toInt(), text.paddingStart)
                    assertEquals(1, text.lineCount)
                    assertTargetBounds(text, view, w, h, density, "reapply $width")
                }
            }
        }
    }

    @Test fun translatedWidgetsFitAtNormalAndLargeFontSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val sizes = listOf(120 to 96, 180 to 240, 240 to 96, 240 to 160, 320 to 240)
        for (language in listOf("en", "lv", "ru", "pl")) for (scale in listOf(1f, 1.5f, 2f)) {
            val configuration = Configuration(base.resources.configuration).apply {
                fontScale = scale
                setLocale(Locale.forLanguageTag(language))
            }
            val context = base.createConfigurationContext(configuration)
            for ((width, height) in sizes) for (responsive in listOf(false, true)) {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                    if (responsive) putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        arrayListOf(android.util.SizeF(width.toFloat(), height.toFloat())))
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
                    val orb = root.findViewById<View>(R.id.halo_widget_orb)
                    assertTrue("Every widget size must retain its primary action", effectivelyVisible(orb, root))
                    assertTargetBounds(orb, root, w, h, density, "Orb $language $scale ${width}x$height responsive=$responsive")
                    if (width == 120 && height == 96) {
                        // The actions do not fit below the 48dp header and must be hidden by their owner.
                        assertEquals(View.GONE, root.findViewById<View>(R.id.halo_widget_talk_container).visibility)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.halo_widget_capture_container).visibility)
                    }
                    if (width == 180 && height == 240 && scale == 1f) {
                        assertTrue(effectivelyVisible(root.findViewById(R.id.halo_widget_talk), root))
                        assertTrue(effectivelyVisible(root.findViewById(R.id.halo_widget_capture), root))
                    }
                    for (id in listOf(R.id.halo_widget_talk, R.id.halo_widget_capture, R.id.halo_widget_study)) {
                        val button = root.findViewById<TextView>(id) ?: continue
                        // RemoteViews hides whole containers; an individually VISIBLE child can be
                        // unmeasured under GONE. isShown is also unsuitable for this unattached tree.
                        if (!effectivelyVisible(button, root)) continue
                        val label = "$language font=$scale ${width}x$height responsive=$responsive id=$id"
                        assertTargetBounds(button, root, w, h, density, label)
                        val layout = button.layout
                        assertTrue("Clipped text: $label viewHeight=${button.height} padding=${button.compoundPaddingTop + button.compoundPaddingBottom} lines=${layout?.lineCount} lastBottom=${layout?.let { it.getLineBottom(it.lineCount - 1) }} textSize=${button.textSize}", layout != null &&
                            layout.getLineBottom(layout.lineCount - 1) <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                    }
                }
            }
        }
    }

    private fun effectivelyVisible(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    private fun assertTargetBounds(view: View, root: View, width: Int, height: Int, density: Float, label: String) {
        assertTrue("Small target: $label ${view.width}x${view.height}",
            view.width >= 48 * density - 1 && view.height >= 48 * density - 1)
        val bounds = android.graphics.Rect(0, 0, view.width, view.height)
        (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(view, bounds)
        assertTrue("Clipped target: $label $bounds",
            bounds.left >= 0 && bounds.top >= 0 && bounds.right <= width && bounds.bottom <= height)
    }
}
