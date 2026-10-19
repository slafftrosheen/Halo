// halo DevKit 2.0 · "2027 light layer" — aura, screen-edge light, live task pill, assistant overlay motion.
// Reference implementation (Compose). Rules:
//  - Light follows REAL state only (AssistantSurface interactionState / runtime phase). Never decorative.
//  - Breathe, don't spin. Only THINKING rotates (orb). Edge pulses only while halo is actually acting or listening as assistant.
//  - One emphatic light per screen. Apricot = halo needs you. Red = error. Success bloom only after a CONFIRMED receipt.
//  - Low-end budget: no real-time blur below API 31 or when thermal status >= MODERATE → solid surfaces, same layout.
//  - Reduced motion: aura static at mid opacity, edge static 70%, sweep replaced by instant edge.
package com.halo.app.ui.fx

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class HaloLight { OFF, IDLE, LISTENING, THINKING, ACTING, NEEDS_YOU, SUCCESS, ERROR }

/** Map runtime phase → light. Keep in one place so every surface agrees. */
fun lightFor(phase: String, receiptConfirmed: Boolean = false): HaloLight = when (phase) {
    "LISTENING", "CAPTURING_VOICE" -> HaloLight.LISTENING
    "THINKING", "PLANNING", "PREPARING_CONTEXT", "STREAMING_TEXT", "MODEL_LOADING" -> HaloLight.THINKING
    "ACTING", "ACTING_PHONE" -> HaloLight.ACTING
    "APPROVAL", "WAITING", "PERMISSION_MISSING" -> HaloLight.NEEDS_YOU
    "SUCCESS" -> if (receiptConfirmed) HaloLight.SUCCESS else HaloLight.IDLE
    "ERROR", "OFFLINE" -> HaloLight.ERROR
    "STOPPED" -> HaloLight.OFF
    else -> HaloLight.IDLE
}

/** Top-of-screen aura. period: idle 4.8s · thinking 2.6s · acting/needs 2.4s · listening 1.8s. */
fun Modifier.haloAura(light: HaloLight, accent: Color, attention: Color, error: Color, reducedMotion: Boolean): Modifier = composed {
    val (color, base, period) = when (light) {
        HaloLight.OFF -> Triple(accent, 0f, 0)
        HaloLight.IDLE -> Triple(accent, .32f, 4800)
        HaloLight.LISTENING -> Triple(accent, .80f, 1800)
        HaloLight.THINKING -> Triple(accent, .65f, 2600)
        HaloLight.ACTING -> Triple(accent, .70f, 2400)
        HaloLight.NEEDS_YOU -> Triple(attention, .70f, 2400)
        HaloLight.SUCCESS -> Triple(accent, .75f, 0)
        HaloLight.ERROR -> Triple(error, .50f, 0)
    }
    val alpha by animateFloatAsState(base, tween(320), label = "auraAlpha")
    val t = rememberInfiniteTransition(label = "aura")
    val breath by t.animateFloat(1f, if (period == 0 || reducedMotion) 1f else 1.08f,
        infiniteRepeatable(tween(maxOf(period, 1) / 2, easing = CubicBezierEasing(.45f, 0f, .55f, 1f)), RepeatMode.Reverse), label = "breath")
    drawBehind {
        val r = size.width * .78f * breath
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .34f * alpha), Color.Transparent), Offset(size.width / 2, -size.width * .12f), r),
            radius = r, center = Offset(size.width / 2, -size.width * .12f))
    }
}

/** Screen-edge light. INVOKE = one 0.9 s sweep up both sides, then PULSE. Use on the assistant overlay window and while ACTING. */
enum class EdgeMode { OFF, INVOKE, PULSE, ATTENTION }

fun Modifier.haloScreenEdge(mode: EdgeMode, accent: Color, attention: Color, corner: Float, reducedMotion: Boolean): Modifier = composed {
    if (mode == EdgeMode.OFF) return@composed this
    val sweep = remember { Animatable(if (mode == EdgeMode.INVOKE && !reducedMotion) 0f else 1f) }
    LaunchedEffect(mode) { if (mode == EdgeMode.INVOKE && !reducedMotion) sweep.animateTo(1f, tween(900, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) }
    val t = rememberInfiniteTransition(label = "edge")
    val pulse by t.animateFloat(.55f, if (reducedMotion) .55f else 1f, infiniteRepeatable(tween(1200, easing = CubicBezierEasing(.45f, 0f, .55f, 1f)), RepeatMode.Reverse), label = "pulse")
    val c = if (mode == EdgeMode.ATTENTION) attention else accent
    drawWithContent {
        drawContent()
        val a = if (sweep.value < 1f) sweep.value else pulse
        val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
        val measure = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        if (sweep.value < 1f) { // draw from bottom centre outwards on both sides
            val len = measure.length; val start = len * .5f /* bottom centre depends on path origin; adjust per device */
            measure.getSegment(start - len / 2 * sweep.value, start + len / 2 * sweep.value, seg, true)
        } else seg.addPath(path)
        drawPath(seg, c.copy(alpha = .35f * a), style = Stroke(14.dp.toPx()))
        drawPath(seg, c.copy(alpha = .9f * a), style = Stroke(2.5.dp.toPx()))
    }
}

/** Live task pill: a running task hugs the nav on every tab. Tap → Activity run detail. Stop is always one tap away. */
data class LiveTask(val title: String, val progress: Float?, val needsYou: Boolean)
