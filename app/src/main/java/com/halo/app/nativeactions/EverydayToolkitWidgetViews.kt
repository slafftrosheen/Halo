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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.everyday.presentation.*
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

    fun systemTheme(context: Context, lockHost: Boolean = false): Boolean? {
        if (Build.VERSION.SDK_INT < 27) return null
        return runCatching {
            val manager = WallpaperManager.getInstance(context)
            val lockColors = if (lockHost) manager.getWallpaperColors(WallpaperManager.FLAG_LOCK) else null
            val colors = lockColors ?: manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
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
        val surface = scheme.surfaceContainerLow.toArgb()
        val night = ColorUtils.calculateLuminance(surface) < .5
        val glass = config.widgetStyle == EverydayWidgetStyle.GLASS && !highContrast
        val colour = if (!glass) surface else ColorUtils.blendARGB(surface,
            if (night) 0xFF13144A.toInt() else 0xFFF4ECDD.toInt(), .42f)
        val high = if (!glass) colour else ColorUtils.blendARGB(colour,
            if (night) 0xFF262959.toInt() else Color.WHITE, .20f)
        val opacity = (surfaceAlpha(config.widgetStyle, highContrast) * 255f).toInt()
        val pixelDp = density * scale
        val bitmap = Bitmap.createBitmap(ceil(pixelsWide * scale).toInt(), ceil(pixelsHigh * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = pixelDp / 2f
        val bounds = RectF(inset, inset, bitmap.width - inset, bitmap.height - inset)
        val radius = 28f * pixelDp
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val start = ColorUtils.setAlphaComponent(high, opacity)
        val end = ColorUtils.setAlphaComponent(colour, opacity)
        paint.shader = LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), start, end, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        if (glass) {
            val glow = ColorUtils.setAlphaComponent(scheme.primary.toArgb(), if (night) 33 else 15)
            paint.shader = RadialGradient(bounds.width() * .70f, bounds.height() * .46f,
                maxOf(1f, minOf(bounds.width(), bounds.height()) * .59f), glow, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (active) 2f else 1f) * pixelDp
        paint.color = if (active) scheme.primary.toArgb() else scheme.outlineVariant.toArgb()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        return bitmap
    }
}

internal object EverydayToolkitWidgetViews {
    private val dayIds = listOf(R.id.toolkit_widget_day1, R.id.toolkit_widget_day2, R.id.toolkit_widget_day3,
        R.id.toolkit_widget_day4, R.id.toolkit_widget_day5, R.id.toolkit_widget_day6, R.id.toolkit_widget_day7)

    fun render(context: Context, instance: EverydayWidgetInstance, snapshot: EverydaySnapshot, face: EverydayCardFace,
        width: Int, height: Int, lockHost: Boolean, preview: Boolean, unavailable: Boolean, locale: Locale, ratio: Float,
        scheme: ColorScheme, label: (String) -> String, freshGlow: Boolean, onOpen: (EverydayTarget) -> PendingIntent?): RemoteViews {
        val config = instance.config

        val kinds = EverydayToolkitWidgets.stackKinds(snapshot)
        val swipe = config.kind == EverydayCardKind.SMART_STACK && Build.VERSION.SDK_INT >= 31 && !lockHost &&
            width >= 180 && height >= 115 && !preview && !unavailable
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
            val rotate = config.widgetAutoPage && !snapshot.kids && android.animation.ValueAnimator.areAnimatorsEnabled() &&
                EverydayWidgetMotion.level(context, config.widgetMotion, snapshot.kids) != com.halo.app.ui.theme.HaloLookMotion.STILL
            return RemoteViews(context.packageName,
                if (rotate) R.layout.halo_toolkit_stack_auto else R.layout.halo_toolkit_stack).apply {
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
        val collapsedStack = config.kind == EverydayCardKind.SMART_STACK && !unavailable
        val faceConfig = if (collapsedStack) config.copy(kind = kinds.first()) else config
        val visibleFace = if (collapsedStack && width < 180) face.copy(title = label(EverydayCards.title(config.kind))) else face
        return faceView(context, faceConfig, snapshot, visibleFace, width, height, locale, ratio, scheme, lockHost, label,
            preview = preview, freshGlow = freshGlow).apply {
            if (!preview) setOnClickPendingIntent(R.id.toolkit_widget_root, onOpen(face.target))
        }
    }

    private fun faceView(context: Context, config: EverydayCardConfig, snapshot: EverydaySnapshot, face: EverydayCardFace,
        width: Int, height: Int, locale: Locale, ratio: Float, scheme: ColorScheme, lockHost: Boolean, label: (String) -> String,
        stackIndex: Int = 0, stackSize: Int = 0, preview: Boolean = false, freshGlow: Boolean = false): RemoteViews {
        val kind = config.kind
        val accent = scheme.primary.toArgb()
        val foreground = scheme.onSurface.toArgb()
        val muted = scheme.onSurfaceVariant.toArgb()
        val contrast = context.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE).getBoolean("contrast", false)
        val scale = context.resources.configuration.fontScale * ratio
        val compact = lockHost || height < 100 || width < 110 || scale >= 1.60f
        // The launcher owns the physical bounds; a saved size can limit the content inside them.
        val contentWidth = minOf(width, config.widgetSize?.widthDp ?: width)
        val contentHeight = minOf(height, config.widgetSize?.heightDp ?: height)
        val wrapCompact = compact && contentHeight >= 65 * scale
        val showWeek = kind == EverydayCardKind.PLANNER_WEEK && config.widgetShows in
            setOf(EverydayWidgetShows.DEFAULT, EverydayWidgetShows.THIS_WEEK) && contentWidth >= 200 &&
            contentHeight >= 132 && scale < 1.35f
        val runningFocus = snapshot.widgetFocus?.takeIf { kind == EverydayCardKind.FOCUS }
        val learning = snapshot.widgetLearning?.steps?.coerceIn(0, 5)?.takeIf { kind == EverydayCardKind.LEARN && !snapshot.kids }
        val largeFocus = runningFocus != null && !compact && contentHeight >= 132 * scale
        val largeLearn = learning != null && !compact && contentHeight >= 142 * scale && contentWidth >= 122
        val ring = largeFocus || largeLearn
        val attempts = snapshot.widgetChildAttempts.takeIf { kind == EverydayCardKind.KIDS_SKY && snapshot.kids } ?: 0
        val marks = when {
            kind == EverydayCardKind.KIDS_SKY && snapshot.kids -> (0 until 5).joinToString(" ") { if (it < attempts) "★" else "☆" }
            kind == EverydayCardKind.LITTLE_HABITS && !snapshot.kids ->
                snapshot.widgetHabits.take(minOf(2, config.widgetSize?.rowLimit ?: 2)).joinToString("\n") { days ->
                    days.joinToString(" ") { if (it) "●" else "○" }
                }
            else -> ""
        }
        val spokenMarks = if (kind != EverydayCardKind.KIDS_SKY) null else context.getString(
            if (attempts == 1) R.string.widget_toolkit_one_star else R.string.widget_toolkit_many_stars, attempts)
        val isTool = (face.target as? EverydayTarget.WidgetTool)?.tool?.let { it != EverydayWidgetTool.KIDS } == true
        val action = if (!compact && !snapshot.kids && scale <= 1.35f && contentHeight >= 132 * scale && isTool) when (kind) {
            EverydayCardKind.SCAN -> face.value
            EverydayCardKind.RECORD -> label("Open Recorder")
            EverydayCardKind.TRANSLATE -> face.value
            EverydayCardKind.LEARN -> if (largeLearn && contentHeight >= 156 * scale) label("Continue") else null
            else -> null
        } else null
        val actionForeground = if (kind == EverydayCardKind.SCAN || kind == EverydayCardKind.TRANSLATE || contrast)
            scheme.onPrimary.toArgb() else foreground
        val actionFill = if (kind == EverydayCardKind.SCAN || kind == EverydayCardKind.TRANSLATE || contrast) accent
            else ColorUtils.blendARGB(scheme.surfaceContainerHighest.toArgb(), accent, .14f)
        val value = when {
            kind == EverydayCardKind.SCAN && action != null -> label("Point & read")
            kind == EverydayCardKind.RECORD && action != null && !snapshot.widgetRecording -> label("Tap to record")
            kind == EverydayCardKind.TRANSLATE && action != null -> label("Translate text")
            showWeek && face.observedAt != null -> label("Next event") + " · " + face.value
            else -> face.value
        }
        val status = freshness(context, face, snapshot, locale)
        val detail = when {
            kind == EverydayCardKind.SCAN && action != null -> label("Read · Translate")
            kind == EverydayCardKind.RECORD && action != null && !snapshot.widgetRecording -> label("Words as you talk")
            kind == EverydayCardKind.OUR_HOME && snapshot.widgetHomeCount > 0 && !snapshot.kids ->
                label("Done") + " · " + java.text.NumberFormat.getIntegerInstance(locale).format(snapshot.widgetHomeDone)
            else -> face.rows.firstOrNull()?.title.orEmpty()
        }
        val wideDetail = when (kind) {
            EverydayCardKind.SCAN, EverydayCardKind.RECORD -> contentHeight >= 150 * scale
            else -> contentHeight >= 126 * scale
        }
        val showDetail = !compact && wideDetail && detail.isNotBlank() && scale < 1.45f
        val footerSpace = when {
            action != null -> 172
            stackSize > 0 || showWeek || ring -> 153
            marks.isNotBlank() -> 142
            wrapCompact -> 94
            compact -> 68
            else -> 96
        }
        val showStatus = contentHeight >= footerSpace * scale && status.isNotBlank() &&
            status != value && status != action && (status != detail || !showDetail)
        val padding = (context.resources.displayMetrics.density * (if (compact) 8 else 14)).toInt()
        val valueSp = when {
            compact -> 16f
            showWeek -> 13f
            kind == EverydayCardKind.QUICK_NOTE -> 17f
            kind == EverydayCardKind.AIR_POLLEN -> 24f
            else -> 20f
        }
        val header = when {
            showWeek -> face.title + " · " + label("This week")
            kind == EverydayCardKind.FOCUS -> context.getString(R.string.halo_focus_title)
            else -> face.title
        }
        val showOrb = width >= 105 && !lockHost && (!wrapCompact || contentWidth >= 180)
        val headerSp = (if (compact) 10f else 11f) * ratio
        val headerLines = if (wrapCompact) 2 else 1
        val valueLines = if (showWeek || compact && !wrapCompact) 1 else 2
        val textWidth = width - 16 - if (stackSize > 0) 12 else 0
        val headerText = if (compact) fitCompactText(context, header, headerSp,
            textWidth - 20 - if (showOrb) 16 else 0, headerLines, 1.08f) else header.uppercase(locale)
        val valueText = if (compact) fitCompactText(context, value, valueSp * ratio, textWidth, valueLines) else value
        return RemoteViews(context.packageName, R.layout.halo_toolkit_widget).apply {
            setImageViewBitmap(R.id.toolkit_widget_background, EverydayWidgetAppearance.background(context,
                config, scheme, width, height, if (stackSize > 0) 256f else 512f,
                active = kind == EverydayCardKind.RECORD && snapshot.widgetRecording && !lockHost))
            val railSpace = if (stackSize > 0) (12 * context.resources.displayMetrics.density).toInt() else 0
            val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            setViewPadding(R.id.toolkit_widget_root, padding + if (rtl) railSpace else 0, padding,
                padding + if (rtl) 0 else railSpace, padding)
            setImageViewBitmap(R.id.toolkit_widget_glyph, EverydayWidgetGlyphs.bitmap(context,
                EverydayWidgetGlyphs.resource(kind), 14, muted))
            setImageViewBitmap(R.id.toolkit_widget_orb, nearbyOrb(context, (14 * context.resources.displayMetrics.density).toInt(), scheme = scheme))
            setViewVisibility(R.id.toolkit_widget_orb, if (showOrb) View.VISIBLE else View.GONE)
            text(R.id.toolkit_widget_title, headerText, muted, headerSp)
            setInt(R.id.toolkit_widget_title, "setMaxLines", headerLines)
            setContentDescription(R.id.toolkit_widget_title, header)

            val inlineClock = runningFocus != null && !preview && !largeFocus
            setViewVisibility(R.id.toolkit_widget_value, if (ring || inlineClock) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.toolkit_widget_countdown, if (inlineClock) View.VISIBLE else View.GONE)
            if (runningFocus != null && inlineClock) countdown(R.id.toolkit_widget_countdown, runningFocus, foreground, valueSp * ratio)
            text(R.id.toolkit_widget_value, valueText, foreground, valueSp * ratio)
            setInt(R.id.toolkit_widget_value, "setMaxLines", valueLines)

            setViewVisibility(R.id.toolkit_widget_ring_panel, if (ring) View.VISIBLE else View.GONE)
            if (ring) {
                val segments = if (largeLearn) 5 else 1
                val progress = if (largeLearn) requireNotNull(learning) / 5f
                    else requireNotNull(runningFocus).let { it.remainingMs.toFloat() / it.durationMs.coerceAtLeast(1L) }
                setImageViewBitmap(R.id.toolkit_widget_ring, dial(progress, accent, muted, segments))
                val liveRing = largeFocus && !preview
                setViewVisibility(R.id.toolkit_widget_ring_clock, if (liveRing) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.toolkit_widget_ring_label, if (liveRing) View.GONE else View.VISIBLE)
                if (liveRing) countdown(R.id.toolkit_widget_ring_clock, requireNotNull(runningFocus), foreground, 14f * ratio)
                else text(R.id.toolkit_widget_ring_label, if (largeLearn) face.value.removePrefix(label("Step") + " ") else face.value,
                    foreground, 14f * ratio)
            }

            setViewVisibility(R.id.toolkit_widget_detail, if (showDetail) View.VISIBLE else View.GONE)
            text(R.id.toolkit_widget_detail, detail, muted, 12f * ratio)
            setViewVisibility(R.id.toolkit_widget_status_row, if (showStatus) View.VISIBLE else View.GONE)
            val announceFresh = showStatus && freshGlow && face.observedAt != null && !face.stale && !face.lastRefreshFailed
            setViewVisibility(R.id.toolkit_widget_fresh_signal, if (announceFresh) View.VISIBLE else View.GONE)
            if (announceFresh && Build.VERSION.SDK_INT >= 31) setColorStateList(R.id.toolkit_widget_fresh_signal,
                "setIndeterminateTintList", android.content.res.ColorStateList.valueOf(accent))
            val credited = listOfNotNull(status, face.attribution).distinct().joinToString(" · ")
            text(R.id.toolkit_widget_status, credited, muted, 11f * ratio)
            if (!ring && !inlineClock && (valueText != value || !showStatus || !showDetail && detail.isNotBlank())) {
                setContentDescription(R.id.toolkit_widget_value, listOf(value, detail.takeUnless { showDetail }.orEmpty(),
                    credited.takeUnless { showStatus }.orEmpty()).filter(String::isNotBlank).distinct().joinToString(" · "))
            }
            setViewVisibility(R.id.toolkit_widget_action, if (action == null) View.GONE else View.VISIBLE)
            if (action != null) {
                setImageViewBitmap(R.id.toolkit_widget_action_background, capsule(actionFill))
                setImageViewBitmap(R.id.toolkit_widget_action_glyph, EverydayWidgetGlyphs.bitmap(context,
                    if (kind == EverydayCardKind.LEARN) R.drawable.halo_ic_play else EverydayWidgetGlyphs.resource(kind), 13, actionForeground))
                text(R.id.toolkit_widget_action_label, action, actionForeground, 13f * ratio)
            }

            setViewVisibility(R.id.toolkit_widget_week, if (showWeek) View.VISIBLE else View.GONE)
            if (showWeek) EverydayToolkitWidgets.week(snapshot, locale).forEachIndexed { index, day ->
                val id = dayIds[index]
                val longDay = day.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                text(id, day.label + "\n" + java.text.NumberFormat.getIntegerInstance(locale).format(day.day.dayOfMonth) + "\n" +
                    "•".repeat(day.eventCount.coerceAtMost(3)).padEnd(1, ' '),
                    if (day.today) ContextCompat.getColor(context, R.color.widget_toolkit_day_ink) else foreground, 12f * ratio)
                setInt(id, "setBackgroundResource", if (day.today) R.drawable.halo_widget_day_sage else R.drawable.halo_widget_day_cell)
                val dateLabel = if (day.today) context.getString(R.string.widget_toolkit_today_day, longDay) else longDay
                setContentDescription(id, context.getString(R.string.widget_toolkit_day_events, dateLabel, day.eventCount))
            }
            val marksVisible = marks.isNotBlank() && !compact && height >= 118 * scale
            setViewVisibility(R.id.toolkit_widget_marks, if (marksVisible) View.VISIBLE else View.GONE)
            if (marksVisible) {
                val spans = android.text.SpannableString(marks).apply {
                    forEachIndexed { index, glyph ->
                        val ink = when (glyph) {
                            '★', '●' -> accent
                            '☆', '○' -> muted
                            else -> return@forEachIndexed
                        }
                        setSpan(android.text.style.ForegroundColorSpan(ink), index, index + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                setTextViewText(R.id.toolkit_widget_marks, spans)
                setTextViewTextSize(R.id.toolkit_widget_marks, android.util.TypedValue.COMPLEX_UNIT_SP, 18f * ratio)
                setContentDescription(R.id.toolkit_widget_marks, spokenMarks ?: label("Days a routine was run"))
            }
            val showDots = stackSize > 0 && height >= (if (ring) 151 else 120) * scale
            setViewVisibility(R.id.toolkit_widget_dots, if (showDots) View.VISIBLE else View.GONE)
            if (showDots) {
                val dots = android.text.SpannableString((0 until stackSize).joinToString("\n") { "●" })
                val activeIndex = stackIndex * 2
                dots.setSpan(android.text.style.ForegroundColorSpan(accent), activeIndex, activeIndex + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setTextViewText(R.id.toolkit_widget_dots, dots)
                setTextColor(R.id.toolkit_widget_dots, muted)
                setTextViewTextSize(R.id.toolkit_widget_dots, android.util.TypedValue.COMPLEX_UNIT_SP, 9f * ratio)
                setContentDescription(R.id.toolkit_widget_dots, context.getString(R.string.widget_toolkit_stack_position,
                    stackIndex + 1, stackSize))
            }
        }
    }

    private fun RemoteViews.countdown(id: Int, state: WidgetFocus, color: Int, size: Float) {
        setChronometer(id, state.deadlineElapsed, null, true)
        setChronometerCountDown(id, true)
        setTextColor(id, color)
        setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun capsule(colour: Int): Bitmap = Bitmap.createBitmap(120, 32, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawRoundRect(RectF(0f, 0f, 120f, 32f), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
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

    private fun fitCompactText(context: Context, text: String, sizeSp: Float, widthDp: Int,
        maxLines: Int, glyphScale: Float = 1f): String {
        if (text.isBlank()) return text
        val metrics = context.resources.displayMetrics
        val width = ((widthDp - 4) * metrics.density).toInt().coerceAtLeast(1)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            typeface = Typeface.create(context.resources.getFont(R.font.sora_variable), Typeface.BOLD)
            textScaleX = glyphScale
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setIncludePad(false).build()
        if (layout.lineCount <= maxLines) return text
        val lastStart = layout.getLineStart(maxLines - 1)
        val lastEnd = layout.getLineEnd(maxLines - 1)
        val end = TextUtils.ellipsize(text.substring(lastStart, lastEnd).trimEnd() + "…", paint,
            width.toFloat(), TextUtils.TruncateAt.END).toString()
        return if (lastStart == 0) end else text.substring(0, lastStart).trimEnd() + "\n" + end
    }

    private fun RemoteViews.text(id: Int, value: String, color: Int, size: Float) {
        setTextViewText(id, value)
        setTextColor(id, color)
        setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun dial(progress: Float, color: Int, muted: Int, segments: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
        val frame = RectF(13f, 13f, 131f, 131f)
        val step = 360f / segments
        val gap = if (segments == 1) 0f else 14f
        repeat(segments) { index ->
            val start = -90f + index * step + gap / 2f
            paint.color = ColorUtils.setAlphaComponent(muted, 69)
            canvas.drawArc(frame, start, step - gap, false, paint)
            val visible = (progress * segments - index).coerceIn(0f, 1f)
            if (visible > 0f) {
                paint.color = color
                canvas.drawArc(frame, start, (step - gap) * visible, false, paint)
            }
        }
        return bitmap
    }
}
