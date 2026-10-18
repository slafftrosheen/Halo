package com.halo.app.nativeactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.mini.*
import kotlinx.coroutines.*

/** Launches reviewed Halo flows; broadcasts never execute tools. */
class HaloNativeWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, Intent.ACTION_CONFIGURATION_CHANGED)) {
            val pending = goAsync()
            scope.launch { try { refreshAll(context) } finally { pending.finish() } }
        } else super.onReceive(context, intent)
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, manager.getAppWidgetOptions(it))) }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, views(context, options))
    }
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun refreshAsync(context: Context) { scope.launch { refreshAll(context.applicationContext) } }
        fun refreshAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, HaloNativeWidget::class.java))
                if (ids.isEmpty()) return
                ManualDependencyGraph.init(context)
                ids.forEach { manager.updateAppWidget(it, views(context, manager.getAppWidgetOptions(it))) }
            }.onFailure { android.util.Log.w("HaloWidget", "Could not update Halo widgets", it) }
        }
        internal fun views(context: Context, options: Bundle, label: (String) -> String = { context.nativeUi(it) }): RemoteViews {
            // Modern launchers supply actual portrait/landscape sizes. Render each
            // independently instead of combining unrelated minimum dimensions.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    .distinct().take(16)
                if (sizes.isNotEmpty()) return RemoteViews(sizes.associateWith { size ->
                    viewsForSize(context, size.width.toInt(), size.height.toInt(), label)
                })
            }
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(120)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240).coerceAtLeast(96)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            return if (maxWidth > width || maxHeight > height) RemoteViews(
                viewsForSize(context, maxWidth, height, label),
                viewsForSize(context, width, maxHeight, label),
            ) else viewsForSize(context, width, height, label)
        }

        private fun widgetIcon(context: Context, resource: Int, tint: Int, size: Int): android.graphics.Bitmap {
            val drawable = requireNotNull(androidx.core.content.ContextCompat.getDrawable(context, resource)).mutate()
            drawable.setTint(tint)
            val bitmap = android.graphics.Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(android.graphics.Canvas(bitmap))
            return bitmap
        }

        private fun viewsForSize(context: Context, requestedWidth: Int, requestedHeight: Int, label: (String) -> String): RemoteViews {
            val width = requestedWidth.coerceAtLeast(72)
            val height = requestedHeight.coerceAtLeast(72)
            val narrow = width < 240 || context.resources.configuration.fontScale >= 1.2f
            val wide = !narrow && width >= 240 && height >= 160
            val scheme = nearbyScheme(context)
            val density = context.resources.displayMetrics.density
            return RemoteViews(context.packageName, if (narrow) R.layout.halo_native_widget_narrow else if (wide) R.layout.halo_native_widget_wide else R.layout.halo_native_widget).apply {
                setImageViewBitmap(R.id.halo_widget_background, nearbyBackground((width*density).toInt(), (height*density).toInt(), density, scheme.surfaceContainerLow.toArgb()))
                setImageViewBitmap(R.id.halo_widget_orb, nearbyOrb(context, (48*density).toInt()))
                setContentDescription(R.id.halo_widget_orb, label("Open Mini Halo"))
                setOnClickPendingIntent(R.id.halo_widget_orb, MiniHaloEntry.pending(context, 8000))
                listOf(R.id.halo_widget_title to "halo", R.id.halo_widget_talk to "Talk", R.id.halo_widget_open to "Open Halo").forEach { (id,key) ->
                    setTextViewText(id, label(key)); setTextColor(id, if (id == R.id.halo_widget_talk) scheme.onPrimaryContainer.toArgb() else scheme.onSurface.toArgb())
                }
                setOnClickPendingIntent(R.id.halo_widget_talk, MiniHaloEntry.pending(context, 8000))
                setOnClickPendingIntent(R.id.halo_widget_open, MiniHaloEntry.pending(context, 8001, "home"))
                setTextViewText(R.id.halo_widget_capture, label("Capture"))
                setTextColor(R.id.halo_widget_capture, scheme.primary.toArgb())
                setOnClickPendingIntent(R.id.halo_widget_capture, MiniHaloEntry.pending(context, 8002, draft = "open camera"))
                setOnClickPendingIntent(R.id.halo_widget_title, MiniHaloEntry.pending(context, 8001, "home"))
                if (wide) {
                    setTextViewText(R.id.halo_widget_capture, label("Capture"))
                    setTextViewText(R.id.halo_widget_study, label("Continue learning"))
                    setTextColor(R.id.halo_widget_capture, scheme.primary.toArgb()); setTextColor(R.id.halo_widget_study, scheme.onSurface.toArgb())
                    setOnClickPendingIntent(R.id.halo_widget_capture, MiniHaloEntry.pending(context, 8002, draft = "open camera"))
                    setOnClickPendingIntent(R.id.halo_widget_study, MiniHaloEntry.pending(context, 8003, "study"))
                }
                val actionIds = if (wide) listOf(R.id.halo_widget_talk, R.id.halo_widget_capture, R.id.halo_widget_study)
                    else listOf(R.id.halo_widget_talk, R.id.halo_widget_capture)
                actionIds.forEach { id ->
                    val primary = id == R.id.halo_widget_talk
                    setTextColor(id, if (primary || android.os.Build.VERSION.SDK_INT < 31) scheme.onPrimaryContainer.toArgb() else scheme.onSurface.toArgb())
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        setColorStateList(id, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(
                            if (primary) scheme.primaryContainer.toArgb() else scheme.surfaceContainerHigh.toArgb()))

                    }
                }
                // Tint bitmaps through the supported ImageView API. TextView's
                // compound-drawable tint setter is not a RemoteViews method on
                // launchers such as MIUI, and would reject the entire widget.
                listOf(Triple(R.id.halo_widget_talk_icon, R.drawable.halo_ic_mic, scheme.onPrimaryContainer),
                    Triple(R.id.halo_widget_capture_icon, R.drawable.halo_ic_camera, if (android.os.Build.VERSION.SDK_INT >= 31) scheme.primary else scheme.onPrimaryContainer)).forEach { (id, icon, tint) ->
                    setImageViewBitmap(id, widgetIcon(context, icon, tint.toArgb(), (24*density).toInt()))
                }
                if (wide) setImageViewBitmap(R.id.halo_widget_study_icon,
                    widgetIcon(context, R.drawable.halo_ic_study, (if (android.os.Build.VERSION.SDK_INT >= 31) scheme.primary else scheme.onPrimaryContainer).toArgb(), (24*density).toInt()))
                // Keep complete labels and 48dp targets; short or enlarged widgets hide
                // secondary actions rather than clipping them below the launcher bounds.
                val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 16 * context.resources.displayMetrics.scaledDensity
                    typeface = context.resources.getFont(R.font.atkinson_hyperlegible_next_variable)
                }
                fun needed(key: String, availableWidth: Int): Int {
                    val text = label(key)
                    return maxOf((48*density).toInt(), android.text.StaticLayout.Builder.obtain(
                        text, 0, text.length, textPaint, (availableWidth*density).toInt().coerceAtLeast(1)).build().height)
                }
                setViewVisibility(R.id.halo_widget_open, android.view.View.GONE)
                if (narrow) {
                    // Keep enlarged single-word labels intact. A narrow host can
                    // trade the decorative glyph for text space, never shrink text.
                    listOf(Triple(R.id.halo_widget_talk, R.id.halo_widget_talk_icon, "Talk"),
                        Triple(R.id.halo_widget_capture, R.id.halo_widget_capture_icon, "Capture")).forEach { (id, icon, key) ->
                        if (textPaint.measureText(label(key)) > (width - 72) * density) {
                            setViewVisibility(icon, android.view.View.GONE)
                            setViewPadding(id, (4*density).toInt(), 0, (4*density).toInt(), 0)
                        }
                    }
                    val remaining = ((height - 64)*density).toInt()
                    val talkHeight = needed("Talk", width-72)
                    val titleLayout = android.text.StaticLayout.Builder.obtain(label("halo"), 0, label("halo").length,
                        android.text.TextPaint(textPaint).apply {
                            textSize = 18 * context.resources.displayMetrics.scaledDensity
                            typeface = context.resources.getFont(R.font.sora_variable)
                        }, ((width-80)*density).toInt().coerceAtLeast(1)).build()
                    setViewVisibility(R.id.halo_widget_title, if (titleLayout.lineCount == 1 && titleLayout.height <= 48*density) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_talk_container, if (remaining >= talkHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture_container, if (remaining-talkHeight-4*density >= needed("Capture", width-72)) android.view.View.VISIBLE else android.view.View.GONE)
                } else if (wide) {
                    setViewVisibility(R.id.halo_widget_title, android.view.View.VISIBLE)
                    val remaining = ((height - 88)*density).toInt()
                    val rowHeight = maxOf(needed("Talk", (width-40)/2-48), needed("Capture", (width-40)/2-48))
                    val fitsRow = remaining >= rowHeight
                    setViewVisibility(R.id.halo_widget_talk_container, if (fitsRow) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture_container, if (fitsRow) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_study_container, if (fitsRow && remaining-rowHeight-8*density >= needed("Continue learning", width-80)) android.view.View.VISIBLE else android.view.View.GONE)
                } else {
                    val available = ((height-24)*density).toInt()
                    val captureWidth = minOf(100, (width-72)/2)
                    val talkWidth = (width-80-captureWidth).coerceAtLeast(48)
                    val talkHeight = needed("Talk", talkWidth)
                    val titleHeight = android.text.StaticLayout.Builder.obtain(label("halo"),0,label("halo").length,
                        android.text.TextPaint(textPaint).apply {
                            textSize = 18*context.resources.displayMetrics.scaledDensity
                            typeface = context.resources.getFont(R.font.sora_variable)
                        },
                        (talkWidth*density).toInt()).build().height
                    setViewVisibility(R.id.halo_widget_title, if (available >= talkHeight+titleHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_talk_container, if (available >= talkHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture_container, if (available >= needed("Capture", captureWidth)) android.view.View.VISIBLE else android.view.View.GONE)
                }
            }
        }
    }
}
