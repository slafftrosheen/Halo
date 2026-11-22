package com.halo.app.nativeactions

import android.app.PendingIntent
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.halo.app.R
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydaySnapshot
import com.halo.app.ui.theme.HaloLookMotion
import java.util.Locale

/** Both time and date are Android host clocks. Full motion adds a local minute-length sweep. */
internal object EverydayWidgetClockFace {
    fun render(context: Context, config: EverydayCardConfig, snapshot: EverydaySnapshot, scheme: ColorScheme,
        width: Int, height: Int, locale: Locale, ratio: Float, preview: Boolean, launch: PendingIntent?, ask: PendingIntent?): RemoteViews {
        val muted = scheme.onSurfaceVariant.toArgb()
        val accent = scheme.primary.toArgb()
        val moment = DateFormat.getBestDateTimePattern(locale, "EEEMMMd")
        val moving = !preview && EverydayWidgetMotion.level(context, config.widgetMotion, snapshot.kids) == HaloLookMotion.FULL
        return RemoteViews(context.packageName, R.layout.halo_widget_clock_face).apply {
            setImageViewBitmap(R.id.widget_clock_face_background, EverydayWidgetAppearance.background(context,
                config, scheme, width, height))
            setImageViewBitmap(R.id.widget_clock_face_track, EverydayWidgetGlyphs.bitmap(context,
                R.drawable.halo_widget_clock_track, 108, ColorUtils.setAlphaComponent(muted, 73)))
            setViewVisibility(R.id.widget_clock_face_progress, if (moving) View.VISIBLE else View.GONE)
            if (moving && Build.VERSION.SDK_INT >= 31) {
                setColorStateList(R.id.widget_clock_face_progress, "setIndeterminateTintList", ColorStateList.valueOf(accent))
            }
            setString(R.id.everyday_widget_clock, "setTimeZone", config.zoneId)
            setString(R.id.everyday_widget_clock_date, "setTimeZone", config.zoneId)
            setCharSequence(R.id.everyday_widget_clock_date, "setFormat12Hour", moment)
            setCharSequence(R.id.everyday_widget_clock_date, "setFormat24Hour", moment)
            setTextColor(R.id.everyday_widget_clock, scheme.onSurface.toArgb())
            val clockSize = if (DateFormat.is24HourFormat(context)) 25f else 20f
            setTextViewTextSize(R.id.everyday_widget_clock, TypedValue.COMPLEX_UNIT_SP, clockSize * ratio)
            setTextColor(R.id.everyday_widget_clock_date, muted)
            setTextViewTextSize(R.id.everyday_widget_clock_date, TypedValue.COMPLEX_UNIT_SP, 11f * ratio)
            setViewVisibility(R.id.widget_clock_face_zone, if (config.zoneId != null) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_clock_face_zone, config.cityLabel.ifBlank { config.zoneId.orEmpty() })
            setTextColor(R.id.widget_clock_face_zone, muted)
            setTextViewTextSize(R.id.widget_clock_face_zone, TypedValue.COMPLEX_UNIT_SP, 12f * ratio)
            if (!preview) setOnClickPendingIntent(R.id.widget_clock_face_open, launch)
            setViewVisibility(R.id.widget_clock_face_ask, if (ask == null) View.GONE else View.VISIBLE)
            if (ask != null) setOnClickPendingIntent(R.id.widget_clock_face_ask, ask)
        }
    }
}
