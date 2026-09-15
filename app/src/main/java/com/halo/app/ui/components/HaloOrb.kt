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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
 * High-level entry point: interactive Halo orb with subtle motion,
 * gradient caustics and orbiting particles.
 */
@Composable
fun HaloOrb(
    presence: HaloPresenceUi,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    onTap: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo-orb-transition")

    // Base breathing scale
    val baseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2600,
                easing = FastOutSlowInEasing
            )
        ),
        label = "halo-orb-breath"
    )

    // Slow rotation for caustics
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 12000,
                easing = LinearEasing
            )
        ),
        label = "halo-orb-rotation"
    )

    // Signal intensity by presence state
    val signalIntensity by animateFloatAsState(
        targetValue = when (presence.state) {
            HaloPresenceState.Idle -> 0.35f
            HaloPresenceState.Listening -> 0.8f
            HaloPresenceState.Thinking -> 0.65f
            HaloPresenceState.Acting -> 1.0f // Mapped Speaking to Acting to align with model
            HaloPresenceState.Error -> 0.6f
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "halo-orb-signal-intensity"
    )

    // Core color by presence
    val coreColor by animateColorAsState(
        targetValue = when (presence.state) {
            HaloPresenceState.Idle -> HaloPrimary.copy(alpha = 0.7f)
            HaloPresenceState.Listening -> HaloPrimaryStrong
            HaloPresenceState.Thinking -> HaloInformation
            HaloPresenceState.Acting -> HaloPrimaryStrong.copy(alpha = 0.9f)
            HaloPresenceState.Error -> HaloDanger
        },
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "halo-orb-core-color"
    )

    // Outer halo color by presence
    val outerColor by animateColorAsState(
        targetValue = when (presence.state) {
            HaloPresenceState.Idle -> HaloSignalSurface
            HaloPresenceState.Listening -> HaloInformation.copy(alpha = 0.5f)
            HaloPresenceState.Thinking -> HaloInformation.copy(alpha = 0.35f)
            HaloPresenceState.Acting -> HaloPrimaryStrong.copy(alpha = 0.6f)
            HaloPresenceState.Error -> HaloWarning.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "halo-orb-outer-color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .blur(18.dp),
        color = Color.Transparent,
        interactionSource = interactionSource,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(presence.isInteractive) {
                    if (presence.isInteractive && onTap != null) {
                        detectTapGestures {
                            onTap.invoke()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background well
                drawHaloBackgroundWell(outerColor)

                // Core orb with caustics
                scale(baseScale) {
                    rotate(rotation) {
                        drawHaloCoreOrb(coreColor, signalIntensity)
                    }
                }

                // Orbiting particles
                drawHaloParticles(coreColor, signalIntensity, rotation)
            }
        }
    }
}

private fun DrawScope.drawHaloBackgroundWell(color: Color) {
    val gradient = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = 0.0f),
            color.copy(alpha = 0.5f),
            color.copy(alpha = 0.9f)
        ),
        center = center,
        radius = size.minDimension / 2.0f
    )

    drawRect(
        brush = gradient,
        size = size
    )

    // Darken edges to blend into HaloBackground
    drawRect(
        color = HaloBackground.copy(alpha = 0.6f),
        size = size
    )
}

private fun DrawScope.drawHaloCoreOrb(coreColor: Color, signalIntensity: Float) {
    val innerRadius = size.minDimension / 3.3f

    val innerGradient = Brush.radialGradient(
        colors = listOf(
            coreColor.copy(alpha = 0.2f),
            coreColor.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.95f)
        ),
        center = center,
        radius = innerRadius
    )

    drawCircle(
        brush = innerGradient,
        radius = innerRadius
    )

    // Inner caustic shape
    val causticPath = Path().apply {
        moveTo(center.x, center.y - innerRadius * 0.7f)
        cubicTo(
            center.x + innerRadius * 0.8f, center.y - innerRadius * 0.1f,
            center.x - innerRadius * 0.2f, center.y + innerRadius * 0.6f,
            center.x, center.y + innerRadius * 0.3f
        )
        cubicTo(
            center.x + innerRadius * 0.4f, center.y + innerRadius * 0.8f,
            center.x - innerRadius * 0.9f, center.y + innerRadius * 0.2f,
            center.x, center.y - innerRadius * 0.7f
        )
        close()
    }

    val causticColor = coreColor.copy(alpha = 0.35f + 0.25f * signalIntensity)

    drawPath(
        path = causticPath,
        color = causticColor
    )
}

private fun DrawScope.drawHaloParticles(coreColor: Color, signalIntensity: Float, rotation: Float) {
    val particleCount = (18 + 24 * signalIntensity).toInt()
    val orbitRadius = size.minDimension / 2.4f

    repeat(particleCount) { index ->
        val progress = index.toFloat() / particleCount.toFloat()

        val angle = progress * 360f + rotation * 0.8f
        val radians = Math.toRadians(angle.toDouble())
        val x = center.x + orbitRadius * kotlin.math.cos(radians).toFloat()
        val y = center.y + orbitRadius * kotlin.math.sin(radians).toFloat()

        val particleScale = 0.9f + 0.6f * signalIntensity
        val particleRadius = (size.minDimension / 120f) * particleScale

        translate(left = x - center.x, top = y - center.y) {
            drawCircle(
                color = coreColor.copy(alpha = 0.35f + 0.3f * progress),
                radius = particleRadius
            )
        }
    }
}
