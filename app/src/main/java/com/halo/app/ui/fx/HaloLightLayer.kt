package com.halo.app.ui.fx

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.halo.app.ui.presence.HaloSignalState

/** A single state projection for Home, Screen, and the native assistant. */
enum class HaloLight(val period: Int, val strength: Float) {
    OFF(0, 0f), IDLE(4800, .32f), LISTENING(1800, .8f), THINKING(2600, .65f),
    ACTING(2400, .7f), NEEDS_YOU(2400, .7f), SUCCESS(0, .75f), ERROR(0, .5f),
}

fun HaloSignalState.light(): HaloLight = when (this) {
    HaloSignalState.LISTENING, HaloSignalState.CAPTURING -> HaloLight.LISTENING
    HaloSignalState.THINKING -> HaloLight.THINKING
    HaloSignalState.ACTING -> HaloLight.ACTING
    HaloSignalState.WAITING, HaloSignalState.WARNING -> HaloLight.NEEDS_YOU
    HaloSignalState.ERROR, HaloSignalState.OFFLINE -> HaloLight.ERROR
    HaloSignalState.STOPPED -> HaloLight.OFF
    else -> HaloLight.IDLE
}

/** Unsaved halo drafts have a dashed boundary and never imitate the saved document. */
fun Modifier.haloDraftOutline(color: Color): Modifier = drawWithContent {
    drawContent()
    val inset = 1.dp.toPx()
    drawRoundRect(color.copy(alpha = .55f), topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
}

fun Modifier.haloStateAura(light: HaloLight, accent: Color, attention: Color, error: Color): Modifier = composed {
    val guideLight = com.halo.app.ui.theme.rememberVisualPreferences().guideLight
    val reduced = rememberHaloReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val strength by animateFloatAsState(light.strength, tween(if (reduced) 0 else 333), label = "halo-aura-state")
    val breath = if (light.period > 0 && !reduced && visible) {
        val transition = rememberInfiniteTransition(label = "halo-aura")
        val value by transition.animateFloat(.92f, 1.08f,
            infiniteRepeatable(tween(light.period / 2, easing = CubicBezierEasing(.45f, 0f, .55f, 1f)), RepeatMode.Reverse),
            label = "halo-aura-breath")
        value
    } else 1f
    val color = when (light) { HaloLight.NEEDS_YOU -> attention; HaloLight.ERROR -> error; else -> accent }
    drawBehind {
        if (guideLight && strength > 0f) {
            val center = Offset(size.width / 2f, -size.width * .12f)
            val radius = size.width * .78f * breath
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .34f * strength), Color.Transparent),
                center = center, radius = radius), radius, center)
        }
    }
}

/** Both edges climb from the bottom on invocation, then breathe without rotation. */
fun Modifier.haloAssistantEdge(active: Boolean, attention: Boolean, accent: Color, apricot: Color): Modifier = composed {
    val guideLight = com.halo.app.ui.theme.rememberVisualPreferences().guideLight
    val reduced = rememberHaloReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(active, reduced, foreground) {
        if (!active) sweep.snapTo(0f)
        else if (reduced) sweep.snapTo(1f)
        else if (foreground && sweep.value < 1f) sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val opacity by animateFloatAsState(if (active) 1f else 0f, tween(if (reduced) 0 else 333), label = "halo-edge-stop")
    val pulse = if (!reduced && active && foreground) {
        val transition = rememberInfiniteTransition(label = "halo-edge")
        val value by transition.animateFloat(.55f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "halo-edge-pulse")
        value
    } else .7f
    drawWithContent {
        drawContent()
        if (guideLight && opacity > 0f) {
            val c = if (attention) apricot else accent
            val y = size.height * (1f - sweep.value)
            val path = Path().apply {
                moveTo(1.dp.toPx(), size.height); lineTo(1.dp.toPx(), y)
                moveTo(size.width - 1.dp.toPx(), size.height); lineTo(size.width - 1.dp.toPx(), y)
            }
            drawPath(path, c.copy(alpha = .12f * pulse * opacity), style = Stroke(16.dp.toPx()))
            drawPath(path, c.copy(alpha = .9f * pulse * opacity), style = Stroke(2.5.dp.toPx()))
        }
    }
}
