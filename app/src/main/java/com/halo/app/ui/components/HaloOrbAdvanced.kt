package com.halo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloBackground
import com.halo.app.ui.theme.HaloDanger
import com.halo.app.ui.theme.HaloInformation
import com.halo.app.ui.theme.HaloPrimary
import com.halo.app.ui.theme.HaloPrimaryStrong
import com.halo.app.ui.theme.HaloSignalSurface
import com.halo.app.ui.theme.HaloWarning

/**
 * More aggressive, multi-layer Halo orb:
 * - Deep background well
 * - Core orb with caustic path
 * - Dynamic rings responding to presence state
 * - Orbiting particles
 * - Subtle scanline/glitch overlay
 */
@Composable
fun HaloOrbAdvanced(
    presence: HaloPresenceUi,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    onTap: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo-orb-advanced")

    val baseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing)
        ),
        label = "halo-orb-advanced-breath"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing)
        ),
        label = "halo-orb-advanced-rotation"
    )

    val signalIntensity by animateFloatAsState(
        targetValue = when (presence.state) {
            HaloPresenceState.Idle -> 0.3f
            HaloPresenceState.Listening -> 0.7f
            HaloPresenceState.Thinking -> 0.55f
            HaloPresenceState.Acting -> 1.0f
            HaloPresenceState.Error -> 0.65f
        },
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "halo-orb-advanced-signal-intensity"
    )

    val coreColor by animateColorAsState(
        targetValue = when (presence.state) {
            HaloPresenceState.Idle -> HaloPrimary.copy(alpha = 0.7f)
            HaloPresenceState.Listening -> HaloPrimaryStrong
            HaloPresenceState.Thinking -> HaloInformation
            HaloPresenceState.Acting -> HaloPrimaryStrong.copy(alpha = 0.95f)
            HaloPresenceState.Error -> HaloDanger
        },
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "halo-orb-advanced-core-color"
    )

    val outerColor by animateColorAsState(
        targetValue = when (presence.state) {
            HaloPresenceState.Idle -> HaloSignalSurface
            HaloPresenceState.Listening -> HaloInformation.copy(alpha = 0.55f)
            HaloPresenceState.Thinking -> HaloInformation.copy(alpha = 0.4f)
            HaloPresenceState.Acting -> HaloPrimaryStrong.copy(alpha = 0.7f)
            HaloPresenceState.Error -> HaloWarning.copy(alpha = 0.7f)
        },
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "halo-orb-advanced-outer-color"
    )

    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .blur(20.dp)
            .pointerInput(presence.isInteractive) {
                if (presence.isInteractive && onTap != null) {
                    detectTapGestures(onTap = { onTap.invoke() })
                }
            },
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawHaloWell(outerColor)
                scale(baseScale) {
                    rotate(rotation) {
                        drawCoreOrb(coreColor, signalIntensity)
                    }
                }
                drawRings(coreColor, signalIntensity)
                drawParticles(coreColor, signalIntensity, rotation)
                drawScanlines(signalIntensity)
            }
        }
    }
}

private fun DrawScope.drawHaloWell(color: Color) {
    val gradient = Brush.radialGradient(
        colors = listOf(color.copy(alpha = 0.0f), color.copy(alpha = 0.4f), color.copy(alpha = 0.85f)),
        center = center,
        radius = size.minDimension / 1.9f
    )
    drawRect(brush = gradient, size = size)
    drawRect(color = HaloBackground.copy(alpha = 0.7f), size = size)
}

private fun DrawScope.drawCoreOrb(coreColor: Color, signalIntensity: Float) {
    val innerRadius = size.minDimension / 3.4f
    val innerGradient = Brush.radialGradient(
        colors = listOf(coreColor.copy(alpha = 0.15f), coreColor.copy(alpha = 0.8f), Color.White.copy(alpha = 0.95f)),
        center = center,
        radius = innerRadius
    )
    drawCircle(brush = innerGradient, radius = innerRadius)

    val causticPath = Path().apply {
        moveTo(center.x, center.y - innerRadius * 0.8f)
        cubicTo(
            center.x + innerRadius * 0.9f, center.y - innerRadius * 0.2f,
            center.x - innerRadius * 0.3f, center.y + innerRadius * 0.7f,
            center.x, center.y + innerRadius * 0.4f
        )
        cubicTo(
            center.x + innerRadius * 0.5f, center.y + innerRadius * 0.9f,
            center.x - innerRadius * 1.0f, center.y + innerRadius * 0.1f,
            center.x, center.y - innerRadius * 0.8f
        )
        close()
    }
    drawPath(path = causticPath, color = coreColor.copy(alpha = 0.4f + 0.25f * signalIntensity))
}

private fun DrawScope.drawRings(coreColor: Color, signalIntensity: Float) {
    val baseRadius = size.minDimension / 2.6f
    val ringCount = 3
    repeat(ringCount) { index ->
        val progress = index.toFloat() / ringCount.toFloat()
        val radius = baseRadius + progress * (size.minDimension / 10f)
        val alpha = 0.1f + 0.18f * (signalIntensity * (1f - progress))
        drawCircle(
            color = coreColor.copy(alpha = alpha),
            radius = radius,
            style = Stroke(width = (size.minDimension / 220f) * (1f + progress * 1.5f))
        )
    }
}

private fun DrawScope.drawParticles(coreColor: Color, signalIntensity: Float, rotation: Float) {
    val particleCount = (22 + 26 * signalIntensity).toInt()
    val orbitRadius = size.minDimension / 2.3f
    repeat(particleCount) { index ->
        val progress = index.toFloat() / particleCount.toFloat()
        val angle = progress * 360f + rotation * 0.9f
        val radians = Math.toRadians(angle.toDouble())
        val x = center.x + orbitRadius * kotlin.math.cos(radians).toFloat()
        val y = center.y + orbitRadius * kotlin.math.sin(radians).toFloat()
        val particleRadius = (size.minDimension / 130f) * (0.8f + signalIntensity * 0.9f)
        drawCircle(
            color = coreColor.copy(alpha = 0.2f + 0.4f * progress),
            radius = particleRadius,
            center = androidx.compose.ui.geometry.Offset(x, y)
        )
    }
}

private fun DrawScope.drawScanlines(signalIntensity: Float) {
    val lineCount = 18
    val spacing = size.height / (lineCount + 1).toFloat()
    repeat(lineCount) { index ->
        val y = spacing * (index + 1)
        val alpha = 0.03f + 0.05f * signalIntensity
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 0.8f
        )
    }
}
