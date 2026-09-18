package com.halo.app.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloRuntimeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Halo's primary presence surface.
 *
 * The renderer deliberately uses a small number of large geometric marks rather
 * than a dense particle cloud. Every runtime state has a readable silhouette and
 * a distinct semantic colour, so the orb still communicates before the user reads
 * the label underneath it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HaloStateOrb(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String = "Halo"
) {
    val profile = orbProfile(state)
    val transition = rememberInfiniteTransition(label = "halo_presence")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "halo_orb_press"
    )
    val shellScale by animateFloatAsState(
        targetValue = profile.scale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 150f),
        label = "halo_orb_scale"
    )
    val glowStrength by animateFloatAsState(
        targetValue = profile.glow,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "halo_orb_glow"
    )
    val primary by animateColorAsState(
        targetValue = profile.primary,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "halo_orb_primary"
    )
    val secondary by animateColorAsState(
        targetValue = profile.secondary,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "halo_orb_secondary"
    )

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_orb_phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(profile.pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_orb_pulse"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick
                        )
                        .semantics {
                            this.contentDescription = contentDescription
                            role = Role.Button
                        }
                } else {
                    Modifier
                }
            )
    ) {
        drawPresenceOrb(
            state = state,
            profile = profile,
            primary = primary,
            secondary = secondary,
            shellScale = shellScale,
            glowStrength = glowStrength,
            phase = phase,
            pulse = pulse
        )
    }
}

private enum class OrbGlyph {
    REST,
    LISTEN,
    WAKE,
    CAPTURE,
    RECOGNIZE,
    REASON,
    PLAN,
    EXECUTE,
    SPEAK,
    RECOVER,
    STOP
}

private data class OrbProfile(
    val primary: Color,
    val secondary: Color,
    val scale: Float,
    val glow: Float,
    val pulseDepth: Float,
    val rotationTurns: Float,
    val vertices: Int,
    val nodeCount: Int,
    val pulseDurationMs: Int,
    val glyph: OrbGlyph
)

private fun orbProfile(state: HaloRuntimeState): OrbProfile = when (state) {
    HaloRuntimeState.Idle -> OrbProfile(
        primary = Color(0xFF7DE2B1),
        secondary = Color(0xFFCBFFE7),
        scale = 1.00f,
        glow = 0.86f,
        pulseDepth = 0.018f,
        rotationTurns = 0f,
        vertices = 10,
        nodeCount = 8,
        pulseDurationMs = 3_800,
        glyph = OrbGlyph.REST
    )

    HaloRuntimeState.Listening -> OrbProfile(
        primary = Color(0xFF55ECFF),
        secondary = Color(0xFF7C91FF),
        scale = 1.04f,
        glow = 1.00f,
        pulseDepth = 0.045f,
        rotationTurns = 0.42f,
        vertices = 12,
        nodeCount = 10,
        pulseDurationMs = 1_650,
        glyph = OrbGlyph.LISTEN
    )

    HaloRuntimeState.Wakeword -> OrbProfile(
        primary = Color(0xFFF2FFFF),
        secondary = Color(0xFF56EFFF),
        scale = 1.07f,
        glow = 1.10f,
        pulseDepth = 0.065f,
        rotationTurns = 0.18f,
        vertices = 8,
        nodeCount = 8,
        pulseDurationMs = 850,
        glyph = OrbGlyph.WAKE
    )

    HaloRuntimeState.Capturing -> OrbProfile(
        primary = Color(0xFF42FFC2),
        secondary = Color(0xFF00D9FF),
        scale = 1.08f,
        glow = 1.08f,
        pulseDepth = 0.070f,
        rotationTurns = 0.10f,
        vertices = 10,
        nodeCount = 8,
        pulseDurationMs = 900,
        glyph = OrbGlyph.CAPTURE
    )

    HaloRuntimeState.Recognizing -> OrbProfile(
        primary = Color(0xFF73A8FF),
        secondary = Color(0xFFAD8CFF),
        scale = 1.01f,
        glow = 0.95f,
        pulseDepth = 0.030f,
        rotationTurns = 0.88f,
        vertices = 12,
        nodeCount = 9,
        pulseDurationMs = 1_300,
        glyph = OrbGlyph.RECOGNIZE
    )

    HaloRuntimeState.Reasoning -> OrbProfile(
        primary = Color(0xFFB77CFF),
        secondary = Color(0xFF63E8FF),
        scale = 1.03f,
        glow = 1.00f,
        pulseDepth = 0.040f,
        rotationTurns = 0.26f,
        vertices = 6,
        nodeCount = 6,
        pulseDurationMs = 1_450,
        glyph = OrbGlyph.REASON
    )

    HaloRuntimeState.Planning -> OrbProfile(
        primary = Color(0xFF8D83FF),
        secondary = Color(0xFF65FFD8),
        scale = 1.03f,
        glow = 0.98f,
        pulseDepth = 0.035f,
        rotationTurns = 0.62f,
        vertices = 8,
        nodeCount = 8,
        pulseDurationMs = 1_250,
        glyph = OrbGlyph.PLAN
    )

    HaloRuntimeState.Executing -> OrbProfile(
        primary = Color(0xFFFFD166),
        secondary = Color(0xFF58E8FF),
        scale = 1.06f,
        glow = 1.05f,
        pulseDepth = 0.050f,
        rotationTurns = 1.05f,
        vertices = 7,
        nodeCount = 7,
        pulseDurationMs = 900,
        glyph = OrbGlyph.EXECUTE
    )

    HaloRuntimeState.Speaking -> OrbProfile(
        primary = Color(0xFFFF7DCE),
        secondary = Color(0xFF6AFFF0),
        scale = 1.07f,
        glow = 1.08f,
        pulseDepth = 0.075f,
        rotationTurns = 0.12f,
        vertices = 10,
        nodeCount = 8,
        pulseDurationMs = 760,
        glyph = OrbGlyph.SPEAK
    )

    HaloRuntimeState.Recovering -> OrbProfile(
        primary = Color(0xFFFFB85C),
        secondary = Color(0xFFFF765C),
        scale = 1.02f,
        glow = 0.95f,
        pulseDepth = 0.055f,
        rotationTurns = 0.34f,
        vertices = 6,
        nodeCount = 6,
        pulseDurationMs = 1_100,
        glyph = OrbGlyph.RECOVER
    )

    HaloRuntimeState.Stopped -> OrbProfile(
        primary = Color(0xFFEF6A76),
        secondary = Color(0xFF8A9390),
        scale = 0.94f,
        glow = 0.55f,
        pulseDepth = 0f,
        rotationTurns = 0f,
        vertices = 8,
        nodeCount = 4,
        pulseDurationMs = 3_000,
        glyph = OrbGlyph.STOP
    )
}

private fun DrawScope.drawPresenceOrb(
    state: HaloRuntimeState,
    profile: OrbProfile,
    primary: Color,
    secondary: Color,
    shellScale: Float,
    glowStrength: Float,
    phase: Float,
    pulse: Float
) {
    val extent = min(size.width, size.height)
    val center = Offset(size.width / 2f, size.height / 2f)
    val wave = ((sin(pulse * TWO_PI - HALF_PI) + 1f) * 0.5f)
    val shellRadius = extent * 0.305f * shellScale * (1f + profile.pulseDepth * wave)
    val rotation = phase * 360f * profile.rotationTurns

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = 0.22f * glowStrength),
                primary.copy(alpha = 0.10f * glowStrength),
                secondary.copy(alpha = 0.045f * glowStrength),
                Color.Transparent
            ),
            center = center,
            radius = extent * 0.49f
        ),
        radius = extent * 0.49f,
        center = center
    )

    drawCircle(
        color = primary.copy(alpha = 0.10f * glowStrength),
        radius = shellRadius * 1.16f,
        center = center
    )

    drawPixelRing(
        center = center,
        radius = extent * 0.405f,
        count = profile.nodeCount,
        rotationDegrees = rotation,
        phase = phase,
        state = state,
        primary = primary,
        secondary = secondary,
        extent = extent
    )

    drawPolygonShell(
        center = center,
        radius = shellRadius,
        vertices = profile.vertices,
        rotationDegrees = rotation,
        state = state,
        phase = phase,
        primary = primary,
        secondary = secondary,
        extent = extent
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondary.copy(alpha = 0.18f),
                primary.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = center,
            radius = shellRadius * 0.72f
        ),
        radius = shellRadius * 0.72f,
        center = center
    )

    drawStateGlyph(
        glyph = profile.glyph,
        center = center,
        radius = shellRadius * 0.62f,
        primary = primary,
        secondary = secondary,
        phase = phase,
        pulse = wave,
        extent = extent
    )
}

private fun DrawScope.drawPixelRing(
    center: Offset,
    radius: Float,
    count: Int,
    rotationDegrees: Float,
    phase: Float,
    state: HaloRuntimeState,
    primary: Color,
    secondary: Color,
    extent: Float
) {
    val activeIndex = ((phase * count).toInt()) % count
    val base = extent * 0.026f

    repeat(count) { index ->
        val angle = (TWO_PI * index / count) + degreesToRadians(rotationDegrees)
        val position = Offset(
            center.x + cos(angle) * radius,
            center.y + sin(angle) * radius
        )
        val active = when (state) {
            HaloRuntimeState.Listening,
            HaloRuntimeState.Recognizing,
            HaloRuntimeState.Planning,
            HaloRuntimeState.Executing,
            HaloRuntimeState.Recovering -> index == activeIndex
            HaloRuntimeState.Speaking -> index % 2 == activeIndex % 2
            HaloRuntimeState.Wakeword -> true
            else -> false
        }
        val pixelSize = if (active) base * 1.48f else base
        val color = if (index % 2 == 0) primary else secondary

        drawRoundRect(
            color = color.copy(alpha = if (active) 0.98f else 0.58f),
            topLeft = Offset(position.x - pixelSize / 2f, position.y - pixelSize / 2f),
            size = Size(pixelSize, pixelSize),
            cornerRadius = CornerRadius(pixelSize * 0.14f, pixelSize * 0.14f)
        )
    }
}

private fun DrawScope.drawPolygonShell(
    center: Offset,
    radius: Float,
    vertices: Int,
    rotationDegrees: Float,
    state: HaloRuntimeState,
    phase: Float,
    primary: Color,
    secondary: Color,
    extent: Float
) {
    val outer = polygonPath(
        center = center,
        radius = radius,
        vertices = vertices,
        rotationDegrees = rotationDegrees,
        state = state,
        phase = phase
    )
    val inner = polygonPath(
        center = center,
        radius = radius * 0.78f,
        vertices = vertices,
        rotationDegrees = -rotationDegrees * 0.45f,
        state = HaloRuntimeState.Idle,
        phase = phase
    )

    drawPath(
        path = outer,
        color = primary.copy(alpha = 0.11f)
    )
    drawPath(
        path = outer,
        color = primary.copy(alpha = 0.96f),
        style = Stroke(width = extent * 0.013f)
    )
    drawPath(
        path = inner,
        color = secondary.copy(alpha = 0.34f),
        style = Stroke(width = extent * 0.006f)
    )
}

private fun polygonPath(
    center: Offset,
    radius: Float,
    vertices: Int,
    rotationDegrees: Float,
    state: HaloRuntimeState,
    phase: Float
): Path {
    val path = Path()
    repeat(vertices) { index ->
        val angle = -HALF_PI + TWO_PI * index / vertices + degreesToRadians(rotationDegrees)
        val stateScale = when (state) {
            HaloRuntimeState.Capturing -> 1f + 0.08f * sin(angle * 3f + phase * TWO_PI * 2f)
            HaloRuntimeState.Reasoning -> if (index % 2 == 0) 1.10f else 0.90f
            HaloRuntimeState.Planning -> if (index % 2 == 0) 1.05f else 0.96f
            HaloRuntimeState.Executing -> if (index == 1 || index == 2) 1.12f else 0.97f
            HaloRuntimeState.Speaking -> 1f + 0.055f * sin(angle * 4f + phase * TWO_PI * 3f)
            HaloRuntimeState.Recovering -> if (index % 3 == 0) 0.82f else 1.08f
            else -> 1f
        }
        val point = Offset(
            center.x + cos(angle) * radius * stateScale,
            center.y + sin(angle) * radius * stateScale
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

private fun DrawScope.drawStateGlyph(
    glyph: OrbGlyph,
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    pulse: Float,
    extent: Float
) {
    when (glyph) {
        OrbGlyph.REST -> drawRestGlyph(center, radius, primary, secondary, extent)
        OrbGlyph.LISTEN -> drawListenGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.WAKE -> drawWakeGlyph(center, radius, primary, secondary, pulse, extent)
        OrbGlyph.CAPTURE -> drawCaptureGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.RECOGNIZE -> drawRecognizeGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.REASON -> drawReasonGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.PLAN -> drawPlanGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.EXECUTE -> drawExecuteGlyph(center, radius, primary, secondary, extent)
        OrbGlyph.SPEAK -> drawSpeakGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.RECOVER -> drawRecoverGlyph(center, radius, primary, secondary, phase, extent)
        OrbGlyph.STOP -> drawStopGlyph(center, radius, primary, secondary, extent)
    }
}

private fun DrawScope.drawRestGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    extent: Float
) {
    val diamond = Path().apply {
        moveTo(center.x, center.y - radius * 0.33f)
        lineTo(center.x + radius * 0.33f, center.y)
        lineTo(center.x, center.y + radius * 0.33f)
        lineTo(center.x - radius * 0.33f, center.y)
        close()
    }
    drawPath(diamond, primary.copy(alpha = 0.18f))
    drawPath(diamond, primary, style = Stroke(width = extent * 0.012f))
    drawCircle(secondary, radius * 0.08f, center)
}

private fun DrawScope.drawListenGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    val ringSize = Size(radius * 1.45f, radius * 1.45f)
    val topLeft = Offset(center.x - ringSize.width / 2f, center.y - ringSize.height / 2f)
    drawArc(
        color = primary,
        startAngle = 125f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = topLeft,
        size = ringSize,
        style = Stroke(width = extent * 0.014f, cap = StrokeCap.Round)
    )
    drawArc(
        color = secondary.copy(alpha = 0.82f),
        startAngle = -55f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = topLeft,
        size = ringSize,
        style = Stroke(width = extent * 0.010f, cap = StrokeCap.Round)
    )
    drawCircle(primary, radius * 0.12f, center)

    val sweepAngle = -HALF_PI + phase * TWO_PI
    val sweepPoint = Offset(
        center.x + cos(sweepAngle) * radius * 0.72f,
        center.y + sin(sweepAngle) * radius * 0.72f
    )
    drawPixel(sweepPoint, extent * 0.040f, secondary, 1f)
}

private fun DrawScope.drawWakeGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    pulse: Float,
    extent: Float
) {
    val diamondRadius = radius * (0.38f + 0.06f * pulse)
    val path = Path().apply {
        moveTo(center.x, center.y - diamondRadius)
        lineTo(center.x + diamondRadius, center.y)
        lineTo(center.x, center.y + diamondRadius)
        lineTo(center.x - diamondRadius, center.y)
        close()
    }
    drawPath(path, primary.copy(alpha = 0.30f))
    drawPath(path, primary, style = Stroke(width = extent * 0.016f))
    drawCircle(
        color = secondary.copy(alpha = 0.72f - pulse * 0.24f),
        radius = radius * (0.52f + pulse * 0.18f),
        center = center,
        style = Stroke(width = extent * 0.008f)
    )
    drawCircle(Color.White.copy(alpha = 0.92f), radius * 0.09f, center)
}

private fun DrawScope.drawCaptureGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    val bars = 5
    val barWidth = radius * 0.17f
    val gap = radius * 0.11f
    val total = bars * barWidth + (bars - 1) * gap
    val startX = center.x - total / 2f

    repeat(bars) { index ->
        val level = 0.32f + 0.62f * normalizedWave(phase * 2f + index * 0.18f)
        val height = radius * level
        drawRoundRect(
            color = if (index == bars / 2) secondary else primary,
            topLeft = Offset(startX + index * (barWidth + gap), center.y - height / 2f),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth * 0.28f, barWidth * 0.28f)
        )
    }
    drawLine(
        color = primary.copy(alpha = 0.28f),
        start = Offset(center.x - radius * 0.88f, center.y),
        end = Offset(center.x + radius * 0.88f, center.y),
        strokeWidth = extent * 0.006f
    )
}

private fun DrawScope.drawRecognizeGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    drawCircle(primary.copy(alpha = 0.92f), radius * 0.50f, center, style = Stroke(extent * 0.012f))
    drawCircle(secondary.copy(alpha = 0.60f), radius * 0.26f, center, style = Stroke(extent * 0.008f))
    drawCircle(primary, radius * 0.07f, center)

    repeat(3) { index ->
        val angle = phase * TWO_PI * 1.7f + TWO_PI * index / 3f
        val point = Offset(
            center.x + cos(angle) * radius * 0.67f,
            center.y + sin(angle) * radius * 0.67f
        )
        drawPixel(point, extent * 0.035f, if (index == 0) secondary else primary, 0.95f)
    }
}

private fun DrawScope.drawReasonGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    val rotation = phase * 55f
    rotate(rotation, center) {
        val points = listOf(
            Offset(center.x, center.y - radius * 0.55f),
            Offset(center.x + radius * 0.52f, center.y - radius * 0.12f),
            Offset(center.x + radius * 0.28f, center.y + radius * 0.50f),
            Offset(center.x - radius * 0.34f, center.y + radius * 0.42f),
            Offset(center.x - radius * 0.54f, center.y - radius * 0.12f)
        )
        points.indices.forEach { index ->
            val next = (index + 2) % points.size
            drawLine(
                color = primary.copy(alpha = 0.42f),
                start = points[index],
                end = points[next],
                strokeWidth = extent * 0.007f
            )
        }
        points.forEachIndexed { index, point ->
            drawPixel(
                point,
                extent * if (index == 0) 0.047f else 0.034f,
                if (index % 2 == 0) secondary else primary,
                0.96f
            )
        }
    }
}

private fun DrawScope.drawPlanGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    val box = Size(radius * 1.16f, radius * 1.16f)
    val topLeft = Offset(center.x - box.width / 2f, center.y - box.height / 2f)
    repeat(6) { index ->
        val active = index == ((phase * 6f).toInt() % 6)
        drawArc(
            color = if (active) secondary else primary.copy(alpha = 0.54f),
            startAngle = -90f + index * 60f + phase * 40f,
            sweepAngle = 34f,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = extent * if (active) 0.016f else 0.010f, cap = StrokeCap.Round)
        )
    }
    val core = extent * 0.080f
    drawRoundRect(
        color = secondary.copy(alpha = 0.90f),
        topLeft = Offset(center.x - core / 2f, center.y - core / 2f),
        size = Size(core, core),
        cornerRadius = CornerRadius(core * 0.12f, core * 0.12f)
    )
}

private fun DrawScope.drawExecuteGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    extent: Float
) {
    val left = center.x - radius * 0.45f
    val right = center.x + radius * 0.48f
    val top = center.y - radius * 0.40f
    val bottom = center.y + radius * 0.40f

    drawLine(primary, Offset(left, top), Offset(center.x, center.y), extent * 0.016f, cap = StrokeCap.Round)
    drawLine(primary, Offset(left, bottom), Offset(center.x, center.y), extent * 0.016f, cap = StrokeCap.Round)
    drawLine(secondary, Offset(center.x - radius * 0.08f, top), Offset(right, center.y), extent * 0.016f, cap = StrokeCap.Round)
    drawLine(secondary, Offset(center.x - radius * 0.08f, bottom), Offset(right, center.y), extent * 0.016f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSpeakGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    val bars = 5
    val barWidth = radius * 0.13f
    val gap = radius * 0.10f
    val total = bars * barWidth + (bars - 1) * gap
    val startX = center.x - total / 2f
    repeat(bars) { index ->
        val level = 0.28f + 0.68f * normalizedWave(phase * 3f + index * 0.21f)
        val height = radius * level
        drawRoundRect(
            color = if (index % 2 == 0) primary else secondary,
            topLeft = Offset(startX + index * (barWidth + gap), center.y - height / 2f),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }

    val arcSize = Size(radius * 1.80f, radius * 1.80f)
    val topLeft = Offset(center.x - arcSize.width / 2f, center.y - arcSize.height / 2f)
    drawArc(
        color = primary.copy(alpha = 0.44f),
        startAngle = 322f,
        sweepAngle = 76f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = extent * 0.008f, cap = StrokeCap.Round)
    )
    drawArc(
        color = secondary.copy(alpha = 0.44f),
        startAngle = 142f,
        sweepAngle = 76f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = extent * 0.008f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawRecoverGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    phase: Float,
    extent: Float
) {
    val box = Size(radius * 1.25f, radius * 1.25f)
    val topLeft = Offset(center.x - box.width / 2f, center.y - box.height / 2f)
    repeat(4) { index ->
        drawArc(
            color = if (index % 2 == 0) primary else secondary,
            startAngle = phase * 120f + index * 90f,
            sweepAngle = 48f,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = extent * 0.014f, cap = StrokeCap.Round)
        )
    }
    drawPixel(center, extent * 0.060f, primary, 0.94f)
}

private fun DrawScope.drawStopGlyph(
    center: Offset,
    radius: Float,
    primary: Color,
    secondary: Color,
    extent: Float
) {
    val diagonal = radius * 0.43f
    drawLine(
        primary,
        Offset(center.x - diagonal, center.y - diagonal),
        Offset(center.x + diagonal, center.y + diagonal),
        extent * 0.018f,
        cap = StrokeCap.Round
    )
    drawLine(
        secondary,
        Offset(center.x + diagonal, center.y - diagonal),
        Offset(center.x - diagonal, center.y + diagonal),
        extent * 0.018f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawPixel(
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float
) {
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(center.x - size / 2f, center.y - size / 2f),
        size = Size(size, size),
        cornerRadius = CornerRadius(size * 0.12f, size * 0.12f)
    )
}

private fun normalizedWave(value: Float): Float =
    ((sin(value * TWO_PI) + 1f) * 0.5f)

private fun degreesToRadians(degrees: Float): Float = degrees * PI.toFloat() / 180f

private const val TWO_PI = (PI * 2.0).toFloat()
private const val HALF_PI = (PI / 2.0).toFloat()

@Preview(showBackground = true, backgroundColor = 0xFF080B0B)
@Composable
private fun HaloStateOrbPreview() {
    Column(modifier = Modifier.padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HaloStateOrb(HaloRuntimeState.Idle, size = 112.dp)
            HaloStateOrb(HaloRuntimeState.Capturing, size = 112.dp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(top = 20.dp)
        ) {
            HaloStateOrb(HaloRuntimeState.Reasoning, size = 112.dp)
            HaloStateOrb(HaloRuntimeState.Speaking, size = 112.dp)
        }
    }
}
