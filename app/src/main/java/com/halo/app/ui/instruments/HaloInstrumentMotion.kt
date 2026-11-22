package com.halo.app.ui.instruments

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.theme.rememberVisualPreferences
import kotlin.math.roundToInt

internal object HaloInstrumentMotion {
    const val QUICK = 160
    const val BASE = 240
    const val CALM = 360
    const val UNDO_NOTICE = 3_000L
    val standard = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val enter = CubicBezierEasing(.05f, .7f, .1f, 1f)

    @Composable fun level(): HaloLookMotion = if (rememberReducedMotion().value) HaloLookMotion.STILL
        else rememberVisualPreferences().motionLevel

    fun duration(ms: Int, level: HaloLookMotion): Int = when (level) {
        HaloLookMotion.FULL -> ms
        HaloLookMotion.GENTLE -> (ms * 1.3f).roundToInt()
        HaloLookMotion.STILL -> 150
    }

    fun <T> spec(ms: Int, level: HaloLookMotion): TweenSpec<T> = tween(duration(ms, level), easing = standard)

    @Composable fun menuEnter(level: HaloLookMotion): EnterTransition {
        val drop = with(LocalDensity.current) { 4.dp.roundToPx() }
        val fade = fadeIn(tween(duration(QUICK, level), easing = enter))
        return if (level == HaloLookMotion.STILL) fade
            else fade + slideInVertically(tween(duration(QUICK, level), easing = enter)) { -drop }
    }

    fun panelEnter(level: HaloLookMotion): EnterTransition = if (level == HaloLookMotion.FULL)
        fadeIn(spec(BASE, level)) + expandHorizontally(spec(BASE, level)) else fadeIn(spec(BASE, level))

    fun panelExit(level: HaloLookMotion): ExitTransition = if (level == HaloLookMotion.FULL)
        fadeOut(spec(BASE, level)) + shrinkHorizontally(spec(BASE, level)) else fadeOut(spec(BASE, level))

    @Composable fun pageEnter(forward: Boolean, level: HaloLookMotion = level()): EnterTransition {
        val fade = fadeIn(spec(CALM, level))
        if (level == HaloLookMotion.STILL) return fade
        val offset = with(LocalDensity.current) { (if (level == HaloLookMotion.FULL) 24.dp else 4.dp).roundToPx() }
        return fade + slideInHorizontally(spec(CALM, level)) { if (forward) offset else -offset }
    }
}
