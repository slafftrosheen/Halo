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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    override fun onDisabled(context: Context) = stopLearningWatch()

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private data class LearningFrame(val id: String, val completed: Int)
        @Volatile private var learning: LearningFrame? = null
        private var learningWatch: Job? = null
        private var listeningWatch: Job? = null
        @Volatile private var listening = false
        fun refreshAsync(context: Context) {
            runCatching { EverydayWidgetUpdates.enqueue(context.applicationContext) }
                .onFailure { android.util.Log.w("HaloWidget","Everyday widget refresh needs retry",it) }
            scope.launch { refreshAll(context.applicationContext) }
        }
        fun refreshAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, HaloNativeWidget::class.java))
                if (ids.isEmpty()) return
                ManualDependencyGraph.init(context)
                startLearningWatch(context.applicationContext)
                repaint(context)
            }.onFailure { android.util.Log.w("HaloWidget", "Could not update Halo widgets", it) }
        }

        private fun startLearningWatch(context: Context) = synchronized(this) {
            if (listeningWatch?.isActive != true) listeningWatch = scope.launch {
                com.halo.app.wallpaper.HaloWallpaperActivity.state
                    .map { it == com.halo.app.wallpaper.HaloAmbient.LISTENING }.distinctUntilChanged().collect { active ->
                        if (listening != active) {
                            listening = active
                            runCatching { repaint(context) }
                        }
                    }
            }
            if (learningWatch?.isActive == true) return@synchronized
            learningWatch = scope.launch {
                val graph = ManualDependencyGraph
                combine(graph.studyRepository.observeActiveSessions(), graph.studyLessonStore.widgetJourneyRevision,
                    graph.kidsPreferences.enabled, graph.kidsPreferences.loadedState) { sessions, _, kids, ready ->
                    if (!ready || kids) return@combine null
                    val active = sessions.maxByOrNull { it.updatedAtEpochMs }
                    if (active == null) {
                        val last = learning?.id?.let { graph.studyRepository.getSession(it) }
                        if (last?.lifecycle == com.halo.sdk.runtime.study.StudySessionLifecycle.COMPLETED)
                            LearningFrame(last.id, 5) else null
                    } else {
                        val stage = graph.studyLessonStore.loadJourney(active.id)?.stage
                            ?: com.halo.app.ui.screens.study.LearningStage.START
                        val count = if (stage == com.halo.app.ui.screens.study.LearningStage.CONTINUE) 5
                            else com.halo.app.ui.screens.study.learnPathStep(stage).ordinal
                        LearningFrame(active.id, count)
                    }
                }.distinctUntilChanged().collect { current ->
                    val previous = learning
                    learning = current
                    val from = if (current != null && previous != null && previous.id == current.id && previous.completed < current.completed)
                        previous.completed else null
                    runCatching { repaint(context, from) }.onFailure {
                        android.util.Log.w("HaloWidget", "Could not show Study widget progress", it)
                    }
                }
            }
        }

        private fun stopLearningWatch() = synchronized(this) {
            learningWatch?.cancel()
            learningWatch = null
            listeningWatch?.cancel()
            listeningWatch = null
            listening = false
            learning = null
        }

        private fun repaint(context: Context, studyMotionFrom: Int? = null) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, HaloNativeWidget::class.java)).forEach { id ->
                manager.updateAppWidget(id, renderViews(context, manager.getAppWidgetOptions(id), studyMotionFrom,
                    ManualDependencyGraph.kidsPreferences.enabled.value) { context.nativeUi(it) })
            }
        }

        private fun studyRing(size: Int, completed: Int, colour: Int, muted: Int): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = size * 0.09f
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val ring = android.graphics.RectF(size * .15f, size * .15f, size * .85f, size * .85f)
            repeat(5) { index ->
                paint.color = if (index < completed) colour else muted
                canvas.drawArc(ring, -86f + index * 72f, 57f, false, paint)
            }
            return bitmap
        }

        internal fun views(context: Context, options: Bundle,
            kids: Boolean = ManualDependencyGraph.kidsPreferences.enabled.value,
            label: (String) -> String = { context.nativeUi(it) }): RemoteViews =
            renderViews(context, options, null, kids, label)

        private fun renderViews(context: Context, options: Bundle, studyMotionFrom: Int?, kids: Boolean, label: (String) -> String): RemoteViews {
            if (com.halo.app.privacy.HaloPauseOwner.get(context).isPaused()) return haloPausedWidget(context, null)
            // Modern launchers supply actual portrait/landscape sizes. Render each
            // independently instead of combining unrelated minimum dimensions.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    .distinct().take(16)
                if (sizes.isNotEmpty()) return RemoteViews(sizes.associateWith { size ->
                    viewsForSize(context, size.width.toInt(), size.height.toInt(), studyMotionFrom, kids, label)
                })
            }
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(120)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240).coerceAtLeast(96)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            return if (maxWidth > width || maxHeight > height) RemoteViews(
                viewsForSize(context, maxWidth, height, studyMotionFrom, kids, label),
                viewsForSize(context, width, maxHeight, studyMotionFrom, kids, label),
            ) else viewsForSize(context, width, height, studyMotionFrom, kids, label)
        }

        private fun widgetIcon(context: Context, resource: Int, tint: Int, size: Int): android.graphics.Bitmap {
            val drawable = requireNotNull(androidx.core.content.ContextCompat.getDrawable(context, resource)).mutate()
            drawable.setTint(tint)
            val bitmap = android.graphics.Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(android.graphics.Canvas(bitmap))
            return bitmap
        }

        private fun viewsForSize(context: Context, requestedWidth: Int, requestedHeight: Int, studyMotionFrom: Int?, kids: Boolean, label: (String) -> String): RemoteViews {
            val width = requestedWidth.coerceAtLeast(72)
            val height = requestedHeight.coerceAtLeast(72)
            val narrow = width < 240 || context.resources.configuration.fontScale >= 1.2f
            val wide = !narrow && width >= 240 && height >= 160
            val config = com.halo.app.everyday.presentation.EverydayCardConfig(com.halo.app.everyday.presentation.EverydayCardKind.QUICK_ACTIONS)
            val scheme = EverydayWidgetAppearance.scheme(context, config)
            val density = context.resources.displayMetrics.density
            return RemoteViews(context.packageName, if (narrow) R.layout.halo_native_widget_narrow else if (wide) R.layout.halo_native_widget_wide else R.layout.halo_native_widget).apply {
                setImageViewBitmap(R.id.halo_widget_background,
                    EverydayWidgetAppearance.background(context, config, scheme, width, height, pixelsLimit = 384f))
                setImageViewBitmap(R.id.halo_widget_orb, nearbyOrb(context, (48*density).toInt(), scheme = scheme))
                setContentDescription(R.id.halo_widget_orb, label("Open Mini Halo"))
                val animated = EverydayWidgetMotion.level(context, kids = kids) ==
                    com.halo.app.ui.theme.HaloLookMotion.FULL
                val askAction = MiniHaloEntry.pending(context, 8000)
                setOnClickPendingIntent(R.id.halo_widget_orb, askAction)
                setViewVisibility(R.id.halo_widget_orb, if (animated) android.view.View.GONE else android.view.View.VISIBLE)
                val animatedListening = animated && listening
                setViewVisibility(R.id.halo_widget_orb_motion, if (animated && !animatedListening) android.view.View.VISIBLE else android.view.View.GONE)
                setViewVisibility(R.id.halo_widget_orb_listening, if (animatedListening) android.view.View.VISIBLE else android.view.View.GONE)
                setOnClickPendingIntent(R.id.halo_widget_orb_container, if (animated) askAction else null)
                setContentDescription(R.id.halo_widget_orb_container, if (animated) label("Open Mini Halo") else null)
                if (animated && android.os.Build.VERSION.SDK_INT >= 31) {
                    setColorStateList(if (animatedListening) R.id.halo_widget_orb_listening else R.id.halo_widget_orb_motion, "setIndeterminateTintList",
                        android.content.res.ColorStateList.valueOf(scheme.primary.toArgb()))
                }
                listOf(R.id.halo_widget_title to "halo", R.id.halo_widget_talk to "Talk", R.id.halo_widget_open to "Open Halo").forEach { (id,key) ->
                    setTextViewText(id, label(key)); setTextColor(id, if (id == R.id.halo_widget_talk) scheme.onPrimaryContainer.toArgb() else scheme.onSurface.toArgb())
                }
                setOnClickPendingIntent(R.id.halo_widget_talk, MiniHaloEntry.pending(context, 8000))
                setOnClickPendingIntent(R.id.halo_widget_open, MiniHaloEntry.pending(context, 8001, "home"))
                setTextViewText(R.id.halo_widget_capture, label("Capture"))
                setTextColor(R.id.halo_widget_capture, scheme.primary.toArgb())
                setOnClickPendingIntent(R.id.halo_widget_capture, MiniHaloEntry.pending(context, 8002, draft = "open camera"))
                setOnClickPendingIntent(R.id.halo_widget_title, MiniHaloEntry.pending(context, 8001, "home"))
                if (wide) {
                    setTextViewText(R.id.halo_widget_capture, label("Capture"))
                    setTextViewText(R.id.halo_widget_study, label("Continue learning"))
                    setTextColor(R.id.halo_widget_capture, scheme.primary.toArgb()); setTextColor(R.id.halo_widget_study, scheme.onSurface.toArgb())
                    setOnClickPendingIntent(R.id.halo_widget_capture, MiniHaloEntry.pending(context, 8002, draft = "open camera"))
                    setOnClickPendingIntent(R.id.halo_widget_study, MiniHaloEntry.pending(context, 8003, "study"))
                }
                val actionIds = if (wide) listOf(R.id.halo_widget_talk, R.id.halo_widget_capture, R.id.halo_widget_study)
                    else listOf(R.id.halo_widget_talk, R.id.halo_widget_capture)
                actionIds.forEach { id ->
                    val primary = id == R.id.halo_widget_talk
                    setTextColor(id, if (primary || android.os.Build.VERSION.SDK_INT < 31) scheme.onPrimaryContainer.toArgb() else scheme.onSurface.toArgb())
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        setColorStateList(id, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(
                            if (primary) scheme.primaryContainer.toArgb() else scheme.surfaceContainerHigh.toArgb()))

                    }
                }
                // Tint bitmaps through the supported ImageView API. TextView's
                // compound-drawable tint setter is not a RemoteViews method on
                // launchers such as MIUI, and would reject the entire widget.
                listOf(Triple(R.id.halo_widget_talk_icon, R.drawable.halo_ic_mic, scheme.onPrimaryContainer),
                    Triple(R.id.halo_widget_capture_icon, R.drawable.halo_ic_camera, if (android.os.Build.VERSION.SDK_INT >= 31) scheme.primary else scheme.onPrimaryContainer)).forEach { (id, icon, tint) ->
                    setImageViewBitmap(id, widgetIcon(context, icon, tint.toArgb(), (24*density).toInt()))
                }
                if (wide) {
                    val iconTint = (if (android.os.Build.VERSION.SDK_INT >= 31) scheme.primary else scheme.onPrimaryContainer).toArgb()
                    val sizePx = (24 * density).toInt()
                    val progress = if (kids) null else learning
                    val latest = if (progress != null) studyRing(sizePx, progress.completed,
                        iconTint, scheme.outlineVariant.toArgb()) else widgetIcon(context, R.drawable.halo_ic_study, iconTint, sizePx)
                    val level = EverydayWidgetMotion.level(context, kids = kids)
                    val transition = studyMotionFrom != null && progress != null
                    val full = transition && level == com.halo.app.ui.theme.HaloLookMotion.FULL
                    val gentle = transition && level == com.halo.app.ui.theme.HaloLookMotion.GENTLE
                    setViewVisibility(R.id.halo_widget_study_icon, if (full || gentle) android.view.View.GONE else android.view.View.VISIBLE)
                    setViewVisibility(R.id.halo_widget_study_motion, if (full) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_study_gentle_motion, if (gentle) android.view.View.VISIBLE else android.view.View.GONE)
                    setImageViewBitmap(R.id.halo_widget_study_icon, latest)
                    if ((full || gentle) && studyMotionFrom != null) {
                        val old = if (full) R.id.halo_widget_study_old else R.id.halo_widget_study_gentle_old
                        val next = if (full) R.id.halo_widget_study_new else R.id.halo_widget_study_gentle_new
                        val flipper = if (full) R.id.halo_widget_study_motion else R.id.halo_widget_study_gentle_motion
                        setImageViewBitmap(old, studyRing(sizePx, studyMotionFrom, iconTint, scheme.outlineVariant.toArgb()))
                        setImageViewBitmap(next, latest)
                        setDisplayedChild(flipper, 0)
                        setDisplayedChild(flipper, 1)
                    }
                }
                // Keep complete labels and 48dp targets; short or enlarged widgets hide
                // secondary actions rather than clipping them below the launcher bounds.
                val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 16 * context.resources.displayMetrics.scaledDensity
                    typeface = context.resources.getFont(R.font.atkinson_hyperlegible_next_variable)
                }
                fun needed(key: String, availableWidth: Int): Int {
                    val text = label(key)
                    return maxOf((48*density).toInt(), android.text.StaticLayout.Builder.obtain(
                        text, 0, text.length, textPaint, (availableWidth*density).toInt().coerceAtLeast(1)).build().height)
                }
                setViewVisibility(R.id.halo_widget_open, android.view.View.GONE)
                if (narrow) {
                    // Keep enlarged single-word labels intact. A narrow host can
                    // trade the decorative glyph for text space, never shrink text.
                    listOf(Triple(R.id.halo_widget_talk, R.id.halo_widget_talk_icon, "Talk"),
                        Triple(R.id.halo_widget_capture, R.id.halo_widget_capture_icon, "Capture")).forEach { (id, icon, key) ->
                        if (textPaint.measureText(label(key)) > (width - 72) * density) {
                            setViewVisibility(icon, android.view.View.GONE)
                            setViewPadding(id, (4*density).toInt(), 0, (4*density).toInt(), 0)
                        } else {
                            setViewVisibility(icon, android.view.View.VISIBLE)
                            setViewPadding(id, (44*density).toInt(), 0, (12*density).toInt(), 0)
                        }
                    }
                    val remaining = ((height - 64)*density).toInt()
                    val talkHeight = needed("Talk", width-72)
                    val titleLayout = android.text.StaticLayout.Builder.obtain(label("halo"), 0, label("halo").length,
                        android.text.TextPaint(textPaint).apply {
                            textSize = 18 * context.resources.displayMetrics.scaledDensity
                            typeface = context.resources.getFont(R.font.sora_variable)
                        }, ((width-80)*density).toInt().coerceAtLeast(1)).build()
                    setViewVisibility(R.id.halo_widget_title, if (titleLayout.lineCount == 1 && titleLayout.height <= 48*density) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_talk_container, if (remaining >= talkHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture_container, if (remaining-talkHeight-4*density >= needed("Capture", width-72)) android.view.View.VISIBLE else android.view.View.GONE)
                } else if (wide) {
                    setViewVisibility(R.id.halo_widget_title, android.view.View.VISIBLE)
                    val remaining = ((height - 88)*density).toInt()
                    val rowHeight = maxOf(needed("Talk", (width-40)/2-48), needed("Capture", (width-40)/2-48))
                    val fitsRow = remaining >= rowHeight
                    setViewVisibility(R.id.halo_widget_talk_container, if (fitsRow) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture_container, if (fitsRow) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_study_container, if (fitsRow && remaining-rowHeight-8*density >= needed("Continue learning", width-80)) android.view.View.VISIBLE else android.view.View.GONE)
                } else {
                    val available = ((height-24)*density).toInt()
                    // Short widgets prioritize whole translated labels. Capture
                    // takes its measured content width; Talk receives the rest.
                    // Restore both states explicitly for launcher reapply/resize.
                    val showIcons = width >= 360
                    val textPadding = if (showIcons) 56 else 8
                    listOf(R.id.halo_widget_talk to R.id.halo_widget_talk_icon,
                        R.id.halo_widget_capture to R.id.halo_widget_capture_icon).forEach { (id, icon) ->
                        setViewVisibility(icon, if (showIcons) android.view.View.VISIBLE else android.view.View.GONE)
                        setViewPadding(id, ((if (showIcons) 44 else 4)*density).toInt(), 0,
                            ((if (showIcons) 12 else 4)*density).toInt(), 0)
                    }
                    val captureWidth = maxOf(48, kotlin.math.ceil(textPaint.measureText(label("Capture"))/density).toInt()+textPadding)
                    val captureTextWidth = captureWidth-textPadding
                    val captureFits = captureWidth <= width-120 && available >= needed("Capture", captureTextWidth)
                    val talkWidth = width-80-if (captureFits) captureWidth else 0
                    val talkTextWidth = talkWidth-textPadding
                    val talkHeight = needed("Talk", talkTextWidth)
                    val titleHeight = android.text.StaticLayout.Builder.obtain(label("halo"),0,label("halo").length,
                        android.text.TextPaint(textPaint).apply {
                            textSize = 18*context.resources.displayMetrics.scaledDensity
                            typeface = context.resources.getFont(R.font.sora_variable)
                        },
                        (talkWidth*density).toInt()).build().height
                    setViewVisibility(R.id.halo_widget_title, if (available >= talkHeight+titleHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_talk_container, if (available >= talkHeight) android.view.View.VISIBLE else android.view.View.GONE)
                    setViewVisibility(R.id.halo_widget_capture_container, if (captureFits) android.view.View.VISIBLE else android.view.View.GONE)
                }
            }
        }
    }
}
