package com.halo.app.nativeactions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.ui.shell.HaloScreenCatalog

/** No exported executor: buttons open user-controlled Halo flows with current permission checks. */
class HaloNativeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.halo_native_widget)
            fun text(key: String) = context.getString(requireNotNull(com.halo.app.language.uiCatalogue[key]))
            views.setTextViewText(R.id.halo_widget_talk, text("Talk to Halo"))
            views.setTextViewText(R.id.halo_widget_notebook, text("Open Notebook"))
            views.setOnClickPendingIntent(R.id.halo_widget_talk, PendingIntent.getActivity(context, id * 2,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_ASSIST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.halo_widget_notebook, PendingIntent.getActivity(context, id * 2 + 1,
                Intent(context, MainActivity::class.java).putExtra(HaloScreenCatalog.EXTRA_SCREEN, "notebook")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }
}
