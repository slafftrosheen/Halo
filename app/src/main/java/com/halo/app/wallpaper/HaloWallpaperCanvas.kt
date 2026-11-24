package com.halo.app.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.LocalHaloAccentInk
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.theme.LocalHaloDecorationPaused
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.flow.collect

@Composable
internal fun HaloWallpaperCanvas(
    options: WallpaperOptions,
    modifier: Modifier,
    previewTime: Float? = null,
    animate: Boolean = true,
    useSchedule: Boolean = false,
    previewAmbient: HaloAmbient? = null,
    sharedPreview: WallpaperPreviewClock? = null,
    kidsSky: Boolean = false,
    motionScale: Float = 1f,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motion = rememberVisualPreferences().motionLevel
    val ambient = if (options.reactsToHalo && previewAmbient == null)
        HaloWallpaperActivity.state.collectAsStateWithLifecycle().value else previewAmbient ?: HaloAmbient.IDLE
    val accent = LocalHaloAccentInk.current.toArgb()
    var clock by remember { mutableFloatStateOf(haloWallpaperTime()) }
    val latch = remember(options.schedule) { WallpaperPeriodLatch(wallpaperPeriod(haloWallpaperTime())) }
    var period by remember(latch) { mutableStateOf(latch.period) }
    val wall = if (useSchedule || options.followSky) options.wallpaperAt(period) else options.wallpaper
    var skyRefresh by remember { mutableIntStateOf(0) }
    val powerAllowsMotion = rememberWallpaperMotionPermission(animate)
    if (animate || useSchedule || options.followSky) DisposableEffect(context, lifecycle, useSchedule, latch, motion) {
        fun updateClock(visible: Boolean) {
            clock = haloWallpaperTime()
            if (useSchedule) period = latch.update(wallpaperPeriod(clock), visible, SystemClock.elapsedRealtime())
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    intent?.action != Intent.ACTION_SCREEN_OFF
                updateClock(visible)
                if (motion == HaloLookMotion.FULL || intent?.action == Intent.ACTION_SCREEN_ON) skyRefresh++
            }
        }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> { updateClock(true); skyRefresh++ }
            Lifecycle.Event.ON_PAUSE -> updateClock(false)
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    val renderedTime = previewTime ?: clock
    val night = options.calmerAtNight && wallpaperQuietTime(renderedTime)
    val paused = LocalHaloDecorationPaused.current
    val moving = !wall.nearStill && !paused && (!options.followSky || motion == HaloLookMotion.FULL) &&
        (sharedPreview?.moving ?: (animate && powerAllowsMotion && motion != HaloLookMotion.STILL))
    val speed = wallpaperMotionSpeed(motion, night) * motionScale
    val ownClock = rememberWallpaperFrames(moving && sharedPreview == null, speed, WALLPAPER_FRAME_NS)
    val elapsed = sharedPreview?.elapsed ?: ownClock
    var ambientStart by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ambient) { ambientStart = elapsed.value }
    val weather = options.followSky || (animate || useSchedule || kidsSky) && wall.usesEnvironment && wall != HaloWallpaper.LUMEN_GARDEN
    val garden = (animate || useSchedule || kidsSky) && wall == HaloWallpaper.LUMEN_GARDEN
    val environment by produceState(WallpaperEnvironment(), lifecycle, weather, garden, kidsSky) {
        value = WallpaperEnvironment()
        if (weather || garden) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            wallpaperEnvironment(weather, garden, kidsSky).collect { value = it }
        }
    }
    val photoStore = remember(context) { WallpaperPhotoStore(context) }
    val stylePhoto = options.photo.takeIf { wall == HaloWallpaper.YOUR_PHOTO }
    val photo by produceState<android.graphics.Bitmap?>(null, photoStore, stylePhoto?.id, stylePhoto?.blur) {
        value = null
        value = photoStore.render(stylePhoto)
    }
    val painter = remember { HaloWallpaperPainter() }
    val sky = remember(options.followSky, environment, previewTime, skyRefresh) {
        if (options.followSky) environment.skyState(previewTime) else null
    }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            painter.paint(canvas.nativeCanvas, wall, if (wall.nearStill) 0f else elapsed.value, renderedTime, accent,
                if (options.reactsToHalo) ambient else HaloAmbient.IDLE, 1f,
                ripples = moving && sharedPreview == null, dim = if (night) .25f else 0f,
                width = size.width, height = size.height, environment = environment, photo = photo,
                ring = stylePhoto?.ring ?: WallpaperRing.CENTER, ambientTime = (elapsed.value - ambientStart).coerceAtLeast(0f),
                sky = sky)
        }
    }
}

internal data class WallpaperPreviewClock(val elapsed: State<Float>, val moving: Boolean)

@Composable
internal fun rememberWallpaperPreviewClock(enabled: Boolean = true, motionScale: Float = 1f): WallpaperPreviewClock {
    val motion = rememberVisualPreferences().motionLevel
    val permission = rememberWallpaperMotionPermission(track = enabled)
    val moving = permission && motion != HaloLookMotion.STILL && !LocalHaloDecorationPaused.current
    return WallpaperPreviewClock(rememberWallpaperFrames(moving, motionScale * wallpaperMotionSpeed(motion, false), 66_666_667L), moving)
}

@Composable
internal fun rememberWallpaperMotionPermission(track: Boolean): Boolean {
    if (!track) return false
    val context = LocalContext.current
    val appContext = context.applicationContext
    val reduced by rememberReducedMotion()
    var allowed by remember(context) { mutableStateOf(wallpaperMotionAllowed(context)) }
    DisposableEffect(context) {
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                allowed = wallpaperMotionAllowed(appContext)
            }
        }
        val resolver = context.contentResolver
        val scale = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { allowed = wallpaperMotionAllowed(context) }
        }
        ContextCompat.registerReceiver(context, listener, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scale)
        onDispose { context.unregisterReceiver(listener); resolver.unregisterContentObserver(scale) }
    }
    return allowed && !reduced
}

@Composable
private fun rememberWallpaperFrames(moving: Boolean, speed: Float, interval: Long): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle, moving, speed, interval) {
        if (moving) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val delta = frame - lastFrame
                if (delta >= interval) {
                    elapsed.floatValue += delta.toFloat().coerceAtMost(150_000_000f) / 1e9f * speed
                    lastFrame = frame
                }
            }
        }
    }
    return elapsed
}

@Composable
internal fun hasHaloHomeWallpaper(): Boolean {
    val context = LocalContext.current
    val options by remember(context) { WallpaperPreferences.get(context) }.state.collectAsStateWithLifecycle()
    return options.current.inApp && !rememberVisualPreferences().higherContrast
}

@Composable
internal fun HaloWallpaperBackdrop(modifier: Modifier, kids: Boolean, home: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { WallpaperPreferences.get(context) }
    val options by preferences.state.collectAsStateWithLifecycle()
    val visual = rememberVisualPreferences()
    if (home && options.current.inApp && !visual.higherContrast && !kids) {
        val base = MaterialTheme.colorScheme.background
        val dark = base.luminance() < .5f
        val scenes = with(options.current) {
            if (schedule.enabled) WallpaperPeriod.entries.map { wallpaperAt(it) } else listOf(wallpaper)
        }
        val blend = when {
            dark && scenes.all { it in darkScenes } -> listOf(.58f, .36f, .30f, .52f)
            !dark && scenes.all { it in lightScenes } -> listOf(.63f, .36f, .31f, .57f)
            dark -> listOf(.74f, .54f, .45f, .68f)
            else -> listOf(.80f, .63f, .55f, .73f)
        }
        Box(modifier) {
            HaloWallpaperCanvas(options.current, Modifier.matchParentSize(), useSchedule = true)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to base.copy(alpha = blend[0]), .32f to base.copy(alpha = blend[1]),
                .72f to base.copy(alpha = blend[2]), 1f to base.copy(alpha = blend[3]),
            )))
        }
    } else {
        com.halo.app.ui.theme.HaloAmbientBackdrop(modifier)
    }
}

private val darkScenes = setOf(HaloWallpaper.DRIFT, HaloWallpaper.AURORA, HaloWallpaper.CONSTELLATION,
    HaloWallpaper.NORTHERN_FOREST, HaloWallpaper.RAIN_GLASS, HaloWallpaper.LUMEN_GARDEN,
    HaloWallpaper.NIGHT_TRAIN, HaloWallpaper.DEEP_SEA)
private val lightScenes = setOf(HaloWallpaper.PAPER, HaloWallpaper.BALTIC_DUNES, HaloWallpaper.PAPER_CUT,
    HaloWallpaper.INK_WASH, HaloWallpaper.MEADOW_WIND)
