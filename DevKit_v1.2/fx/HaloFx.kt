// HaloFx.kt — reference implementation of halo FX for Jetpack Compose.
// Pairs with tokens.json → fx / guide and HaloPalette.kt (accent ringStart / ringEnd).
// Motion v2: breathe first, rotate rarely. Rotation only for THINKING and an optional one-lap Guide on first appearance.
// Rules: one light at a time (Aura or Trace; the orb doesn't count). Each effect maps to one state.
// Reduced motion: every effect falls back to a static glow (no movement).
package app.halo.ui.fx

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.withStyle

/** True when the system animator scale is 0 (or Halo's own "Reduce motion" is on — wire that in here). */
@Composable
fun rememberHaloReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private val HaloEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val BreathEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Sweep shader with the Open Ring gap, rotated to [degrees]. gapFraction 30° ≈ 0.083. */
private fun ringShader(center: Offset, start: Color, end: Color, degrees: Float, gapFraction: Float): ShaderBrush {
    val g = gapFraction / 2f
    val shader = android.graphics.SweepGradient(
        center.x, center.y,
        intArrayOf(Color.Transparent.toArgb(), start.toArgb(), end.toArgb(), start.toArgb(), Color.Transparent.toArgb()),
        floatArrayOf(0f, g, 0.5f, 1f - g, 1f)
    )
    // Gap rests at the upper right (-45°) like the logo.
    val m = android.graphics.Matrix(); m.setRotate(degrees - 45f, center.x, center.y); shader.setLocalMatrix(m)
    return ShaderBrush(shader)
}

/**
 * TRACE — the Halo Guide. Default is a calm PULSE (glow breathes every 2.4 s, gap fixed at upper right).
 * Set [lapOnAppear] = true for the one moment that deserves it (first time a screen shows its next step):
 * light travels one lap (2.4 s), then settles into the pulse. Rotation is a rare, special moment.
 * Use on ONE element per screen. Never on cloud opt-in, purchases, new permissions or destructive actions.
 */
fun Modifier.haloTrace(
    enabled: Boolean,
    ringStart: Color,
    ringEnd: Color,
    shape: Shape = RoundedCornerShape(50),
    lapOnAppear: Boolean = false,
    strokeWidth: Dp = 2.5.dp,
    glowWidth: Dp = 10.dp,
): Modifier = composed {
    if (!enabled) return@composed this
    val reduced = rememberHaloReducedMotion()
    val angle = remember { Animatable(if (reduced || !lapOnAppear) 360f else 0f) }
    LaunchedEffect(enabled) { if (!reduced && lapOnAppear) { angle.snapTo(0f); angle.animateTo(360f, tween(2400, easing = HaloEase)) } }
    val t = rememberInfiniteTransition(label = "guidePulse")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = BreathEase), RepeatMode.Reverse), label = "p")
    val p = if (reduced) 0.5f else pulse
    drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val brush = ringShader(center, ringStart, ringEnd, angle.value, 0.083f)
        drawOutline(outline, brush, alpha = 0.14f + 0.26f * p, style = Stroke(glowWidth.toPx()))
        drawOutline(outline, brush, style = Stroke(strokeWidth.toPx()))
    }
}

/**
 * EDGE PULSE — halo is acting on the phone. Apply to the root while ACTING / ACTING_PHONE only.
 * No rotation: the edge breathes every 2.4 s, with a soft glow rising from the bottom where the steps card sits.
 * Fades in 250 ms, out 150 ms.
 */
fun Modifier.haloEdgeGlow(active: Boolean, ringStart: Color, ringEnd: Color, cornerRadius: Dp = 44.dp): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val alpha by animateFloatAsState(if (active) 1f else 0f, tween(if (active) 250 else 150), label = "edgeAlpha")
    val t = rememberInfiniteTransition(label = "edge")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = BreathEase), RepeatMode.Reverse), label = "edgeBreath")
    val b = if (reduced) 0.5f else breath
    drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val outline = RoundedCornerShape(cornerRadius).createOutline(size, layoutDirection, this)
        val brush = ringShader(center, ringStart, ringEnd, 0f, 0.06f)
        // Wide strokes are half outside the screen, so they read as an inner glow.
        drawOutline(outline, brush, alpha = (0.06f + 0.12f * b) * alpha, style = Stroke((28f + 16f * b).dp.toPx()))
        drawOutline(outline, brush, alpha = (0.18f + 0.22f * b) * alpha, style = Stroke((12f + 8f * b).dp.toPx()))
        drawOutline(outline, brush, alpha = (0.7f + 0.3f * b) * alpha, style = Stroke((4f + 2f * b).dp.toPx()))
        drawCircle(
            Brush.radialGradient(listOf(ringStart.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width / 2f, size.height + 60.dp.toPx()), radius = 260.dp.toPx()),
            radius = 260.dp.toPx(), center = Offset(size.width / 2f, size.height + 60.dp.toPx()), alpha = (0.4f + 0.6f * b) * alpha
        )
    }
}

/** AURA — presence. Soft Mint→Apricot glow behind the orb or a hero moment; breathes every 4 s. */
fun Modifier.haloAura(ringStart: Color, warm: Color = Color(0xFFFF9F6B), sizeRatio: Float = 2f): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val t = rememberInfiniteTransition(label = "aura")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = BreathEase), RepeatMode.Reverse), label = "breath")
    val b = if (reduced) 0.6f else breath
    drawBehind {
        val r = size.minDimension / 2f * sizeRatio * (1f + 0.08f * b)
        drawCircle(
            Brush.radialGradient(
                0f to ringStart.copy(alpha = 0.42f), 0.45f to warm.copy(alpha = 0.14f), 1f to Color.Transparent,
                center = center, radius = r
            ),
            radius = r, alpha = 0.55f + 0.35f * b
        )
    }
}

/** SHIMMER — loading / thinking. A highlight sweeps across placeholders (1.6 s). Never a spinner. */
fun Modifier.haloShimmer(base: Color, highlight: Color): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1600, easing = HaloEase)), label = "x")
    drawBehind {
        if (reduced) { drawRect(base); return@drawBehind }
        val w = size.width
        drawRect(Brush.linearGradient(
            listOf(base, highlight, base),
            start = Offset(w * x - w * 0.5f, 0f), end = Offset(w * x + w * 0.5f, 0f)
        ))
    }
}

/** BLOOM — success. Three rings expand once (0.6 s). Pair with a light haptic. Kids: pass Apricot first. */
@Composable
fun HaloBloom(play: Boolean, colorA: Color, colorB: Color, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val reduced = rememberHaloReducedMotion()
    val p = remember { Animatable(0f) }
    LaunchedEffect(play) { if (play && !reduced) { p.snapTo(0f); p.animateTo(1f, tween(600, easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f))) } }
    Canvas(modifier.size(size)) {
        val base = this.size.minDimension * 0.35f
        for (k in 0..2) {
            val local = ((p.value * 1f) - k * 0.13f).coerceIn(0f, 0.6f) / 0.6f
            if (local <= 0f || local >= 1f) continue
            drawCircle(
                if (k % 2 == 0) colorA else colorB,
                radius = base * (1f + 0.5f * local),
                alpha = (0.7f - k * 0.15f) * (1f - local),
                style = Stroke((6f - k * 1.5f).dp.toPx())
            )
        }
    }
}

/** RIPPLE — listening. Only while the mic is truly open. [level] = smoothed mic level 0..1. */
@Composable
fun HaloRipple(listening: Boolean, level: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    if (!listening) return
    val reduced = rememberHaloReducedMotion()
    val t = rememberInfiniteTransition(label = "ripple")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase")
    Canvas(modifier.size(size)) {
        val base = this.size.minDimension * 0.35f
        for (k in 0..2) {
            val f = if (reduced) 0.3f else (phase + k / 3f) % 1f
            drawCircle(color, radius = base * (1f + (0.2f + 0.25f * level) * f), alpha = 0.55f * (1f - f), style = Stroke(3.dp.toPx()))
        }
    }
}

// ───────────────────────── Motion v2.1 — the last seven moments ─────────────────────────
private val Glide = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * PULL TO REFRESH — the Open Ring draws as you pull ([pullFraction] 0..1), closes into a full ring on release,
 * breathes while [refreshing], then opens back to its gap. Use with Material3 PullToRefreshBox's state.
 * Lottie twin: halo_fx_pull_refresh_* (markers pull / release / refreshing / done).
 */
@Composable
fun HaloPullRing(pullFraction: Float, refreshing: Boolean, ringStart: Color, ringEnd: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val reduced = rememberHaloReducedMotion()
    val close by animateFloatAsState(if (refreshing) 1f else 0f, tween(300, easing = Glide), label = "close")
    val t = rememberInfiniteTransition(label = "pullBreath")
    val breath by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(800, easing = BreathEase), RepeatMode.Reverse), label = "b")
    Canvas(modifier.size(size)) {
        val gap = 44f * (1f - close)
        val sweep = (360f - gap) * if (refreshing) 1f else pullFraction.coerceIn(0f, 1f)
        val stroke = this.size.minDimension * 0.11f
        val inset = stroke / 2f + this.size.minDimension * 0.12f
        drawArc(
            Brush.linearGradient(listOf(ringStart, ringEnd)),
            startAngle = -45f + gap / 2f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
            alpha = if (refreshing && !reduced) breath else 0.4f + 0.6f * pullFraction.coerceIn(0f, 1f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * MODEL DOWNLOAD — the ring fills with real progress (0..1) from the upper-right gap, then closes into a check.
 * Lottie twin: halo_fx_download_ring_* (frame = percent 0..100, then marker "complete").
 */
@Composable
fun HaloProgressRing(progress: Float, ringStart: Color, ringEnd: Color, track: Color, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400, easing = Glide), label = "progress")
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.07f
        val inset = stroke / 2f + this.size.minDimension * 0.08f
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, alpha = 0.1f, style = Stroke(stroke))
        drawArc(Brush.linearGradient(listOf(ringStart, ringEnd)), -45f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    // At p == 1f: crossfade the download glyph to a check and play HaloBloom once.
}

/**
 * DRAFT DRAW-ON — halo's new canvas card draws its dashed Mint border (0.9 s), then the content fades in.
 * Keep the dashed border until the user taps Keep. Lottie twin: halo_fx_draft_drawon_*.
 */
fun Modifier.haloDraftBorder(visible: Boolean, color: Color, corner: Dp = 14.dp): Modifier = composed {
    val reduced = rememberHaloReducedMotion()
    val draw = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(visible) { if (visible && !reduced) draw.animateTo(1f, tween(900, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) }
    drawWithContent {
        drawContent()
        val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx())) }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path(); measure.getSegment(0f, measure.length * draw.value, partial, true)
        drawPath(partial, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
    }
}

/**
 * CANVAS ARRANGE — "Tidy the board": each card glides to its target with [haloGlideTo]; stagger 100 ms per card,
 * 0.9 s, Glide easing, rotation settles to 0. Then the new group frame scales 96→102→100 % once.
 * Lottie twin: halo_fx_canvas_arrange_*.
 */
@Composable
fun haloGlideOffset(target: Offset, index: Int): State<Offset> {
    val anim = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target) { kotlinx.coroutines.delay(100L * index); anim.animateTo(target, tween(900, easing = Glide)) }
    return anim.asState()
}

/**
 * ACCENT SWEEP — when the accent changes, the new ring colour sweeps in from the gap over the old one (0.75 s).
 * Works for any pair. Lottie twins: halo_fx_accent_sweep_{from}_to_{to}_*.
 */
@Composable
fun HaloAccentSweepRing(ringStart: Color, ringEnd: Color, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    var old by remember { mutableStateOf(ringStart to ringEnd) }
    var new by remember { mutableStateOf(ringStart to ringEnd) }
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(ringStart, ringEnd) {
        if (new != (ringStart to ringEnd)) { old = new; new = ringStart to ringEnd; sweep.snapTo(0f); sweep.animateTo(1f, tween(750, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))); old = new }
    }
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.075f
        val inset = stroke / 2f + this.size.minDimension * 0.08f
        val sz = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
        val start = -45f + 22f
        drawArc(Brush.linearGradient(listOf(old.first, old.second)), start, 316f, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Brush.linearGradient(listOf(new.first, new.second)), start, 316f * sweep.value, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/*
 * SHARED ORB — the orb is ONE object across Home, Mini Halo, approval sheet and Floating Halo.
 * Compose 1.7+:
 *
 * SharedTransitionLayout {
 *   AnimatedContent(targetState = screen, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }) { s ->
 *     HaloOrb(
 *       state = presence,
 *       modifier = Modifier.sharedElement(
 *         rememberSharedContentState(key = "halo-orb"), animatedVisibilityScope = this@AnimatedContent,
 *         boundsTransform = { _, _ -> tween(800, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)) }
 *       ).size(if (s == Screen.Home) 108.dp else 40.dp)
 *     )
 *   }
 * }
 * Rules: the orb keeps its presence state during the move (no restart of its Lottie); everything else crossfades;
 * reduced motion = crossfade only.
 */

/**
 * KIDS READ-ALONG — each word glows as halo says it. Feed [currentWord] from the TTS word-boundary callback
 * (UtteranceProgressListener.onRangeStart). Tapping a word should re-speak it.
 */
@Composable
fun KidsReadAlong(words: List<String>, currentWord: Int, glow: Color, text: Color, upcoming: Color, modifier: Modifier = Modifier) {
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        words.forEachIndexed { i, w ->
            val style = when {
                i == currentWord -> androidx.compose.ui.text.SpanStyle(color = text, background = glow.copy(alpha = 0.28f))
                i < currentWord -> androidx.compose.ui.text.SpanStyle(color = text)
                else -> androidx.compose.ui.text.SpanStyle(color = upcoming)
            }
            withStyle(style) { append(w) }
            if (i < words.lastIndex) append(" ")
        }
    }
    androidx.compose.material3.Text(annotated, modifier, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
}
