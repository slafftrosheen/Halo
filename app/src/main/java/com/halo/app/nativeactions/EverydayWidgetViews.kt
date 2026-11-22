package com.halo.app.nativeactions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.presentation.*
import com.halo.app.ui.mini.*
import com.halo.app.ui.everyday.EverydayRoutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

/** Cached native presentation. Every clickable body carries its exact content-free target. */
object EverydayWidgetViews {
    internal fun supportsPaging(kind: EverydayCardKind) =
        kind == EverydayCardKind.SMART_STACK || kind !in EverydayToolkitWidgets.launcherKinds &&
            kind !in setOf(EverydayCardKind.CLOCK, EverydayCardKind.CLOCK_WEATHER, EverydayCardKind.DEVICE, EverydayCardKind.BRIEF)
    const val EXTRA_ID = "com.halo.app.extra.EVERYDAY_WIDGET_ID"
    const val EXTRA_VERSION = "com.halo.app.extra.EVERYDAY_WIDGET_VERSION"
    const val EXTRA_ACTION = "com.halo.app.extra.EVERYDAY_WIDGET_ACTION"
    const val EXTRA_TARGET = "com.halo.app.extra.EVERYDAY_WIDGET_TARGET"
    fun intent(context: Context, instance: EverydayWidgetInstance, action: String, target: EverydayTarget): Intent {
        val payload = EverydayWidgetTargets.encode(target)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        return Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ID,instance.id).putExtra(EXTRA_VERSION,instance.version).putExtra(EXTRA_ACTION,action).putExtra(EXTRA_TARGET,payload)
            .setData(Uri.Builder().scheme("halo").authority("everyday-widget").appendPath(instance.id.toString())
                .appendPath(instance.version.toString()).appendPath(action).appendPath(digest).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    private fun pending(context: Context, instance: EverydayWidgetInstance, action: String, target: EverydayTarget) =
        PendingIntent.getActivity(context,0,intent(context,instance,action,target),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun configure(context: Context,id: Int) = PendingIntent.getActivity(context,id,
        Intent(context,EverydayWidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
            .setData(Uri.parse("halo://configure-widget/$id")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun views(context: Context,instance: EverydayWidgetInstance,snapshot: EverydaySnapshot,options: Bundle,unavailable: Boolean = false, transition: WidgetTransition? = null): RemoteViews {
        if (com.halo.app.privacy.HaloPauseOwner.get(context).isPaused()) return haloPausedWidget(context, instance.id)
        val lockHost = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY) ==
            android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
        fun sized(width: Int,height: Int): RemoteViews = if(unavailable) render(context,instance,snapshot,width,height,
            locale=context.resources.configuration.locales[0],appFontScale=runCatching { Graph.assistantPreferences.state.value.fontScale }.getOrDefault(context.resources.configuration.fontScale),
            label={com.halo.app.language.resolveUiText(context.resources,it)},unavailable=true,lockHost=lockHost)
        else {
            val level = EverydayWidgetMotion.level(context, instance.config.widgetMotion, snapshot.kids)
            val freshGlow = transition?.motion == WidgetUpdateMotion.SOURCE_UPDATE &&
                level == com.halo.app.ui.theme.HaloLookMotion.FULL && !lockHost
            val current = render(context, instance, snapshot, width, height, lockHost = lockHost, freshGlow = freshGlow)
            if (transition == null || lockHost || instance.config.widgetAutoPage || level == com.halo.app.ui.theme.HaloLookMotion.STILL) current else {
                val scheme = EverydayWidgetAppearance.scheme(context, instance.config)
                EverydayWidgetMotion.wrap(context, current, transition, level, scheme.onSurface.toArgb(), scheme.primary.toArgb())
            }
        }
        if(android.os.Build.VERSION.SDK_INT>=31) {
            @Suppress("DEPRECATION")
            val sizes=options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
                .filter { it.width.isFinite() && it.height.isFinite() && it.width >= 1 && it.height >= 1 }
                .distinct().sortedBy { it.width * it.height }
                .let { if (it.size <= 16) it else it.take(8) + it.takeLast(8) }
            if(sizes.isNotEmpty()) return RemoteViews(sizes.associateWith { sized(it.width.toInt().coerceIn(1,1000),it.height.toInt().coerceIn(1,1000)) })
        }
        val width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,240).takeIf { it > 0 }?.coerceAtMost(1000) ?: 240
        val height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240).takeIf { it > 0 }?.coerceAtMost(1000) ?: 240
        val maxWidth=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width).coerceIn(width,1000)
        val maxHeight=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height).coerceIn(height,1000)
        return if(maxWidth>width || maxHeight>height) RemoteViews(sized(maxWidth,height),sized(width,maxHeight)) else sized(width,height)
    }


    private fun RemoteViews.showClockMotion(context: Context, instance: EverydayWidgetInstance,
        snapshot: EverydaySnapshot, width: Int, scale: Float, selected: Boolean, accent: Int) {
        val visible = selected && width >= 240 && scale < 1.3f &&
            EverydayWidgetMotion.level(context, instance.config.widgetMotion, snapshot.kids) == com.halo.app.ui.theme.HaloLookMotion.FULL
        setViewVisibility(R.id.everyday_widget_clock_motion, if (visible) View.VISIBLE else View.GONE)
        if (visible) {
            val padding = (26 * context.resources.displayMetrics.density).toInt()
            setViewPadding(R.id.everyday_widget_clock, 0, 0, padding, 0)
            if (android.os.Build.VERSION.SDK_INT >= 31) setColorStateList(R.id.everyday_widget_clock_motion,
                "setIndeterminateTintList", android.content.res.ColorStateList.valueOf(accent))
        } else setViewPadding(R.id.everyday_widget_clock, 0, 0, 0, 0)
    }

    internal fun render(context: Context,instance: EverydayWidgetInstance,snapshot: EverydaySnapshot,width: Int,height: Int,
        locale: Locale = Locale.forLanguageTag(Graph.appLanguagePreferences.language.value.tag),
        appFontScale: Float = Graph.assistantPreferences.state.value.fontScale,
        label: (String)->String = {context.nativeUi(it)},unavailable: Boolean = false, preview: Boolean = false,
        lockHost: Boolean = false, previewWallpaperDark: Boolean? = null, freshGlow: Boolean = false): RemoteViews {
        val face=if(unavailable) EverydayCardFace(label("Halo widget"),label("Open Halo to load this widget"),emptyList(),
            label("Saved information is unavailable"),EverydayTarget.Organizer)
        else EverydayCards.project(instance.config,snapshot,locale,externalHost=true,text=label)
        val scheme=EverydayWidgetAppearance.scheme(context, instance.config,
            previewWallpaperDark ?: EverydayWidgetAppearance.systemTheme(context, lockHost))
        val density=context.resources.displayMetrics.density
        val scale=maxOf(context.resources.configuration.fontScale,appFontScale)
        val ratio=scale/context.resources.configuration.fontScale
        val rootOpen=if(preview) null else if(unavailable) MiniHaloEntry.pending(context,8700+instance.id,"home") else pending(context,instance,"open",face.target)
        val configOpen=if(preview) null else configure(context,instance.id)
        if (instance.config.kind in EverydayToolkitWidgets.launcherKinds) {
            val localized = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration)
                .apply { setLocale(locale) })
            return EverydayToolkitWidgetViews.render(localized, instance, snapshot, face, width, height, lockHost,
                preview, unavailable, locale, ratio, scheme, label, freshGlow) { target ->
                if (unavailable) configOpen else pending(context, instance, "open", target)
            }
        }
        if (!unavailable && !lockHost && instance.config.kind == EverydayCardKind.CLOCK &&
            width >= 120 && height >= (if (instance.config.zoneId == null) 124 else 150) && scale < 1.35f) {
            val clockAsk = if (!preview && !snapshot.kids && width >= 180) MiniHaloEntry.pending(context, 9100 + instance.id,
                "mini_halo", Zone6WidgetFace.present(context, instance, face, snapshot, locale, false).askDraft) else null
            return EverydayWidgetClockFace.render(context, instance.config, snapshot, scheme, width, height,
                locale, ratio, preview, rootOpen, clockAsk)
        }
        val hasClock=!unavailable && instance.config.kind in setOf(EverydayCardKind.CLOCK,EverydayCardKind.CLOCK_WEATHER,EverydayCardKind.DASHBOARD)
        val paging = instance.config.widgetAutoPage && supportsPaging(instance.config.kind) && !unavailable &&
            !lockHost && !snapshot.kids && width >= 140 &&
            android.animation.ValueAnimator.areAnimatorsEnabled() &&
            EverydayWidgetMotion.level(context, instance.config.widgetMotion, snapshot.kids) != com.halo.app.ui.theme.HaloLookMotion.STILL
        val zone=instance.config.zoneId?.let(ZoneId::of) ?: snapshot.zone
        val rowIds=listOf(R.id.everyday_widget_row1,R.id.everyday_widget_row2,R.id.everyday_widget_row3)
        fun measure(value: String,sp: Float,titleFont: Boolean = false,availableWidth: Int = width-32): Int {
            val paint=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize=sp*context.resources.displayMetrics.scaledDensity*ratio
                typeface=context.resources.getFont(if(locale.language=="ru") R.font.onest_cyrillic_variable else if(titleFont) R.font.sora_variable else R.font.atkinson_hyperlegible_next_variable)
            }
            return ceil(android.text.StaticLayout.Builder.obtain(value,0,value.length,paint,(availableWidth*density).toInt().coerceAtLeast(1))
                .setIncludePad(true).build().height/density).toInt()+2
        }
        fun stamp(at: Instant)=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(snapshot.zone).format(at)+" "+at.atZone(snapshot.zone).offset.id
        val status=if(face.observedAt==null) face.status else listOfNotNull(label(if(snapshot.power) "Power" else "Local"),
            label(if(face.stale) "Cached" else "Saved"),if(face.lastRefreshFailed) label("Last refresh failed") else null,
            face.attribution,face.observedAt.let { (if(face.oldestSelected) label("Oldest saved")+" · " else "")+stamp(it) }).joinToString(" · ")
        val presentation = Zone6WidgetFace.present(context, instance, face, snapshot, locale, unavailable)
        val cityLabel=instance.config.cityLabel.ifBlank { if(instance.config.zoneId==null) label("Phone time zone") else zone.id }
        val clockLabelHeight=if(hasClock) measure(cityLabel,16f)+8 else 0
        val candidates=face.rows.filterIndexed { index,row -> !row.personal && !(index==0 && instance.config.kind in setOf(EverydayCardKind.CLOCK,EverydayCardKind.CLOCK_WEATHER)) }.map { row ->
            val title=if(instance.config.kind==EverydayCardKind.HEADLINES)
                row.title.take(if(scale>=1.5f) 48 else 96)+(if(row.title.length>(if(scale>=1.5f) 48 else 96)) "…" else "") else row.title
            val detail=when {
                instance.config.kind==EverydayCardKind.HEADLINES -> row.publisher.orEmpty()
                instance.config.kind==EverydayCardKind.DASHBOARD && row.savedAt!=null -> listOfNotNull(row.publisher,label(if(row.stale) "Cached" else "Saved"),stamp(row.savedAt)).joinToString(" · ")
                else -> row.detail
            }
            row.copy(title=title,detail=detail)
        }.let { if (paging) it.take(3)
            else if (instance.config.widgetSize == null || instance.config.kind in setOf(EverydayCardKind.CLOCK, EverydayCardKind.CLOCK_WEATHER)) it
            else it.take(instance.config.widgetSize.rowLimit) }
        val clockSp=if(width>=220 && scale<1.5f) 32f else 20f
        val valueSp=if(hasClock) clockSp else 20f
        val clock24=android.text.format.DateFormat.is24HourFormat(context)
        // Reserve the widest supported clock string, using the same bundled font as the actual TextClock.
        val valueHeight=measure(if(hasClock) (if(clock24) "23:59" else "11:59 PM") else face.value,valueSp,true)
        val rowHeights=candidates.map { maxOf(48,measure(listOf(it.title,it.detail).filter(String::isNotBlank).joinToString("\n"),16f))+8 }
        val headline=instance.config.kind==EverydayCardKind.HEADLINES && candidates.isNotEmpty()
        val mandatoryRows=when(instance.config.kind) {
            EverydayCardKind.CLOCK,EverydayCardKind.CLOCK_WEATHER -> rowHeights.sum()
            EverydayCardKind.HEADLINES,EverydayCardKind.WEATHER -> rowHeights.firstOrNull() ?: 0
            else -> 0
        }
        val dateHeight=measure("2233-12-31",16f)+8
        var remaining=height-32-measure(presentation.freshness,13f)-8
        val required=(if(headline) 0 else valueHeight+8)+mandatoryRows+(if(hasClock) dateHeight+clockLabelHeight else 0)
        val minimal=remaining<required
        val layout=if(locale.language=="ru") R.layout.halo_everyday_widget_ru else R.layout.halo_everyday_widget
        val views=RemoteViews(context.packageName,layout)
        views.setViewVisibility(R.id.everyday_widget_page_flipper, View.GONE)
        views.showClockMotion(context, instance, snapshot, width, scale, !preview && hasClock && !minimal, scheme.primary.toArgb())
        views.setImageViewBitmap(R.id.everyday_widget_background,
            EverydayWidgetAppearance.background(context, instance.config, scheme, width, height))
        views.setOnClickPendingIntent(R.id.everyday_widget_root,rootOpen)
        // Let the clickable container aggregate its live TextClock children, rather than caching a spoken time.
        views.setContentDescription(R.id.everyday_widget_root,null)
        listOf(R.id.everyday_widget_ask, R.id.everyday_widget_alert, R.id.everyday_widget_configure).forEach {
            views.setViewVisibility(it, View.GONE)
        }
        if(minimal && !unavailable && instance.config.kind==EverydayCardKind.CLOCK) {
            val fixed=instance.config.zoneId!=null
            val origin=if(fixed) measure(cityLabel,16f)+8 else 0
            val shortDate=android.text.format.DateFormat.getBestDateTimePattern(locale,"Md")
            val compactDate=measure("88.88.",16f)+8
            // Keep a real host-owned clock at large reading sizes. Origin is mandatory for a fixed city.
            if(height-32>=valueHeight+8+origin) {
                views.setViewVisibility(R.id.everyday_widget_header,View.GONE)
                (rowIds+listOf(R.id.everyday_widget_value,R.id.everyday_widget_status,R.id.everyday_widget_configure)).forEach {views.setViewVisibility(it,View.GONE)}
                views.setViewVisibility(R.id.everyday_widget_clock,View.VISIBLE)
                views.showClockMotion(context, instance, snapshot, width, scale, !preview, scheme.primary.toArgb())
                views.setTextViewTextSize(R.id.everyday_widget_clock,android.util.TypedValue.COMPLEX_UNIT_SP,clockSp*ratio)
                views.setTextColor(R.id.everyday_widget_clock,scheme.onSurface.toArgb())
                views.setString(R.id.everyday_widget_clock,"setTimeZone",instance.config.zoneId)
                views.setCharSequence(R.id.everyday_widget_clock,"setFormat24Hour","HH:mm")
                views.setCharSequence(R.id.everyday_widget_clock,"setFormat12Hour","h:mm a")
                val dateFits=height-32>=valueHeight+8+origin+compactDate
                views.setViewVisibility(R.id.everyday_widget_clock_date,if(dateFits) View.VISIBLE else View.GONE)
                views.setString(R.id.everyday_widget_clock_date,"setTimeZone",instance.config.zoneId)
                views.setCharSequence(R.id.everyday_widget_clock_date,"setFormat24Hour",shortDate)
                views.setCharSequence(R.id.everyday_widget_clock_date,"setFormat12Hour",shortDate)
                views.setTextViewTextSize(R.id.everyday_widget_clock_date,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
                views.setTextColor(R.id.everyday_widget_clock_date,scheme.onSurface.toArgb())
                views.setViewVisibility(R.id.everyday_widget_clock_label,if(fixed) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.everyday_widget_clock_label,cityLabel)
                views.setTextViewTextSize(R.id.everyday_widget_clock_label,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
                views.setTextColor(R.id.everyday_widget_clock_label,scheme.onSurfaceVariant.toArgb())
                return views
            }
        }
        if(minimal) {
            val identity=label(when(instance.config.kind) {
                EverydayCardKind.CLOCK_WEATHER -> "Clock and weather";EverydayCardKind.UPCOMING -> "Agenda"
                EverydayCardKind.TASKS -> "Tasks";EverydayCardKind.BRIEF -> "Daily brief";EverydayCardKind.HEADLINES -> "Headlines"
                EverydayCardKind.ALARMS_TIMERS -> "Alarms";EverydayCardKind.DASHBOARD -> "Your day";EverydayCardKind.TODAY -> "Today";else -> "Clock"
            })
            val action=label(if(unavailable) "Configure widget" else "Open")
            val requestedText=identity+"\n"+action
            val text=when {measure(requestedText,16f,true)<=height-40 -> requestedText
                measure(identity,16f,true)<=height-40 -> identity;else -> label("Halo")}
            views.setContentDescription(R.id.everyday_widget_root,action+" · "+label(EverydayCards.title(instance.config.kind)))
            views.setViewVisibility(R.id.everyday_widget_header,View.GONE)
            (rowIds+listOf(R.id.everyday_widget_clock,R.id.everyday_widget_clock_date,R.id.everyday_widget_clock_label,R.id.everyday_widget_status,R.id.everyday_widget_configure)).forEach { views.setViewVisibility(it,View.GONE) }
            views.setTextViewText(R.id.everyday_widget_value,text)
            views.setViewVisibility(R.id.everyday_widget_value,View.VISIBLE)
            views.setTextViewTextSize(R.id.everyday_widget_value,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
            views.setTextColor(R.id.everyday_widget_value,scheme.onSurface.toArgb())
            views.setOnClickPendingIntent(R.id.everyday_widget_root,if(unavailable) configOpen else rootOpen)
            return views
        }
        val showAsk = !preview && !unavailable && !snapshot.kids && width >= 180
        val headerHeight = maxOf(48, measure(face.title, 14f, availableWidth = width - if (showAsk) 110 else 62))
        val showHeader=remaining>=required+headerHeight
        if(showHeader) remaining-=headerHeight
        views.setViewVisibility(R.id.everyday_widget_header,if(showHeader) View.VISIBLE else View.GONE)
        views.setImageViewBitmap(R.id.everyday_widget_orb, EverydayWidgetGlyphs.bitmap(context,
            EverydayWidgetGlyphs.resource(instance.config.kind), 15, scheme.onSurfaceVariant.toArgb()))
        views.setImageViewBitmap(R.id.everyday_widget_header_ring, nearbyOrb(context, (14 * density).toInt(), scheme = scheme))
        views.setViewVisibility(R.id.everyday_widget_header_ring, if (showAsk) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.everyday_widget_title,face.title)
        views.setTextViewTextSize(R.id.everyday_widget_title,android.util.TypedValue.COMPLEX_UNIT_SP,14f*ratio)
        views.setTextColor(R.id.everyday_widget_title,scheme.onSurfaceVariant.toArgb())
        if (showHeader && showAsk) {
            views.setViewVisibility(R.id.everyday_widget_ask, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.everyday_widget_ask, MiniHaloEntry.pending(context, 9100 + instance.id,
                "mini_halo", presentation.askDraft))
            views.setContentDescription(R.id.everyday_widget_ask, presentation.askDescription)
        }
        val alert = presentation.alert
        val alertHeight = alert?.let { maxOf(36, measure(it, 14f, availableWidth = width - 54)) + 8 }
        if (alert != null && alertHeight != null && remaining >= required + alertHeight) {
            views.setViewVisibility(R.id.everyday_widget_alert, View.VISIBLE)
            views.setTextViewText(R.id.everyday_widget_alert, alert)
            views.setTextViewTextSize(R.id.everyday_widget_alert, android.util.TypedValue.COMPLEX_UNIT_SP, 14f * ratio)
            remaining -= alertHeight
        }
        val showValue=!headline
        views.setViewVisibility(R.id.everyday_widget_value,if(showValue && !hasClock) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.everyday_widget_clock,if(showValue && hasClock) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.everyday_widget_value,face.value)
        listOf(R.id.everyday_widget_value,R.id.everyday_widget_clock).forEach { id ->
            views.setTextColor(id,scheme.onSurface.toArgb());views.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,valueSp*ratio)
        }
        views.setString(R.id.everyday_widget_clock,"setTimeZone",instance.config.zoneId)
        views.setCharSequence(R.id.everyday_widget_clock,"setFormat24Hour","HH:mm")
        views.setCharSequence(R.id.everyday_widget_clock,"setFormat12Hour","h:mm a")
        if(showValue) remaining-=valueHeight+8
        val showDate=hasClock && remaining>=dateHeight+clockLabelHeight+mandatoryRows
        views.setViewVisibility(R.id.everyday_widget_clock_date,if(showDate) View.VISIBLE else View.GONE)
        views.setString(R.id.everyday_widget_clock_date,"setTimeZone",instance.config.zoneId)
        views.setCharSequence(R.id.everyday_widget_clock_date,"setFormat24Hour","yyyy-MM-dd")
        views.setCharSequence(R.id.everyday_widget_clock_date,"setFormat12Hour","yyyy-MM-dd")
        views.setTextColor(R.id.everyday_widget_clock_date,scheme.onSurface.toArgb())
        views.setTextViewTextSize(R.id.everyday_widget_clock_date,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
        if(showDate) remaining-=dateHeight
        views.setViewVisibility(R.id.everyday_widget_clock_label,if(hasClock) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.everyday_widget_clock_label,cityLabel)
        views.setTextColor(R.id.everyday_widget_clock_label,scheme.onSurfaceVariant.toArgb())
        views.setTextViewTextSize(R.id.everyday_widget_clock_label,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
        remaining-=clockLabelHeight
        val pageHeight = maxOf(56, rowHeights.maxOrNull() ?: 0)
        val usePages = paging && candidates.size >= 2 && remaining >= pageHeight
        if (usePages) {
            remaining -= pageHeight
            views.removeAllViews(R.id.everyday_widget_page_flipper)
            val showPictures = !preview && width >= 200 && scale < 1.45f &&
                runCatching { Graph.articlePictureSettings.visible.value }.getOrDefault(false)
            candidates.forEachIndexed { index, row ->
                val page = RemoteViews(context.packageName, R.layout.halo_widget_page_row)
                page.setTextViewText(R.id.halo_widget_page_text,
                    listOf(row.title, row.detail).filter(String::isNotBlank).joinToString("\n"))
                page.setTextColor(R.id.halo_widget_page_text, scheme.onSurface.toArgb())
                page.setTextViewTextSize(R.id.halo_widget_page_text, android.util.TypedValue.COMPLEX_UNIT_SP, 16f * ratio)
                if (showPictures) widgetArticleThumbnail(row.pictureKey)?.let { picture ->
                    page.setImageViewBitmap(R.id.halo_widget_page_picture, picture)
                    page.setViewVisibility(R.id.halo_widget_page_picture, View.VISIBLE)
                }
                if (!preview) page.setOnClickPendingIntent(R.id.halo_widget_page_root,
                    pending(context, instance, "row:$index", row.target ?: face.target))
                views.addView(R.id.everyday_widget_page_flipper, page)
            }
            views.setViewVisibility(R.id.everyday_widget_page_flipper, View.VISIBLE)
        }
        rowIds.forEachIndexed { index,id ->
            val row=candidates.getOrNull(index)
            val needed=rowHeights.getOrNull(index) ?: Int.MAX_VALUE
            val shown=!usePages && row!=null && remaining>=needed
            views.setViewVisibility(id,if(shown) View.VISIBLE else View.GONE)
            if(shown) {
                remaining-=needed
                views.setTextViewText(id,listOf(row!!.title,row.detail).filter(String::isNotBlank).joinToString("\n"))
                views.setTextColor(id,scheme.onSurface.toArgb())
                views.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
                if (!preview) views.setOnClickPendingIntent(id,pending(context,instance,"row:$index",row.target ?: face.target))
            }
        }
        views.setTextViewText(R.id.everyday_widget_status,presentation.freshness)
        views.setViewVisibility(R.id.everyday_widget_status,View.VISIBLE)
        views.setTextViewTextSize(R.id.everyday_widget_status,android.util.TypedValue.COMPLEX_UNIT_SP,13f*ratio)
        val amber = if (androidx.core.graphics.ColorUtils.calculateLuminance(scheme.surface.toArgb()) < .5)
            0xFFF4C56A.toInt() else 0xFF805100.toInt()
        views.setTextColor(R.id.everyday_widget_status, if (presentation.stale) amber else scheme.onSurfaceVariant.toArgb())
        views.setContentDescription(R.id.everyday_widget_status, listOf(presentation.freshness, status)
            .filter(String::isNotBlank).distinct().joinToString(" · "))
        val fix = presentation.fix
        if (!preview && fix != null && remaining >= 54 && measure(fix, 15f, availableWidth = width - 64) <= 46) {
            views.setTextViewText(R.id.everyday_widget_configure,fix)
            views.setTextViewTextSize(R.id.everyday_widget_configure,android.util.TypedValue.COMPLEX_UNIT_SP,15f*ratio)
            views.setTextColor(R.id.everyday_widget_configure,scheme.primary.toArgb())
            views.setViewVisibility(R.id.everyday_widget_configure, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.everyday_widget_configure,
                if (unavailable) configOpen else MiniHaloEntry.pending(context, 9300 + instance.id, EverydayRoutes.SOURCES))
        }
        return views
    }

    private fun widgetArticleThumbnail(key: String?): android.graphics.Bitmap? = runCatching {
        val file = Graph.articleContentStore.imageFile(key) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, options)
        val largest = maxOf(options.outWidth, options.outHeight)
        if (largest !in 1..16_384) return null
        options.inSampleSize = Integer.highestOneBit((largest / 120).coerceAtLeast(1))
        options.inJustDecodeBounds = false
        android.graphics.BitmapFactory.decodeFile(file.path, options)
    }.getOrNull()

    fun unavailable(context: Context,id: Int): RemoteViews {
        if (com.halo.app.privacy.HaloPauseOwner.get(context).isPaused()) return haloPausedWidget(context, id)
        val locale=runCatching { Locale.forLanguageTag(Graph.appLanguagePreferences.language.value.tag) }.getOrDefault(context.resources.configuration.locales[0])
        val localized=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) })
        val kind=EverydayWidgetProviders.kind(context,id) ?: EverydayCardKind.CLOCK
        val instance=EverydayWidgetStore(context).get(id) ?: EverydayWidgetInstance(id,0,EverydayCardConfig(kind))
        val snapshot=EverydaySnapshot(Instant.now(),ZoneId.systemDefault(),emptyList(),emptyList(),emptyList(),emptyList())
        return views(localized,instance,snapshot,AppWidgetManager.getInstance(context).getAppWidgetOptions(id),unavailable=true)
    }
}
