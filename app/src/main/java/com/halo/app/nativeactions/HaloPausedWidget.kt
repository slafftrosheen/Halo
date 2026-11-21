package com.halo.app.nativeactions

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.halo.app.R
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.ui.mini.MiniHaloEntry

internal fun haloPausedWidget(context: Context, id: Int?): RemoteViews {
    val scheme = EverydayWidgetAppearance.scheme(context, EverydayCardConfig(EverydayCardKind.QUICK_ACTIONS))
    return RemoteViews(context.packageName, R.layout.halo_widget_paused).apply {
        val label = context.nativeUi("Paused")
        setTextViewText(R.id.halo_paused_widget_label, label)
        setTextColor(R.id.halo_paused_widget_label, scheme.onSurface.toArgb())
        setContentDescription(R.id.halo_paused_widget_root, label)
        setOnClickPendingIntent(R.id.halo_paused_widget_root,
            MiniHaloEntry.pending(context, id ?: 8000, "pause"))
    }
}
