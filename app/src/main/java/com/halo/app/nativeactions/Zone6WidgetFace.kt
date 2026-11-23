package com.halo.app.nativeactions

import android.content.Context
import com.halo.app.R
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.presentation.*
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Uses only the current, user-selected cached snapshot; a widget paint never fetches data. */
internal object Zone6WidgetFace {
    data class Presentation(val freshness: String, val stale: Boolean, val fix: String?, val alert: String?,
                            val askDraft: String, val askDescription: String)

    fun present(context: Context, instance: EverydayWidgetInstance, face: EverydayCardFace, snapshot: EverydaySnapshot,
                locale: Locale, unavailable: Boolean): Presentation {
        val kind = instance.config.kind
        val sources = snapshot.sources.filter { row -> row.source.enabled && row.source.selected && !row.source.muted &&
            (!snapshot.kids || row.source.childApproved) }
        val sourceKind = when (kind) {
            EverydayCardKind.WEATHER, EverydayCardKind.CLOCK_WEATHER -> CollectorKind.WEATHER
            EverydayCardKind.HEADLINES -> CollectorKind.FEED
            else -> null
        }
        val noSource = !unavailable && sourceKind != null &&
            sources.none { it.source.kind == sourceKind && (instance.config.sourceId == null || it.source.id == instance.config.sourceId) }
        val at = face.observedAt
        val stamp = at?.let {
            val format = if (Duration.between(it, snapshot.now).toHours() < 12) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            format.withLocale(locale).withZone(snapshot.zone).format(it)
        }
        val freshness = when {
            noSource -> context.getString(if (sourceKind == CollectorKind.WEATHER)
                R.string.everyday_widget_no_source_weather else R.string.everyday_widget_no_source_news)
            stamp == null -> face.status
            face.lastRefreshFailed -> context.getString(R.string.everyday_widget_unreachable, stamp)
            face.stale -> context.getString(R.string.everyday_widget_old, stamp)
            else -> context.getString(R.string.everyday_widget_updated, stamp)
        }
        val alert = if (unavailable || snapshot.kids || kind != EverydayCardKind.DASHBOARD || instance.config.adaptiveDashboard) null else sources.asSequence()
            .filter { it.source.kind == CollectorKind.ALERTS }
            .mapNotNull { row ->
                row.snapshot?.takeIf { it.sourceVersion == row.source.version }?.payload as? CollectorPayload.Warnings
            }.flatMap { it.items.asSequence() }.filter { it.expires == null || it.expires > snapshot.now }.map { it.title.trim() }.firstOrNull { it.isNotBlank() }
        val fix = when {
            unavailable -> context.getString(R.string.everyday_widget_configure)
            snapshot.kids -> null
            noSource -> context.getString(R.string.everyday_widget_choose_source)
            face.lastRefreshFailed -> context.getString(R.string.everyday_widget_check_source)
            else -> null
        }
        return Presentation(freshness, face.stale || face.lastRefreshFailed, fix, alert,
            context.getString(R.string.everyday_widget_ask_draft, face.title) + " ",
            context.getString(R.string.everyday_widget_ask) + " · " + face.title)
    }
}
