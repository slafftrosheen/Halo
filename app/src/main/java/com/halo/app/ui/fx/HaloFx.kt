package com.halo.app.ui.fx

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.LottieConstants
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.halo.app.ui.theme.rememberReducedMotion

/**
 * Developer Kit v1.2 / Brand Book v2.1. All effects are decorative, never state owners.
 * Callers must gate Ripple on a real open mic, Edge on ACTING, and Cloud on a remote turn.
 * Reduced motion is observed rather than sampled once, so system changes apply immediately.
 */
@Composable
fun rememberHaloReducedMotion(): Boolean {
    val reduced by rememberReducedMotion()
    return reduced
}

enum class HaloFxMoment(val asset: String, val loops: Boolean = false) {
    SPLASH("splash"), SUMMON("summon"), BLOOM("bloom"),
    BLOOM_KIDS("bloom_kids"), REMEMBER("remember"),
    CLOUD_HANDOFF("cloud_handoff"),
    DRAFT_DRAWON("draft_drawon"), CANVAS_ARRANGE("canvas_arrange"),
}

/** Fixed-size motion moments; one-shots play once and stay at their final frame. */
@Composable
fun HaloFxAnimation(
    moment: HaloFxMoment,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = rememberHaloReducedMotion()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val composition = if (enabled && !reduced) {
        rememberLottieComposition(LottieCompositionSpec.Asset(
            "halo/fx/halo_fx_${moment.asset}_${if (dark) "dark" else "light"}.json"
        )).value
    } else null
    if (composition != null) {
        LottieAnimation(composition = composition,
            iterations = if (moment.loops) LottieConstants.IterateForever else 1,
            isPlaying = lifecycle.isAtLeast(Lifecycle.State.STARTED),
            restartOnPlay = false,
            modifier = modifier)
    } else {
        // No animated placeholder. The parent retains its ordinary static UI.
        androidx.compose.foundation.layout.Box(modifier)
    }
}


/** Tool and pairing motion must be clipped to actual runtime phase: never animate Done early. */
enum class HaloFxTool(val asset: String) {
    TIMER("timer"), SEARCH("search"), OPEN_APP("open_app"),
    CAMERA("camera"), CALENDAR("calendar"), SEND("send")
}
enum class HaloFxToolPhase(val marker: String, val loop: Boolean) {
    START("start", false), WORKING("working", true), DONE("done", false)
}
enum class HaloFxPairingPhase(val marker: String, val loop: Boolean) {
    CONNECTING("connecting", true), CONNECTED("connected", false)
}

@Composable
fun HaloToolFx(tool: HaloFxTool, phase: HaloFxToolPhase, modifier: Modifier = Modifier) {
    StagedHaloFx("tool_${tool.asset}", phase.marker, phase.loop, modifier)
}

@Composable
fun HaloPairingFx(phase: HaloFxPairingPhase, modifier: Modifier = Modifier) {
    StagedHaloFx("device_connect", phase.marker, phase.loop, modifier)
}

/** Markers come from the checked-in v1.2 Lottie files. Phase changes remount playback. */
@Composable
private fun StagedHaloFx(asset: String, marker: String, loop: Boolean, modifier: Modifier) {
    val reduced = rememberHaloReducedMotion()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val composition = if (!reduced) {
        rememberLottieComposition(LottieCompositionSpec.Asset(
            "halo/fx/halo_fx_${asset}_${if (dark) "dark" else "light"}.json"
        )).value
    } else null
    if (composition != null) {
        key(asset, marker, dark) {
            LottieAnimation(
                composition = composition,
                clipSpec = LottieClipSpec.Marker(marker),
                iterations = if (loop) LottieConstants.IterateForever else 1,
                isPlaying = lifecycle.isAtLeast(Lifecycle.State.STARTED),
                restartOnPlay = false, modifier = modifier,
            )
        }
    } else androidx.compose.foundation.layout.Box(modifier)
}

private val breathEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Guide: a single best next step. Do not use on cloud opt-in or destructive actions. */
fun Modifier.haloTrace(enabled: Boolean, accent: Color, corner: Dp = 32.dp): Modifier = composed {
    if (!enabled) return@composed this
    val reduced = rememberHaloReducedMotion()
    val pulse = if (reduced) 0.5f else {
        val transition = rememberInfiniteTransition(label = "halo-guide-pulse")
        val value by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(1200, easing = breathEase), RepeatMode.Reverse),
            label = "guide")
        value
    }
    drawWithContent {
        drawContent()
        val outline = RoundedCornerShape(corner).createOutline(size, layoutDirection, this)
        drawOutline(outline, accent, alpha = 0.12f + 0.20f * pulse,
            style = Stroke((9f + 5f * pulse).dp.toPx()))
        drawOutline(outline, accent, alpha = 0.7f + 0.3f * pulse,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Edge pulse while halo is actually acting on this phone: no spinning/rotating glow. */
fun Modifier.haloEdgeGlow(active: Boolean, accent: Color, corner: Dp = 44.dp): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val alpha by animateFloatAsState(if (active) 1f else 0f,
        tween(if (reduced) 0 else if (active) 250 else 150), label = "halo-edge")
    val pulse = if (reduced) 0.5f else {
        val transition = rememberInfiniteTransition(label = "halo-edge-breath")
        val value by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(1200, easing = breathEase), RepeatMode.Reverse),
            label = "edge")
        value
    }
    drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val outline = RoundedCornerShape(corner).createOutline(size, layoutDirection, this)
            drawOutline(outline, accent, alpha = alpha * (0.10f + 0.14f * pulse),
                style = Stroke((20f + 8f * pulse).dp.toPx()))
            drawOutline(outline, accent, alpha = alpha * (0.42f + 0.32f * pulse),
                style = Stroke(3.dp.toPx()))
        }
    }
}

/** Aura is for a hero/presence area; never combine with a simultaneous Guide trace. */
fun Modifier.haloAura(accent: Color, warm: Color = Color(0xFFFF9F6B)): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val pulse = if (reduced) 0.5f else {
        val transition = rememberInfiniteTransition(label = "halo-aura")
        val value by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(2000, easing = breathEase), RepeatMode.Reverse),
            label = "aura")
        value
    }
    drawBehind {
        val radius = size.minDimension * (0.70f + 0.06f * pulse)
        drawCircle(Brush.radialGradient(
            0f to accent.copy(alpha = 0.30f),
            0.52f to warm.copy(alpha = 0.09f),
            1f to Color.Transparent,
            center = center, radius = radius
        ), radius = radius, alpha = 0.55f + 0.25f * pulse)
    }
}

/** Static skeleton under reduced motion. */
fun Modifier.haloShimmer(base: Color, highlight: Color): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val fraction = if (reduced) 0f else {
        val transition = rememberInfiniteTransition(label = "halo-shimmer")
        val value by transition.animateFloat(-1f, 2f,
            infiniteRepeatable(tween(1600)), label = "shimmer")
        value
    }
    drawBehind {
        if (reduced) drawRect(base) else {
            val x = size.width * fraction
            drawRect(Brush.linearGradient(listOf(base, highlight, base),
                start = Offset(x - size.width / 2f, 0f),
                end = Offset(x + size.width / 2f, 0f)))
        }
    }
}

@Composable
fun HaloProgressRing(progress: Float, accent: Color, modifier: Modifier = Modifier, ringSize: Dp = 72.dp) {
    val reduced = rememberHaloReducedMotion()
    val safeProgress = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val displayed by animateFloatAsState(safeProgress, tween(if (reduced) 0 else 300),
        label = "halo-model-download-progress")
    Canvas(modifier.size(ringSize)) {
        val stroke = size.minDimension * 0.085f
        val inset = stroke / 2f + size.minDimension * 0.10f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
        drawArc(accent, -90f, 360f, false, Offset(inset, inset), arcSize,
            alpha = 0.14f, style = Stroke(stroke))
        drawArc(accent, -90f, 360f * displayed, false, Offset(inset, inset), arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun HaloRipple(micOpen: Boolean, level: Float, accent: Color, modifier: Modifier = Modifier, ringSize: Dp = 112.dp) {
    if (!micOpen) return
    val reduced = rememberHaloReducedMotion()
    val phase = if (reduced) 0.5f else {
        val transition = rememberInfiniteTransition(label = "halo-mic-ripple")
        val value by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ripple")
        value
    }
    Canvas(modifier.size(ringSize)) {
        for (index in 0..2) {
            val f = if (reduced) 0.45f else (phase + index / 3f) % 1f
            drawCircle(accent, radius = size.minDimension * (0.34f + 0.11f * level.coerceIn(0f, 1f) * f),
                alpha = 0.30f * (1f - f), style = Stroke(2.dp.toPx()))
        }
    }
}
