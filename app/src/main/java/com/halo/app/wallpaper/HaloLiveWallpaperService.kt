package com.halo.app.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.VisualPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.theme.AppearanceMode
import java.util.Calendar

internal const val WALLPAPER_FRAME_NS = 33_333_334L

/** Decorative state only. A wallpaper never receives text, identities, or task details. */
internal object HaloWallpaperActivity {
    private val mutable = MutableStateFlow(HaloAmbient.IDLE)
    val state = mutable.asStateFlow()
    fun show(ambient: HaloAmbient) { mutable.value = ambient }
}

internal fun haloWallpaperTime(): Float = Calendar.getInstance().let {
    (it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) / 1440f
}

internal fun wallpaperQuietTime(time: Float): Boolean = time >= 22f / 24f || time < 7f / 24f

internal fun wallpaperMotionSpeed(motion: HaloLookMotion, night: Boolean): Float {
    val speed = when (motion) {
        HaloLookMotion.FULL -> 1f
        HaloLookMotion.GENTLE -> .5f
        HaloLookMotion.STILL -> 0f
    }
    return speed * if (night) .5f else 1f
}

internal fun wallpaperMotionAllowed(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return power.isInteractive && !power.isPowerSaveMode &&
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}

class HaloLiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = HaloEngine()

    private inner class HaloEngine : Engine(), Choreographer.FrameCallback {
        private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val choreographer = Choreographer.getInstance()
        private val painter = HaloWallpaperPainter()
        private val choices by lazy { WallpaperPreferences.get(applicationContext) }
        private var visible = false
        private var surfaceReady = false
        private var interactiveMotion = false
        private var motion = HaloLookMotion.STILL
        private var ambient = HaloAmbient.IDLE
        private var accent = 0xFF0CF2BA.toInt()
        private var time = 0f
        private var night = false
        private var lastFrame = 0L
        private var elapsed = 0f
        private var listeningSince = 0f
        private val periodLatch = WallpaperPeriodLatch(wallpaperPeriod(haloWallpaperTime()))
        private var appliedSchedule: WallpaperSchedule? = null
        private val photoStore by lazy { WallpaperPhotoStore(applicationContext) }
        private var wall = HaloWallpaper.DRIFT
        private var photoStyle: WallpaperPhoto? = null
        private var photo: android.graphics.Bitmap? = null
        private var photoLoad: Job? = null
        private var environment = WallpaperEnvironment()
        private var sky: com.halo.app.wallpaper.v13.SkyState? = null
        private var skyEnvironment: WallpaperEnvironment? = null
        private var environmentStyle: HaloWallpaper? = null
        private var environmentRead: Job? = null
        private var runtimeAmbient = HaloAmbient.IDLE
        private var runtimeAmbientRead: Job? = null
        private var useRuntimeAmbient = false
        private var offsetX = 0
        private var colorStamp: ColorStamp? = null

        private val changes = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { redraw() }
        }
        private val animationScale = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { redraw() }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            ContextCompat.registerReceiver(this@HaloLiveWallpaperService, changes, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animationScale)
            main.launch { choices.state.collect { redraw() } }
            main.launch { Graph.haloPause.state.collect { redraw() } }
            main.launch { VisualPreferences.get(applicationContext).state.collect {
                motion = it.motionLevel
                redraw()
            } }
            main.launch { HaloWallpaperActivity.state.collect {
                ambient = it
                listeningSince = elapsed
                redraw()
            } }
            main.launch {
                combine(Graph.palettePreferences.state, Graph.appearancePreferences.mode) { palettes, appearance -> palettes to appearance }
                    .collect { (palettes, appearance) ->
                        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        val dark = when (appearance) {
                            AppearanceMode.DARK -> true
                            AppearanceMode.LIGHT -> false
                            AppearanceMode.SYSTEM -> systemDark
                        }
                        val palette = if (dark) palettes.dark else palettes.light
                        accent = com.halo.app.ui.theme.brandAccent(dark, palette.accent).fill.toArgb()
                        redraw()
                    }
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            redraw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = width > 0 && height > 0
            redraw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            choreographer.removeFrameCallback(this)
            redraw()
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int) {
            offsetX = -xPixelOffset
            redraw()
        }

        override fun onDestroy() {
            visible = false
            choreographer.removeFrameCallback(this)
            main.cancel()
            contentResolver.unregisterContentObserver(animationScale)
            unregisterReceiver(changes)
            super.onDestroy()
        }

        private fun redraw() {
            choreographer.removeFrameCallback(this)
            val power = getSystemService(PowerManager::class.java)
            val watching = visible && surfaceReady && power?.isInteractive == true
            time = haloWallpaperTime()
            val option = choices.state.value.current
            val period = periodLatch.update(wallpaperPeriod(time), watching && !isPreview,
                SystemClock.elapsedRealtime(), scheduleChanged = appliedSchedule != option.schedule)
            appliedSchedule = option.schedule
            wall = if (isPreview) option.wallpaperAt(wallpaperPeriod(time)) else option.wallpaperAt(period)
            val reactive = watching && option.reactsToHalo
            if (reactive != useRuntimeAmbient) {
                useRuntimeAmbient = reactive
                runtimeAmbientRead?.cancel()
                runtimeAmbient = HaloAmbient.IDLE
                runtimeAmbientRead = if (reactive) main.launch {
                    combine(Graph.haloSpeechOutputCoordinator.session.state, Graph.activeTurnCoordinator.activeTurn,
                        Graph.interactionSurfaceStore.active, Graph.kidsPreferences.enabled, Graph.haloPause.state) { audio, turn, surfaces, kids, pause ->
                        when {
                            kids || pause.paused -> HaloAmbient.IDLE
                            surfaces.any { it.inputRequestId != null && it.expiresAt?.isAfter(java.time.Instant.now()) != false } -> HaloAmbient.NEEDS_YOU
                            audio is com.halo.app.audio.HaloAudioSessionState.Listening -> HaloAmbient.LISTENING
                            audio is com.halo.app.audio.HaloAudioSessionState.Thinking || turn != null -> HaloAmbient.WORKING
                            else -> HaloAmbient.IDLE
                        }
                    }.distinctUntilChanged().collect {
                        runtimeAmbient = it
                        listeningSince = elapsed
                        redraw()
                    }
                } else null
            }
            val environmentTarget = wall.takeIf { watching && (option.followSky || it.usesEnvironment) }
            if (environmentStyle != environmentTarget) {
                environmentStyle = environmentTarget
                environmentRead?.cancel()
                environment = WallpaperEnvironment()
                environmentRead = environmentTarget?.let { style ->
                    main.launch {
                        wallpaperEnvironment(style != HaloWallpaper.LUMEN_GARDEN, style == HaloWallpaper.LUMEN_GARDEN).collect {
                            environment = it
                            redraw()
                        }
                    }
                }
            }
            val selectedPhoto = option.photo.takeIf { wall == HaloWallpaper.YOUR_PHOTO }
            if (photoStyle?.id != selectedPhoto?.id || photoStyle?.blur != selectedPhoto?.blur) {
                photoLoad?.cancel()
                photo = null
                photoStyle = selectedPhoto
                photoLoad = selectedPhoto?.let { style ->
                    main.launch {
                        photo = photoStore.render(style)
                        redraw()
                    }
                }
            } else photoStyle = selectedPhoto
            if (!watching) {
                sky = null
                return
            }
            if (!option.followSky) sky = null
            else if (sky == null || skyEnvironment != environment || motion == HaloLookMotion.FULL || isPreview) {
                skyEnvironment = environment
                sky = environment.skyState()
            }
            interactiveMotion = !wall.nearStill && motion != HaloLookMotion.STILL && !Graph.haloPause.isPaused() &&
                (!option.followSky || motion == HaloLookMotion.FULL) && wallpaperMotionAllowed(this@HaloLiveWallpaperService)
            val lockScreen = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            night = option.calmerAtNight && !isPreview &&
                (lockScreen || wallpaperQuietTime(time))
            val colors = ColorStamp(wall, accent, wallpaperPeriod(time), environment.season, photo)
            if (colorStamp != colors && Build.VERSION.SDK_INT >= 27) {
                colorStamp = colors
                notifyColorsChanged()
            }
            lastFrame = 0L
            draw()
            if (interactiveMotion) choreographer.postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible || !surfaceReady || !interactiveMotion) return
            if (frameTimeNanos - lastFrame >= WALLPAPER_FRAME_NS) {
                if (lastFrame != 0L) elapsed += (frameTimeNanos - lastFrame).toFloat().coerceAtMost(150_000_000f) / 1e9f *
                    wallpaperMotionSpeed(motion, night)
                lastFrame = frameTimeNanos
                draw()
            }
            choreographer.postFrameCallback(this)
        }

        private fun draw() {
            if (!surfaceHolder.surface.isValid) return
            val canvas = try { surfaceHolder.lockHardwareCanvas() } catch (_: RuntimeException) {
                try { surfaceHolder.lockCanvas() } catch (_: RuntimeException) { null }
            } ?: return
            try {
                val option = choices.state.value.current
                val animationTime = if (wall.nearStill) 0f else elapsed
                val strength = if (interactiveMotion) ((elapsed - listeningSince) / .6f).coerceIn(0f, 1f) else 1f
                val state = if (option.reactsToHalo && !Graph.haloPause.isPaused() && !Graph.kidsPreferences.enabled.value)
                    runtimeAmbient.takeUnless { it == HaloAmbient.IDLE } ?: ambient else HaloAmbient.IDLE
                val displayWidth = resources.displayMetrics.widthPixels
                val viewportWidth = if (isPreview || displayWidth <= 0) canvas.width else minOf(displayWidth, canvas.width)
                val viewportLeft = offsetX.coerceIn(0, (canvas.width - viewportWidth).coerceAtLeast(0))
                canvas.drawColor(Color.BLACK)
                canvas.save()
                canvas.translate(viewportLeft.toFloat(), 0f)
                painter.paint(canvas, wall, animationTime, time, accent, state, strength,
                    ripples = interactiveMotion, dim = if (night) .25f else 0f,
                    width = viewportWidth.toFloat(), height = canvas.height.toFloat(),
                    environment = environment, photo = photo, ring = photoStyle?.ring ?: WallpaperRing.CENTER,
                    ambientTime = (elapsed - listeningSince).coerceAtLeast(0f), sky = sky)
                canvas.restore()
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        @androidx.annotation.RequiresApi(27)
        override fun onComputeColors(): WallpaperColors {
            val bitmap = Bitmap.createBitmap(96, 160, Bitmap.Config.ARGB_8888)
            return try {
                painter.paint(Canvas(bitmap), wall, 0f, time, accent, HaloAmbient.IDLE, 1f, false, 0f,
                    environment = environment, photo = photo, ring = photoStyle?.ring ?: WallpaperRing.CENTER)
                WallpaperColors.fromBitmap(bitmap)
            } finally { bitmap.recycle() }
        }
    }

    private data class ColorStamp(val wallpaper: HaloWallpaper, val accent: Int, val time: WallpaperPeriod,
        val season: WallpaperSeason, val photo: Bitmap?)
}
