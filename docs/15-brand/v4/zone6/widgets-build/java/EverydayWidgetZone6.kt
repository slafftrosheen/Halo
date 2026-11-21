package com.halo.app.nativeactions

/*
 * halo Zone 6 widget face — applied at the END of EverydayWidgetViews.render() (after the existing
 * measured layout decided visibility). Presentation only: no fetch, no wake lock (the provider contract stays
 * "OS-bounded cached repaint"). Sources refresh inside halo; the widget only offers the one honest fix.
 *
 * Wiring (3 small edits in EverydayWidgetViews.kt):
 *   1. nearbyBackground(..)  -> nearbyBackground(.., radiusDp = 28f, glow = scheme.primary.toArgb())   (HaloNearby.kt gets 2 optional params)
 *   2. title: 14sp, bold, onSurfaceVariant (it is a label now; the value/clock carries the hierarchy)
 *   3. replace the status/configure block at the bottom with:  Zone6WidgetFace.apply(views, context, instance, face, snapshot, scheme, ratio, remaining, label)
 */

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.halo.app.R
import com.halo.app.everyday.presentation.*
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.mini.MiniHaloEntry
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object Zone6WidgetFace {

    enum class Freshness { FRESH, OLD, UNREACHABLE, NO_SOURCE }

    /** Plain words only. No "Local/Power/Cached/attribution stamps" on the face (those move to the content description). */
    fun freshness(face: EverydayCardFace, kind: EverydayCardKind): Freshness = when {
        face.observedAt == null && face.rows.isEmpty() && kind in setOf(EverydayCardKind.HEADLINES, EverydayCardKind.WEATHER) -> Freshness.NO_SOURCE
        face.lastRefreshFailed -> Freshness.UNREACHABLE
        face.stale -> Freshness.OLD
        else -> Freshness.FRESH
    }

    fun statusText(context: Context, face: EverydayCardFace, kind: EverydayCardKind, snapshot: EverydaySnapshot, locale: Locale, sourceCount: Int?): String {
        val at = face.observedAt ?: return face.status
        val age = Duration.between(at, snapshot.now)
        val whenText = if (age.toHours() < 12) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(snapshot.zone).format(at)
            else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(snapshot.zone).format(at)
        return when (freshness(face, kind)) {
            Freshness.UNREACHABLE -> context.getString(R.string.everyday_widget_unreachable, whenText)
            Freshness.OLD -> context.getString(R.string.everyday_widget_old, whenText)
            else -> if (sourceCount != null && sourceCount > 1) context.getString(R.string.everyday_widget_updated_sources, whenText, sourceCount)
                    else context.getString(R.string.everyday_widget_updated, whenText)
        }
    }

    fun apply(views: RemoteViews, context: Context, instance: EverydayWidgetInstance, face: EverydayCardFace, snapshot: EverydaySnapshot,
              scheme: androidx.compose.material3.ColorScheme, ratio: Float, remainingDp: Int, locale: Locale, fullDescription: String) {
        val dark = androidx.core.graphics.ColorUtils.calculateLuminance(scheme.surface.toArgb()) < .5
        val amber = if (dark) 0xFFF4C56A.toInt() else 0xFF805100.toInt()
        val f = freshness(face, instance.config.kind)

        // 1 · ask halo about this (opens Mini halo with a typed draft — never starts the mic)
        val title = face.title
        views.setViewVisibility(R.id.everyday_widget_ask, View.VISIBLE)
        views.setOnClickPendingIntent(R.id.everyday_widget_ask,
            MiniHaloEntry.pending(context, 9100 + instance.id, "mini_halo", context.getString(R.string.everyday_widget_ask_draft, title)))
        views.setContentDescription(R.id.everyday_widget_ask, context.getString(R.string.everyday_widget_ask) + " · " + title)

        // 2 · label-style title
        views.setTextColor(R.id.everyday_widget_title, scheme.onSurfaceVariant.toArgb())
        views.setTextViewTextSize(R.id.everyday_widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, 14f * ratio)

        // 3 · official alert pill (needs EverydayCardRow.attention — see WIDGETS_BUILD.md §4)
        val alert = face.rows.firstOrNull { it.attention }
        views.setViewVisibility(R.id.everyday_widget_alert, if (alert != null && instance.config.kind == EverydayCardKind.DASHBOARD) View.VISIBLE else View.GONE)
        alert?.let { views.setTextViewText(R.id.everyday_widget_alert, it.title) }

        // 4 · freshness line, amber only when old / unreachable
        views.setTextViewText(R.id.everyday_widget_status, statusText(context, face, instance.config.kind, snapshot, locale, sourceCountFor(instance, snapshot)))
        views.setTextColor(R.id.everyday_widget_status, if (f == Freshness.FRESH) scheme.onSurfaceVariant.toArgb() else amber)
        views.setTextViewTextSize(R.id.everyday_widget_status, android.util.TypedValue.COMPLEX_UNIT_SP, 13f * ratio)

        // 5 · exactly one fix, only when needed
        val fix = when (f) { Freshness.NO_SOURCE -> R.string.everyday_widget_choose_source; Freshness.UNREACHABLE -> R.string.everyday_widget_check_source; else -> null }
        views.setViewVisibility(R.id.everyday_widget_configure, if (fix != null && remainingDp >= 54) View.VISIBLE else View.GONE)
        fix?.let {
            views.setTextViewText(R.id.everyday_widget_configure, context.getString(it))
            views.setTextColor(R.id.everyday_widget_configure, scheme.primary.toArgb())
            views.setOnClickPendingIntent(R.id.everyday_widget_configure, MiniHaloEntry.pending(context, 9300 + instance.id, EverydayRoutes.SOURCES))
        }

        // 6 · the whole face reads as one sentence for TalkBack (technical stamps live here, not on the face)
        views.setContentDescription(R.id.everyday_widget_root, fullDescription)
    }

    private fun sourceCountFor(instance: EverydayWidgetInstance, snapshot: EverydaySnapshot): Int? =
        if (instance.config.kind == EverydayCardKind.DASHBOARD) snapshot.sources.size.takeIf { it > 0 } else null

}
