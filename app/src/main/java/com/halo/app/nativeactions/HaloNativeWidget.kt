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
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(120)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240).coerceAtLeast(96)
            val wide = width >= 240 && height >= 160
            val scheme = nearbyScheme(context)
            val density = context.resources.displayMetrics.density
            return RemoteViews(context.packageName, if (width < 240) R.layout.halo_native_widget_narrow else if (wide) R.layout.halo_native_widget_wide else R.layout.halo_native_widget).apply {
                setImageViewBitmap(R.id.halo_widget_background, nearbyBackground((width*density).toInt(), (height*density).toInt(), density, scheme.surfaceContainerLow.toArgb()))
                setImageViewBitmap(R.id.halo_widget_orb, nearbyOrb((72*density).toInt(), scheme.primaryContainer.toArgb()))
                setContentDescription(R.id.halo_widget_orb, label("Open Mini Halo"))
                setOnClickPendingIntent(R.id.halo_widget_orb, MiniHaloEntry.pending(context, 8000))
                listOf(R.id.halo_widget_title to "Halo", R.id.halo_widget_talk to "Talk", R.id.halo_widget_open to "Open Halo").forEach { (id,key) ->
                    setTextViewText(id, label(key)); setTextColor(id, if (id == R.id.halo_widget_talk) scheme.primary.toArgb() else scheme.onSurface.toArgb())
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
                // Keep complete labels and 48dp targets; short or enlarged widgets hide
                // secondary actions rather than clipping them below the launcher bounds.
                val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 16 * context.resources.displayMetrics.scaledDensity
                }
                fun needed(key: String, availableWidth: Int): Int {
                    val text = label(key)
                    return maxOf((48*density).toInt(), android.text.StaticLayout.Builder.obtain(
                        text, 0, text.length, textPaint, (availableWidth*density).toInt().coerceAtLeast(1)).build().height)
                }
                setViewVisibility(R.id.halo_widget_open, android.view.View.GONE)
                if (width < 240) {
                    val remaining = ((height - 72)*density).toInt()
                    val talkHeight = needed("Talk", width-24)
                    val titleHeight = needed("Halo", width-80)
                    setViewVisibility(R.id.halo_widget_title, if (titleHeight <= 48*density) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_talk, if (remaining >= talkHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture, if (remaining-talkHeight >= needed("Capture", width-24)) android.view.View.VISIBLE else android.view.View.GONE)
                } else if (wide) {
                    setViewVisibility(R.id.halo_widget_title, android.view.View.VISIBLE)
                    val remaining = ((height - 88)*density).toInt()
                    val rowHeight = maxOf(needed("Talk", (width-32)/2), needed("Capture", (width-32)/2))
                    val fitsRow = remaining >= rowHeight
                    setViewVisibility(R.id.halo_widget_talk, if (fitsRow) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture, if (fitsRow) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_study, if (fitsRow && remaining-rowHeight >= needed("Continue learning", width-32)) android.view.View.VISIBLE else android.view.View.GONE)
                } else {
                    val available = ((height-24)*density).toInt()
                    val captureWidth = minOf(100, (width-72)/2)
                    val talkWidth = (width-80-captureWidth).coerceAtLeast(48)
                    val talkHeight = needed("Talk", talkWidth)
                    val titleHeight = android.text.StaticLayout.Builder.obtain(label("Halo"),0,label("Halo").length,
                        android.text.TextPaint(textPaint).apply { textSize = 18*context.resources.displayMetrics.scaledDensity },
                        (talkWidth*density).toInt()).build().height
                    setViewVisibility(R.id.halo_widget_title, if (available >= talkHeight+titleHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_talk, if (available >= talkHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture, if (available >= needed("Capture", captureWidth)) android.view.View.VISIBLE else android.view.View.GONE)
                }
            }
        }
    }
}
