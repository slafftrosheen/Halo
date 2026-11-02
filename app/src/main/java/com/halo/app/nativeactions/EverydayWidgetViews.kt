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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

/** Cached native presentation. Every clickable body carries its exact content-free target. */
object EverydayWidgetViews {
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

    fun views(context: Context,instance: EverydayWidgetInstance,snapshot: EverydaySnapshot,options: Bundle,unavailable: Boolean = false): RemoteViews {
        fun sized(width: Int,height: Int): RemoteViews = if(unavailable) render(context,instance,snapshot,width,height,
            locale=context.resources.configuration.locales[0],appFontScale=runCatching { Graph.assistantPreferences.state.value.fontScale }.getOrDefault(context.resources.configuration.fontScale),
            label={com.halo.app.language.resolveUiText(context.resources,it)},unavailable=true)
        else render(context,instance,snapshot,width,height)
        if(android.os.Build.VERSION.SDK_INT>=31) {
            @Suppress("DEPRECATION")
            val sizes=options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
                .filter { it.width.isFinite() && it.height.isFinite() && it.width>=120 && it.height>=96 }.distinct().take(4)
            if(sizes.isNotEmpty()) return RemoteViews(sizes.associateWith { sized(it.width.toInt(),it.height.toInt()) })
        }
        val width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,240).coerceIn(120,1000)
        val height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240).coerceIn(96,1000)
        val maxWidth=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width).coerceIn(120,1000)
        val maxHeight=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height).coerceIn(96,1000)
        return if(maxWidth>width || maxHeight>height) RemoteViews(sized(maxWidth,height),sized(width,maxHeight)) else sized(width,height)
    }

    internal fun render(context: Context,instance: EverydayWidgetInstance,snapshot: EverydaySnapshot,width: Int,height: Int,
        locale: Locale = Locale.forLanguageTag(Graph.appLanguagePreferences.language.value.tag),
        appFontScale: Float = Graph.assistantPreferences.state.value.fontScale,
        label: (String)->String = {context.nativeUi(it)},unavailable: Boolean = false): RemoteViews {
        val face=if(unavailable) EverydayCardFace(label("Halo widget"),label("Open Halo to load this widget"),emptyList(),
            label("Saved information is unavailable"),EverydayTarget.Organizer)
        else EverydayCards.project(instance.config,snapshot,locale,externalHost=true,text=label)
        val scheme=nearbyScheme(context,when(instance.config.theme) { "LIGHT" -> false;"DARK" -> true;else -> null })
        val density=context.resources.displayMetrics.density
        val scale=maxOf(context.resources.configuration.fontScale,appFontScale)
        val ratio=scale/context.resources.configuration.fontScale
        val rootOpen=if(unavailable) MiniHaloEntry.pending(context,8700+instance.id,"home") else pending(context,instance,"open",face.target)
        val configOpen=configure(context,instance.id)
        val hasClock=!unavailable && instance.config.kind in setOf(EverydayCardKind.CLOCK,EverydayCardKind.CLOCK_WEATHER,EverydayCardKind.DASHBOARD)
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
        }
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
        var remaining=height-32-measure(status,14f)-8
        val required=(if(headline) 0 else valueHeight+8)+mandatoryRows+(if(hasClock) dateHeight+clockLabelHeight else 0)
        val minimal=remaining<required
        val layout=if(locale.language=="ru") R.layout.halo_everyday_widget_ru else R.layout.halo_everyday_widget
        val views=RemoteViews(context.packageName,layout)
        val physicalWidth=(width*density).coerceAtLeast(1f)
        val physicalHeight=(height*density).coerceAtLeast(1f)
        val bitmapScale=minOf(1f,512f/physicalWidth,512f/physicalHeight)
        views.setImageViewBitmap(R.id.everyday_widget_background,nearbyBackground(ceil(physicalWidth*bitmapScale).toInt(),
            ceil(physicalHeight*bitmapScale).toInt(),density*bitmapScale,scheme.surfaceContainerLow.toArgb(),scheme.outlineVariant.toArgb()))
        views.setOnClickPendingIntent(R.id.everyday_widget_root,rootOpen)
        // Let the clickable container aggregate its live TextClock children, rather than caching a spoken time.
        views.setContentDescription(R.id.everyday_widget_root,null)
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
                EverydayCardKind.ALARMS_TIMERS -> "Alarms";EverydayCardKind.DASHBOARD -> "Your day";else -> "Clock"
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
        val headerHeight=maxOf(48,measure(face.title,18f,true,width-80))
        val showHeader=remaining>=required+headerHeight
        if(showHeader) remaining-=headerHeight
        views.setViewVisibility(R.id.everyday_widget_header,if(showHeader) View.VISIBLE else View.GONE)
        views.setImageViewBitmap(R.id.everyday_widget_orb,nearbyOrb(context,(32*density).toInt(),scheme=scheme))
        views.setTextViewText(R.id.everyday_widget_title,face.title)
        views.setTextViewTextSize(R.id.everyday_widget_title,android.util.TypedValue.COMPLEX_UNIT_SP,18f*ratio)
        views.setTextColor(R.id.everyday_widget_title,scheme.onSurface.toArgb())
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
        rowIds.forEachIndexed { index,id ->
            val row=candidates.getOrNull(index)
            val needed=rowHeights.getOrNull(index) ?: Int.MAX_VALUE
            val shown=row!=null && remaining>=needed
            views.setViewVisibility(id,if(shown) View.VISIBLE else View.GONE)
            if(shown) {
                remaining-=needed
                views.setTextViewText(id,listOf(row!!.title,row.detail).filter(String::isNotBlank).joinToString("\n"))
                views.setTextColor(id,scheme.onSurface.toArgb())
                views.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
                views.setOnClickPendingIntent(id,pending(context,instance,"row:$index",row.target ?: face.target))
            }
        }
        views.setTextViewText(R.id.everyday_widget_status,status)
        views.setViewVisibility(R.id.everyday_widget_status,View.VISIBLE)
        views.setTextViewTextSize(R.id.everyday_widget_status,android.util.TypedValue.COMPLEX_UNIT_SP,14f*ratio)
        views.setTextColor(R.id.everyday_widget_status,scheme.onSurfaceVariant.toArgb())
        val configureText=label("Configure widget")
        views.setTextViewText(R.id.everyday_widget_configure,configureText)
        views.setTextViewTextSize(R.id.everyday_widget_configure,android.util.TypedValue.COMPLEX_UNIT_SP,16f*ratio)
        views.setTextColor(R.id.everyday_widget_configure,scheme.primary.toArgb())
        views.setViewVisibility(R.id.everyday_widget_configure,if(remaining>=maxOf(48,measure(configureText,16f))) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.everyday_widget_configure,configOpen)
        return views
    }

    fun unavailable(context: Context,id: Int): RemoteViews {
        val locale=runCatching { Locale.forLanguageTag(Graph.appLanguagePreferences.language.value.tag) }.getOrDefault(context.resources.configuration.locales[0])
        val localized=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) })
        val kind=EverydayWidgetProviders.kind(context,id) ?: EverydayCardKind.CLOCK
        val instance=EverydayWidgetStore(context).get(id) ?: EverydayWidgetInstance(id,0,EverydayCardConfig(kind))
        val snapshot=EverydaySnapshot(Instant.now(),ZoneId.systemDefault(),emptyList(),emptyList(),emptyList(),emptyList())
        return views(localized,instance,snapshot,AppWidgetManager.getInstance(context).getAppWidgetOptions(id),unavailable=true)
    }
}
