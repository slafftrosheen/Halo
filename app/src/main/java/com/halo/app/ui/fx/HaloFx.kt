package com.halo.app.ui.fx

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/**
 * Developer Kit v1.2 / Brand Book v2.1. All effects are decorative, never state owners.
 * Callers must gate Ripple on a real open mic, Edge on ACTING, and Cloud on a remote turn.
 * Reduced motion is observed rather than sampled once, so system changes apply immediately.
 */
@Composable
fun rememberHaloReducedMotion(): Boolean {
    val reduced by rememberReducedMotion()
    return reduced || rememberHaloMotionConstrained()
}

enum class HaloFxMoment(val asset: String, val loops: Boolean = false) {
    LEARN_OPEN("learn_open"), LEARN_INSIGHT("learn_insight"),
    LEARN_ADVANCE("learn_advance"), LEARN_RETRY("learn_retry"),
    SPLASH("splash"), SUMMON("summon"), BLOOM("bloom"),
    BLOOM_KIDS("bloom_kids"), REMEMBER("remember"),
    CLOUD_HANDOFF("cloud_handoff"),
    DRAFT_DRAWON("draft_drawon"), CANVAS_ARRANGE("canvas_arrange"),
}

/** Always provide a visible, motion-free affordance when Lottie is disabled or loading.
 * These canonical icons are temporary fallbacks until supplied static SVG conversion
 * is included in the Android resources; they must not claim a new runtime state.
 */
internal fun haloMomentFallback(moment: HaloFxMoment): HaloGlyph = when (moment) {
    HaloFxMoment.LEARN_OPEN -> HaloGlyph.READ_MODE
    HaloFxMoment.LEARN_INSIGHT -> HaloGlyph.SUCCESS
    HaloFxMoment.LEARN_ADVANCE -> HaloGlyph.NEXT
    HaloFxMoment.LEARN_RETRY -> HaloGlyph.QUESTION
    HaloFxMoment.SPLASH, HaloFxMoment.SUMMON -> HaloGlyph.HALO
    HaloFxMoment.BLOOM, HaloFxMoment.BLOOM_KIDS -> HaloGlyph.SUCCESS
    HaloFxMoment.REMEMBER -> HaloGlyph.MEMORY
    HaloFxMoment.CLOUD_HANDOFF -> HaloGlyph.CLOUD
    HaloFxMoment.DRAFT_DRAWON -> HaloGlyph.NOTE
    HaloFxMoment.CANVAS_ARRANGE -> HaloGlyph.NOTE
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
            iterations = if (moment.loops) Int.MAX_VALUE else 1,
            isPlaying = lifecycle.isAtLeast(Lifecycle.State.STARTED),
            restartOnPlay = false,
            modifier = modifier)
    } else {
        HaloStaticFx(moment.asset, dark, haloMomentFallback(moment), modifier)
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

internal fun haloStagedFallback(asset: String): HaloGlyph = when (asset) {
    "read_aloud" -> HaloGlyph.SPEAK
    "tool_timer" -> HaloGlyph.AUTOMATION
    "tool_search" -> HaloGlyph.SEARCH
    "tool_open_app" -> HaloGlyph.OPEN_APP
    "tool_camera" -> HaloGlyph.CAMERA
    "tool_calendar" -> HaloGlyph.CALENDAR
    "tool_send" -> HaloGlyph.ACTION
    "device_connect" -> HaloGlyph.GLASSES
    else -> HaloGlyph.TOOL
}

@Composable
fun HaloToolFx(tool: HaloFxTool, phase: HaloFxToolPhase, modifier: Modifier = Modifier) {
    StagedHaloFx("tool_${tool.asset}", phase.marker, phase.loop, modifier)
}

@Composable
fun HaloPairingFx(phase: HaloFxPairingPhase, modifier: Modifier = Modifier) {
    StagedHaloFx("device_connect", phase.marker, phase.loop, modifier)
}

/** The reading loop is admitted only after TtsStarted; paused/preparing states stay static. */
@Composable
fun HaloReadAloudFx(playing: Boolean, modifier: Modifier = Modifier) {
    if (playing) StagedHaloFx("read_aloud", "reading", true, modifier)
    else HaloIcon(HaloGlyph.SPEAK, null, modifier)
}

/** Branch/compare motion represents an actual UI result, never a mastery verdict. */
@Composable
fun HaloLearningBranchFx(chosen: Boolean, modifier: Modifier = Modifier) {
    StagedHaloFx("learn_branch", if (chosen) "choose" else "grow", false, modifier, enabled = learningFxNotStopped())
}

@Composable
fun HaloExplainBackFx(modifier: Modifier = Modifier) {
    StagedHaloFx("explain_back", "compare", false, modifier, enabled = learningFxNotStopped())
}

@Composable
fun HaloLearningOpenFx(modifier: Modifier = Modifier) {
    HaloFxAnimation(HaloFxMoment.LEARN_OPEN, modifier, enabled = learningFxNotStopped())
}

@Composable
private fun learningFxNotStopped(): Boolean {
    val epoch by com.halo.app.ui.components.VoiceFieldCancellation.epoch.collectAsStateWithLifecycle()
    val entry = remember { epoch }
    return epoch == entry
}

/** Markers come from supplied DevKit assets. Phase changes remount playback. */
@Composable
private fun StagedHaloFx(asset: String, marker: String, loop: Boolean, modifier: Modifier, enabled: Boolean = true) {
    val reduced = rememberHaloReducedMotion()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val composition = if (enabled && !reduced) {
        rememberLottieComposition(LottieCompositionSpec.Asset(
            "halo/fx/halo_fx_${asset}_${if (dark) "dark" else "light"}.json"
        )).value
    } else null
    if (composition != null) {
        key(asset, marker, dark) {
            LottieAnimation(
                composition = composition,
                clipSpec = LottieClipSpec.Marker(marker),
                iterations = if (loop) Int.MAX_VALUE else 1,
                isPlaying = lifecycle.isAtLeast(Lifecycle.State.STARTED),
                restartOnPlay = false, modifier = modifier,
            )
        }
    } else {
        HaloStaticFx(asset, dark, haloStagedFallback(asset), modifier)
    }
}

/** Exact DevKit SVG artwork, not a substitute icon. In reduced-motion mode
 * it is the whole effect; while parsing, the semantic glyph is a safe fallback.
 * This avoids decoding full Lottie compositions when motion is disabled.
 */
internal fun haloStaticAssetName(asset: String, dark: Boolean): String =
    "halo_fx_${asset}_${if (dark) "dark" else "light"}.svg"

@Composable
private fun HaloStaticFx(
    asset: String, dark: Boolean, fallback: HaloGlyph,
    modifier: Modifier = Modifier,
) {
    val filename = haloStaticAssetName(asset, dark)
    val context = LocalContext.current
    val request = remember(context, filename) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/halo/fx/static/$filename")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    if (painter.state is AsyncImagePainter.State.Success) {
        Image(painter = painter, contentDescription = null, modifier = modifier)
    } else {
        HaloIcon(fallback, contentDescription = null,
            modifier = modifier, tint = MaterialTheme.colorScheme.primary)
    }
}

private val breathEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Guide: one best next step, never permission/cloud/payment/destructive opt-ins.
 * The optional first appearance traces the outline ONCE; subsequent attention
 * is only a pulse. Reduced motion always uses a fully drawn static outline.
 */
fun Modifier.haloTrace(
    enabled: Boolean,
    accent: Color,
    corner: Dp = 32.dp,
    lapOnAppear: Boolean = false,
): Modifier = composed {
    if (!com.halo.app.ui.theme.rememberVisualPreferences().guideLight) return@composed this
    val reduced = rememberHaloReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val lap = remember { Animatable(if (lapOnAppear && !reduced) 0f else 1f) }
    var lapPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, reduced, foreground, lapOnAppear) {
        when {
            !enabled || reduced || !lapOnAppear -> lap.snapTo(1f)
            !foreground -> Unit // never consume the first lap while backgrounded
            !lapPlayed -> {
                lapPlayed = true
                lap.snapTo(0f)
                lap.animateTo(1f, tween(850, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
            }
            else -> lap.snapTo(1f)
        }
    }
    if (!enabled) return@composed this
    val pulse = if (reduced || !foreground) 0.5f else {
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
        // A single lap of the 316-degree open ring over the focus rectangle.
        val full = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                0f, 0f, size.width, size.height, corner.toPx(), corner.toPx()))
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val visible = Path()
        measure.getSegment(0f, measure.length * lap.value, visible, true)
        drawPath(visible, accent, alpha = 0.7f + 0.3f * pulse,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Edge pulse while halo is actually acting on this phone: no spinning/rotating glow. */
fun Modifier.haloEdgeGlow(active: Boolean, accent: Color, corner: Dp = 44.dp): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val alpha by animateFloatAsState(if (active) 1f else 0f,
        tween(if (reduced) 0 else if (active) 250 else 150), label = "halo-edge")
    // An invisible edge must not retain an infinite frame clock.
    val pulse = if (reduced || !active) 0.5f else {
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

/** The new accent enters the preview ring from its open gap in 750 ms.
 * No palette writes are made here, and a reduced-motion change is an
 * immediate static repaint. Works for all supported accent/tone pairs.
 */
internal fun haloAccentSweepChanged(previous: Pair<Color, Color>,
    next: Pair<Color, Color>): Boolean = previous != next

@Composable
fun HaloAccentSweepRing(
    ringStart: Color,
    ringEnd: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 96.dp,
) {
    val reduced = rememberHaloReducedMotion()
    val palette = ringStart to ringEnd
    var previous by remember { mutableStateOf(palette) }
    var next by remember { mutableStateOf(palette) }
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(palette, reduced) {
        if (haloAccentSweepChanged(next, palette)) {
            previous = next
            next = palette
            if (reduced) {
                sweep.snapTo(1f)
                previous = palette
            } else {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(750, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)))
                previous = palette
            }
        } else if (reduced) {
            sweep.snapTo(1f)
            previous = next
        }
    }
    Canvas(modifier.size(ringSize)) {
        val stroke = size.minDimension * 0.075f
        val inset = stroke / 2f + size.minDimension * 0.08f
        val arc = androidx.compose.ui.geometry.Size(
            size.width - 2f * inset, size.height - 2f * inset)
        val origin = Offset(inset, inset)
        val start = -23f
        drawArc(
            Brush.linearGradient(listOf(previous.first, previous.second)),
            start, 316f, false, origin, arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            Brush.linearGradient(listOf(next.first, next.second)),
            start, 316f * sweep.value, false, origin, arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Open Ring draw-on while pulling; closes and breathes only during a live refresh.
 * A reduced-motion device gets a stationary partial/full ring.
 */
@Composable
fun HaloPullRing(pullFraction: Float, refreshing: Boolean, accent: Color,
    modifier: Modifier = Modifier, ringSize: Dp = 40.dp) {
    val reduced = rememberHaloReducedMotion()
    val fraction = pullFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    val closed by animateFloatAsState(if (refreshing) 1f else 0f,
        tween(if (reduced) 0 else 300), label = "halo-pull-close")
    val breath = if (!refreshing || reduced) 1f else {
        val animation = rememberInfiniteTransition(label = "halo-pull-breath")
        val value by animation.animateFloat(.6f, 1f,
            infiniteRepeatable(tween(800, easing = breathEase), RepeatMode.Reverse),
            label = "pull-breath")
        value
    }
    Canvas(modifier.size(ringSize)) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f + 4.dp.toPx()
        val arc = androidx.compose.ui.geometry.Size(size.width - 2f * inset, size.height - 2f * inset)
        val sweep = (316f + 44f * closed) * if (refreshing) 1f else fraction
        drawArc(accent, startAngle = -23f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = arc, alpha = breath,
            style = Stroke(stroke, cap = StrokeCap.Round))
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

// Supplied DevKit v1.2 draft draw-on: provisional content keeps its dashed
// accent border until the learner explicitly chooses Keep or Discard.
// Reduced motion uses the complete static outline; visibility never grants approval.
fun Modifier.haloDraftBorder(visible: Boolean, color: Color, corner: Dp = 20.dp): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val draw = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(visible, reduced) {
        when {
            !visible -> draw.snapTo(0f)
            reduced -> draw.snapTo(1f)
            else -> {
                draw.snapTo(0f)
                draw.animateTo(1f, tween(900, easing = CubicBezierEasing(.3f, 0f, .2f, 1f)))
            }
        }
    }
    drawWithContent {
        drawContent()
        if (!visible) return@drawWithContent
        val cornerRadius = corner.toPx()
        val path = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                0f, 0f, size.width, size.height, cornerRadius, cornerRadius))
        }
        val measurement = PathMeasure().apply { setPath(path, false) }
        val visiblePath = Path()
        measurement.getSegment(0f, measurement.length * draw.value, visiblePath, true)
        drawPath(visiblePath, color, style = Stroke(
            width = 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
    }
}
