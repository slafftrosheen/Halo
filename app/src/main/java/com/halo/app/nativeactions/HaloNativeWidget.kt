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
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240).coerceAtLeast(240)
            val wide = width >= 240 && height >= (240 * context.resources.configuration.fontScale).toInt()
            val scheme = nearbyScheme(context)
            val density = context.resources.displayMetrics.density
            return RemoteViews(context.packageName, if (wide) R.layout.halo_native_widget_wide else R.layout.halo_native_widget).apply {
                setImageViewBitmap(R.id.halo_widget_background, nearbyBackground((width*density).toInt(), (height*density).toInt(), density, scheme.surfaceContainerLow.toArgb()))
                setImageViewBitmap(R.id.halo_widget_orb, nearbyOrb((72*density).toInt(), scheme.primary.toArgb()))
                setContentDescription(R.id.halo_widget_orb, label("Open Mini Halo"))
                setOnClickPendingIntent(R.id.halo_widget_orb, MiniHaloEntry.pending(context, 8000))
                listOf(R.id.halo_widget_title to "Halo", R.id.halo_widget_talk to "Talk", R.id.halo_widget_open to "Open Halo").forEach { (id,key) ->
                    setTextViewText(id, label(key)); setTextColor(id, if (id == R.id.halo_widget_talk) scheme.primary.toArgb() else scheme.onSurface.toArgb())
                }
                setOnClickPendingIntent(R.id.halo_widget_talk, MiniHaloEntry.pending(context, 8000))
                setOnClickPendingIntent(R.id.halo_widget_open, MiniHaloEntry.pending(context, 8001, "home"))
                if (!wide && height < 280 && context.resources.configuration.fontScale > 1.3f)
                    setViewVisibility(R.id.halo_widget_open, android.view.View.GONE)
                if (wide) {
                    setTextViewText(R.id.halo_widget_capture, label("Capture"))
                    setTextViewText(R.id.halo_widget_study, label("Continue learning"))
                    setTextColor(R.id.halo_widget_capture, scheme.primary.toArgb()); setTextColor(R.id.halo_widget_study, scheme.onSurface.toArgb())
                    setOnClickPendingIntent(R.id.halo_widget_capture, MiniHaloEntry.pending(context, 8002, draft = "open camera"))
                    setOnClickPendingIntent(R.id.halo_widget_study, MiniHaloEntry.pending(context, 8003, "study"))
                }
            }
        }
    }
}
