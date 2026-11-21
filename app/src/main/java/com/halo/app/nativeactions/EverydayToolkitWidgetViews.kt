package com.halo.app.nativeactions

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.everyday.presentation.*
import com.halo.app.ui.mini.nearbyBackground
import com.halo.app.ui.mini.nearbyOrb
import com.halo.app.ui.mini.nearbyScheme
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

internal object EverydayWidgetAppearance {
    fun explicitTheme(config: EverydayCardConfig): Boolean? = when (config.theme) {
        "LIGHT" -> false
        "DARK" -> true
        else -> null
    }

    fun systemTheme(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 27) return null
        return runCatching {
            val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return@runCatching null
            when {
                Build.VERSION.SDK_INT >= 31 && colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0 -> false
                else -> ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) < 0.54
            }
        }.getOrNull()
    }

    fun scheme(context: Context, config: EverydayCardConfig, wallpaperTheme: Boolean? = systemTheme(context)): ColorScheme =
        nearbyScheme(context, explicitTheme(config) ?: wallpaperTheme, config.widgetMatchLook)

    fun surfaceAlpha(style: EverydayWidgetStyle, highContrast: Boolean): Float = if (highContrast) 1f else when (style) {
        EverydayWidgetStyle.GLASS -> .92f
        EverydayWidgetStyle.SOLID -> 1f
        EverydayWidgetStyle.CLEAR -> .68f
    }

    fun background(context: Context, config: EverydayCardConfig, scheme: ColorScheme, width: Int, height: Int,
        pixelsLimit: Float = 512f, active: Boolean = false): Bitmap {
        val density = context.resources.displayMetrics.density
        val pixelsWide = (width * density).coerceAtLeast(1f)
        val pixelsHigh = (height * density).coerceAtLeast(1f)
        val scale = minOf(1f, pixelsLimit / pixelsWide, pixelsLimit / pixelsHigh)
        val highContrast = context.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE).getBoolean("contrast", false)
        val surface = scheme.surfaceContainerLow.copy(alpha = surfaceAlpha(config.widgetStyle, highContrast))
        return nearbyBackground(ceil(pixelsWide * scale).toInt(), ceil(pixelsHigh * scale).toInt(), density * scale,
            surface.toArgb(), (if (active) scheme.primary else scheme.outlineVariant).toArgb(), radiusDp = 28f,
            glow = scheme.primary.toArgb().takeIf { config.widgetStyle == EverydayWidgetStyle.GLASS && !highContrast })
    }
}

internal object EverydayToolkitWidgetViews {
    private val dayIds = listOf(R.id.toolkit_widget_day1, R.id.toolkit_widget_day2, R.id.toolkit_widget_day3,
        R.id.toolkit_widget_day4, R.id.toolkit_widget_day5, R.id.toolkit_widget_day6, R.id.toolkit_widget_day7)

    fun render(context: Context, instance: EverydayWidgetInstance, snapshot: EverydaySnapshot, face: EverydayCardFace,
        width: Int, height: Int, lockHost: Boolean, preview: Boolean, unavailable: Boolean, locale: Locale, ratio: Float,
        label: (String) -> String, onOpen: (EverydayTarget) -> PendingIntent?): RemoteViews {
        val config = instance.config
        val scheme = EverydayWidgetAppearance.scheme(context, config)
        val kinds = EverydayToolkitWidgets.stackKinds(snapshot)
        val swipe = config.kind == EverydayCardKind.SMART_STACK && Build.VERSION.SDK_INT >= 31 && !lockHost &&
            height >= 115 && !preview && !unavailable
        if (swipe) {
            val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            kinds.forEachIndexed { index, kind ->
                val card = config.copy(kind = kind)
                val projected = EverydayCards.project(card, snapshot, locale, externalHost = true, text = label)
                val views = faceView(context, card, snapshot, projected, width, height, locale, ratio, scheme, false,
                    label, stackIndex = index, stackSize = kinds.size)
                val fillIn = Intent().putExtra(EverydayWidgetViews.EXTRA_TARGET, EverydayWidgetTargets.encode(projected.target))
                views.setOnClickFillInIntent(R.id.toolkit_widget_root, fillIn)
                items.addItem(kind.ordinal.toLong(), views)
            }
            return RemoteViews(context.packageName, R.layout.halo_toolkit_stack).apply {
                setRemoteAdapter(R.id.toolkit_widget_stack, items.build())
                val template = Intent(context, MainActivity::class.java)
                    .setData(Uri.parse("halo://everyday-widget-stack/" + instance.id + "/" + instance.version))
                    .putExtra(EverydayWidgetViews.EXTRA_ID, instance.id)
                    .putExtra(EverydayWidgetViews.EXTRA_VERSION, instance.version)
                    .putExtra(EverydayWidgetViews.EXTRA_ACTION, "open")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                setPendingIntentTemplate(R.id.toolkit_widget_stack, PendingIntent.getActivity(context, instance.id, template,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            }
        }
        val faceConfig = if (config.kind == EverydayCardKind.SMART_STACK && !unavailable) config.copy(kind = kinds.first()) else config
        return faceView(context, faceConfig, snapshot, face, width, height, locale, ratio, scheme, lockHost, label,
            preview = preview).apply {
            if (!preview) setOnClickPendingIntent(R.id.toolkit_widget_root, onOpen(face.target))
        }
    }

    private fun faceView(context: Context, config: EverydayCardConfig, snapshot: EverydaySnapshot, face: EverydayCardFace,
        width: Int, height: Int, locale: Locale, ratio: Float, scheme: ColorScheme, lockHost: Boolean, label: (String) -> String,
        stackIndex: Int = 0, stackSize: Int = 0, preview: Boolean = false): RemoteViews {
        val kind = config.kind
        val accent = scheme.primary.toArgb()
        val foreground = scheme.onSurface.toArgb()
        val muted = scheme.onSurfaceVariant.toArgb()
        val compact = lockHost || height < 100 || width < 110
        val showWeek = kind == EverydayCardKind.PLANNER_WEEK && config.widgetShows in
            setOf(EverydayWidgetShows.DEFAULT, EverydayWidgetShows.THIS_WEEK) && width >= 210 && height >= 138 && ratio < 1.35f
        val runningFocus = snapshot.widgetFocus?.takeIf { kind == EverydayCardKind.FOCUS }
        val focus = runningFocus?.takeIf { !compact && height >= 146 && ratio < 1.35f }
        val attempts = snapshot.widgetChildAttempts.takeIf { kind == EverydayCardKind.KIDS_SKY && snapshot.kids } ?: 0
        val marks = when {
            kind == EverydayCardKind.KIDS_SKY && snapshot.kids -> (0 until 5).joinToString(" ") { if (it < attempts) "★" else "☆" }
            kind == EverydayCardKind.LITTLE_HABITS && !snapshot.kids -> snapshot.widgetHabits.take(2).joinToString("\n") { days ->
                days.joinToString(" ") { if (it) "●" else "○" }
            }
            else -> ""
        }
        val spokenMarks = if (kind != EverydayCardKind.KIDS_SKY) null else context.getString(
            if (attempts == 1) R.string.widget_toolkit_one_star else R.string.widget_toolkit_many_stars, attempts)
        val status = freshness(context, face, snapshot, locale)
        val padding = (context.resources.displayMetrics.density * (if (compact) 8 else 12)).toInt()
        val roomForStatus = height >= (if (compact) 78 else if (stackSize > 0 || showWeek || focus != null) 155 else 104)
        return RemoteViews(context.packageName, R.layout.halo_toolkit_widget).apply {
            setImageViewBitmap(R.id.toolkit_widget_background, EverydayWidgetAppearance.background(context,
                config, scheme, width, height, if (stackSize > 0) 256f else 512f,
                active = kind == EverydayCardKind.RECORD && snapshot.widgetRecording && !lockHost))
            setViewPadding(R.id.toolkit_widget_root, padding, padding, padding, padding)
            setImageViewBitmap(R.id.toolkit_widget_orb, nearbyOrb(context, (16 * context.resources.displayMetrics.density).toInt(), scheme = scheme))
            text(R.id.toolkit_widget_title, face.title, muted, 12f * ratio)
            val clock = runningFocus != null && !preview
            setViewVisibility(R.id.toolkit_widget_value, if (clock) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.toolkit_widget_countdown, if (clock) View.VISIBLE else View.GONE)
            if (runningFocus != null && clock) {
                setChronometer(R.id.toolkit_widget_countdown, runningFocus.deadlineElapsed, null, true)
                setChronometerCountDown(R.id.toolkit_widget_countdown, true)
                setTextColor(R.id.toolkit_widget_countdown, foreground)
                setTextViewTextSize(R.id.toolkit_widget_countdown, android.util.TypedValue.COMPLEX_UNIT_SP, (if (compact) 15f else 18f) * ratio)
            }
            text(R.id.toolkit_widget_value, face.value, foreground, (if (compact) 15f else 18f) * ratio)
            setInt(R.id.toolkit_widget_value, "setMaxLines", if (compact || showWeek || focus != null) 1 else 2)
            val detail = face.rows.firstOrNull()?.title.orEmpty()
            val showDetail = !compact && detail.isNotBlank() && height >= 125 && ratio < 1.4f
            setViewVisibility(R.id.toolkit_widget_detail, if (showDetail) View.VISIBLE else View.GONE)
            text(R.id.toolkit_widget_detail, detail, muted, 12f * ratio)
            setViewVisibility(R.id.toolkit_widget_status, if (roomForStatus) View.VISIBLE else View.GONE)
            val credited = listOfNotNull(status, face.attribution).joinToString(" · ")
            text(R.id.toolkit_widget_status, credited, muted, 13f * ratio)
            if (!roomForStatus || !showDetail && detail.isNotBlank()) setContentDescription(R.id.toolkit_widget_value,
                listOf(face.value, detail.takeUnless { showDetail }.orEmpty(), status).filter(String::isNotBlank).distinct().joinToString(" · "))
            setViewVisibility(R.id.toolkit_widget_week, if (showWeek) View.VISIBLE else View.GONE)
            if (showWeek) EverydayToolkitWidgets.week(snapshot, locale).forEachIndexed { index, day ->
                val id = dayIds[index]
                val longDay = day.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                text(id, day.label + "\n" + day.day.dayOfMonth + "\n" +
                    "•".repeat(day.eventCount.coerceAtMost(3)).padEnd(1, ' '),
                    if (day.today) ContextCompat.getColor(context, R.color.widget_toolkit_day_ink) else foreground, 12f * ratio)
                if (day.today) setInt(id, "setBackgroundResource", R.drawable.halo_widget_day_sage)
                val dateLabel = if (day.today) context.getString(R.string.widget_toolkit_today_day, longDay) else longDay
                setContentDescription(id, context.getString(R.string.widget_toolkit_day_events, dateLabel, day.eventCount))
            }
            setViewVisibility(R.id.toolkit_widget_ring, if (focus == null) View.GONE else View.VISIBLE)
            if (focus != null) setImageViewBitmap(R.id.toolkit_widget_ring, focusRing(focus, accent, muted))
            val marksVisible = marks.isNotBlank() && !compact && height >= 130 && (!roomForStatus || height >= 148 || ratio < 1.2f)
            setViewVisibility(R.id.toolkit_widget_marks, if (marksVisible) View.VISIBLE else View.GONE)
            if (marksVisible) {
                text(R.id.toolkit_widget_marks, marks, accent, 16f * ratio)
                setContentDescription(R.id.toolkit_widget_marks, spokenMarks ?: label("Days a routine was run"))
            }
            setViewVisibility(R.id.toolkit_widget_dots, if (stackSize > 0) View.VISIBLE else View.GONE)
            if (stackSize > 0) {
                text(R.id.toolkit_widget_dots, (0 until stackSize).joinToString(" ") { if (it == stackIndex) "●" else "○" },
                    accent, 13f * ratio)
                setContentDescription(R.id.toolkit_widget_dots, context.getString(R.string.widget_toolkit_stack_position,
                    stackIndex + 1, stackSize))
            }
        }
    }

    fun freshness(context: Context, face: EverydayCardFace, snapshot: EverydaySnapshot, locale: Locale): String {
        val updated = face.observedAt ?: return face.status
        val minutes = Duration.between(updated, snapshot.now).toMinutes().coerceAtLeast(0)
        if (face.stale || face.lastRefreshFailed || minutes >= 60) {
            val stamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(snapshot.zone).format(updated)
            val format = when {
                face.lastRefreshFailed -> R.string.everyday_widget_unreachable
                face.stale -> R.string.everyday_widget_old
                else -> R.string.everyday_widget_updated
            }
            return context.getString(format, stamp)
        }
        return if (minutes == 0L) context.getString(R.string.widget_toolkit_updated_now)
        else context.resources.getQuantityString(R.plurals.widget_toolkit_updated_minutes, minutes.toInt(), minutes)
    }

    private fun RemoteViews.text(id: Int, value: String, color: Int, size: Float) {
        setTextViewText(id, value)
        setTextColor(id, color)
        setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun focusRing(focus: WidgetFocus, color: Int, muted: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f
            strokeCap = Paint.Cap.ROUND
            this.color = ColorUtils.setAlphaComponent(muted, 80)
        }
        val frame = RectF(10f, 10f, 110f, 110f)
        canvas.drawOval(frame, paint)
        paint.color = color
        val progress = (focus.remainingMs.toFloat() / focus.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        canvas.drawArc(frame, -90f, progress * 360f, false, paint)
        return bitmap
    }
}
