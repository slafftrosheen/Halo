package com.halo.app.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.collect

@Composable
internal fun HaloWallpaperCanvas(
    options: WallpaperOptions,
    modifier: Modifier,
    previewTime: Float? = null,
    animate: Boolean = true,
    useSchedule: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motion = rememberVisualPreferences().motionLevel
    val reduced by rememberReducedMotion()
    val ambient by HaloWallpaperActivity.state.collectAsStateWithLifecycle()
    val accent = LocalHaloAccentInk.current.toArgb()
    var clock by remember { mutableFloatStateOf(haloWallpaperTime()) }
    val latch = remember { WallpaperPeriodLatch(wallpaperPeriod(clock)) }
    var period by remember { mutableStateOf(latch.period) }
    val wall = if (useSchedule) options.wallpaperAt(period) else options.wallpaper
    var powerAllowsMotion by remember { mutableStateOf(wallpaperMotionAllowed(context)) }
    if (animate || useSchedule) DisposableEffect(context, lifecycle, useSchedule) {
        fun updateClock(visible: Boolean) {
            clock = haloWallpaperTime()
            powerAllowsMotion = wallpaperMotionAllowed(context)
            if (useSchedule) period = latch.update(wallpaperPeriod(clock), visible, SystemClock.elapsedRealtime())
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    intent?.action != Intent.ACTION_SCREEN_OFF
                updateClock(visible)
            }
        }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> updateClock(true)
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
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    val moving = animate && !reduced && powerAllowsMotion && !wall.nearStill && motion != HaloLookMotion.STILL
    val night = options.calmerAtNight && previewTime == null && (clock >= 22f / 24f || clock < 7f / 24f)
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle, moving, motion, night) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val speed = (if (motion == HaloLookMotion.FULL) 1f else .5f) * if (night) .5f else 1f
            var lastFrame = withFrameNanos { it }
            while (true) {
                val nextFrame = withFrameNanos { it }
                if (nextFrame - lastFrame >= WALLPAPER_FRAME_NS) {
                    elapsed.floatValue += (nextFrame - lastFrame).toFloat().coerceAtMost(150_000_000f) / 1e9f * speed
                    lastFrame = nextFrame
                }
            }
        }
    }
    val weather = animate && wall.usesEnvironment && wall != HaloWallpaper.LUMEN_GARDEN
    val garden = animate && wall == HaloWallpaper.LUMEN_GARDEN
    val environment by produceState(WallpaperEnvironment(), lifecycle, weather, garden) {
        value = WallpaperEnvironment()
        if (weather || garden) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            wallpaperEnvironment(weather, garden).collect { value = it }
        }
    }
    val photoStore = remember(context) { WallpaperPhotoStore(context) }
    val stylePhoto = options.photo.takeIf { wall == HaloWallpaper.YOUR_PHOTO }
    val photo by produceState<android.graphics.Bitmap?>(null, photoStore, stylePhoto?.id, stylePhoto?.blur) {
        value = null
        value = photoStore.render(stylePhoto)
    }
    val painter = remember { HaloWallpaperPainter() }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            painter.paint(canvas.nativeCanvas, wall, if (moving) elapsed.floatValue else 0f, previewTime ?: clock, accent,
                if (options.reactsToHalo) ambient else HaloAmbient.IDLE, 1f,
                ripples = moving && motion == HaloLookMotion.FULL, dim = if (night) .25f else 0f,
                width = size.width, height = size.height, environment = environment, photo = photo,
                ring = stylePhoto?.ring ?: WallpaperRing.CENTER)
        }
    }
}

@Composable
internal fun HaloWallpaperBackdrop(modifier: Modifier, kids: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { WallpaperPreferences.get(context) }
    val options by preferences.state.collectAsStateWithLifecycle()
    val visual = rememberVisualPreferences()
    if (options.current.inApp && !visual.higherContrast && !kids) {
        HaloWallpaperCanvas(options.current, modifier,
            animate = !com.halo.app.ui.theme.LocalHaloDecorationPaused.current, useSchedule = true)
    } else {
        com.halo.app.ui.theme.HaloAmbientBackdrop(modifier)
    }
}
