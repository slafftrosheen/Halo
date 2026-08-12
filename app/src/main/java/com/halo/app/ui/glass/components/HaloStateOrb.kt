package com.halo.app.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloRuntimeState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import kotlin.math.cos
import kotlin.math.sin

/**
 * Halo State Orb - Visualizes runtime state with sophisticated animations.
 * Adheres to HALO-GLASS-DESIGN.md motion and state rules.
 */
@Composable
fun HaloStateOrb(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo_state_orb")
    
    // Shared color animation for transitions between states
    val stateColor by animateColorAsState(
        targetValue = getStateColor(state),
        animationSpec = tween(500),
        label = "state_color"
    )

    // Common animations used across different states
    val pulse4s by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse_4s"
    )
    
    val pulse1_5s by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing)),
        label = "pulse_1_5s"
    )

    val rotation2s by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation_2s"
    )

    val sonarPulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "sonar_pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(size.toPx() / 2, size.toPx() / 2)
        val radius = size.toPx() / 2

        when (state) {
            HaloRuntimeState.Idle, HaloRuntimeState.Recovering -> {
                // Thin ring, subtle breath animation
                val color = if (state == HaloRuntimeState.Recovering) HaloGlassColors.Danger else stateColor
                drawCircle(
                    color = color.copy(alpha = pulse4s),
                    radius = radius * 0.9f,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> {
                // Filled dot + pulse ring
                drawCircle(color = stateColor, radius = radius * 0.4f)
                drawCircle(
                    color = stateColor.copy(alpha = 1f - pulse1_5s),
                    radius = radius * (0.4f + 0.5f * pulse1_5s),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            HaloRuntimeState.Capturing -> {
                // Solid filled dot with waveform "vibration"
                drawCircle(color = stateColor, radius = radius * 0.5f)
                // Minimal waveform lines from center
                for (i in 0..3) {
                    val angle = (rotation2s + i * 90f) * (Math.PI / 180f).toFloat()
                    val lineLen = radius * 0.2f * pulse1_5s
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = center,
                        end = Offset(
                            center.x + cos(angle) * (radius * 0.5f + lineLen),
                            center.y + sin(angle) * (radius * 0.5f + lineLen)
                        ),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            HaloRuntimeState.Recognizing -> {
                // Ring with 3 orbiting dots
                drawCircle(
                    color = stateColor.copy(alpha = 0.3f),
                    radius = radius * 0.8f,
                    style = Stroke(width = 1.dp.toPx())
                )
                for (i in 0..2) {
                    val angle = (rotation2s + i * 120f) * (Math.PI / 180f).toFloat()
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(
                            center.x + cos(angle) * radius * 0.8f,
                            center.y + sin(angle) * radius * 0.8f
                        )
                    )
                }
            }

            HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> {
                // Dot with scale pulse
                val scale = if (state == HaloRuntimeState.Reasoning) 0.8f + 0.1f * pulse1_5s else 0.9f
                drawCircle(color = stateColor, radius = radius * 0.5f * scale)
                
                if (state == HaloRuntimeState.Planning) {
                    // Ring segments fill clockwise
                    drawArc(
                        color = stateColor,
                        startAngle = -90f,
                        sweepAngle = rotation2s,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(radius * 1.8f, radius * 1.8f),
                        topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f)
                    )
                }
            }

            HaloRuntimeState.Executing -> {
                // Dot + rotating progress ring
                drawCircle(color = stateColor, radius = radius * 0.4f)
                drawArc(
                    color = stateColor,
                    startAngle = rotation2s,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(radius * 1.6f, radius * 1.6f),
                    topLeft = Offset(center.x - radius * 0.8f, center.y - radius * 0.8f)
                )
            }

            HaloRuntimeState.Speaking -> {
                // Dot + outward sonar pulses
                drawCircle(color = stateColor, radius = radius * 0.45f)
                for (i in 0..1) {
                    val pulse = (sonarPulse + i * 0.5f) % 1f
                    drawCircle(
                        color = stateColor.copy(alpha = 1f - pulse),
                        radius = radius * (0.45f + 0.55f * pulse),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            HaloRuntimeState.Stopped -> {
                // Cross visualization fading to idle
                val alpha = pulse4s // Reuse pulse for fade
                val crossSize = radius * 0.3f
                drawLine(
                    color = HaloGlassColors.Danger.copy(alpha = alpha),
                    start = Offset(center.x - crossSize, center.y - crossSize),
                    end = Offset(center.x + crossSize, center.y + crossSize),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = HaloGlassColors.Danger.copy(alpha = alpha),
                    start = Offset(center.x + crossSize, center.y - crossSize),
                    end = Offset(center.x - crossSize, center.y + crossSize),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewHaloStateOrb() {
    Column(
        modifier = Modifier
            .background(HaloGlassColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HaloStateOrb(state = HaloRuntimeState.Idle)
            HaloStateOrb(state = HaloRuntimeState.Listening)
            HaloStateOrb(state = HaloRuntimeState.Capturing)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HaloStateOrb(state = HaloRuntimeState.Recognizing)
            HaloStateOrb(state = HaloRuntimeState.Reasoning)
            HaloStateOrb(state = HaloRuntimeState.Planning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HaloStateOrb(state = HaloRuntimeState.Executing)
            HaloStateOrb(state = HaloRuntimeState.Speaking)
            HaloStateOrb(state = HaloRuntimeState.Recovering)
        }
    }
}


private fun getStateColor(state: HaloRuntimeState): Color = when (state) {
    HaloRuntimeState.Idle, HaloRuntimeState.Stopped -> HaloGlassColors.TextMuted
    HaloRuntimeState.Listening, HaloRuntimeState.Wakeword, HaloRuntimeState.Speaking -> HaloGlassColors.Accent
    HaloRuntimeState.Capturing -> HaloGlassColors.Warning
    HaloRuntimeState.Recognizing -> HaloGlassColors.Info
    HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> HaloGlassColors.AccentStrong
    HaloRuntimeState.Executing -> HaloGlassColors.Accent.copy(alpha = 0.8f)
    HaloRuntimeState.Recovering -> HaloGlassColors.Danger
}
