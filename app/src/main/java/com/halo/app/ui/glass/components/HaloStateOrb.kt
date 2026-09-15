package com.halo.app.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloRuntimeState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Halo's primary presence and interaction surface.
 *
 * The orb is deliberately state-driven: visuals describe the runtime and taps
 * issue a command through the caller rather than inventing UI-local state.
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
    val transition = rememberInfiniteTransition(label = "halo_orb")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "orb_press_scale"
    )
    val stateColor by animateColorAsState(
        targetValue = stateColor(state),
        animationSpec = tween(420),
        label = "orb_state_color"
    )

    val breath by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb_breath"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "orb_pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "orb_rotation"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .scale(scale)
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
                } else Modifier
            )
    ) {
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val radius = size.toPx() / 2f
        val core = radius * 0.34f
        val outer = radius * 0.78f

        // Soft atmospheric halo. This is the visual identity at rest.
        drawCircle(
            brush = Brush.radialGradient(
                0f to stateColor.copy(alpha = 0.26f),
                0.42f to stateColor.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 0.98f
            ),
            radius = radius * 0.98f
        )

        // State activity lives in the perimeter, keeping the core calm and legible.
        when (state) {
            HaloRuntimeState.Idle, HaloRuntimeState.Recovering -> {
                drawCircle(
                    color = stateColor.copy(alpha = 0.22f + 0.10f * (breath - 0.90f) / 0.14f),
                    radius = outer * breath,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawArc(
                    color = stateColor.copy(alpha = 0.65f),
                    startAngle = -70f,
                    sweepAngle = if (state == HaloRuntimeState.Recovering) 300f else 210f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx()),
                    topLeft = Offset(center.x - outer, center.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 2f, outer * 2f)
                )
            }

            HaloRuntimeState.Listening, HaloRuntimeState.Wakeword, HaloRuntimeState.Capturing -> {
                val expanding = outer * (0.88f + 0.22f * pulse)
                drawCircle(
                    color = stateColor.copy(alpha = 0.48f * (1f - pulse)),
                    radius = expanding,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawArc(
                    color = stateColor,
                    startAngle = rotation,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = if (state == HaloRuntimeState.Capturing) 3.dp.toPx() else 2.dp.toPx()),
                    topLeft = Offset(center.x - outer, center.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 2f, outer * 2f)
                )
            }

            HaloRuntimeState.Recognizing -> {
                drawCircle(color = stateColor.copy(alpha = 0.55f), radius = outer, style = Stroke(width = 2.dp.toPx()))
                for (i in 0..2) {
                    val angle = (rotation * 1.6f + i * 120f) * (Math.PI / 180f).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 3.dp.toPx(),
                        center = Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer)
                    )
                }
            }

            HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> {
                drawCircle(color = stateColor.copy(alpha = 0.45f), radius = outer, style = Stroke(width = 1.5.dp.toPx()))
                drawArc(
                    color = stateColor,
                    startAngle = rotation * 1.5f - 90f,
                    sweepAngle = if (state == HaloRuntimeState.Planning) 250f else 120f,
                    useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx()),
                    topLeft = Offset(center.x - outer, center.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 2f, outer * 2f)
                )
            }

            HaloRuntimeState.Executing -> {
                drawCircle(color = stateColor.copy(alpha = 0.50f), radius = outer, style = Stroke(width = 2.dp.toPx()))
                drawArc(
                    color = stateColor,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = Offset(center.x - outer, center.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 2f, outer * 2f)
                )
            }

            HaloRuntimeState.Speaking -> {
                for (i in 0..1) {
                    val p = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        color = stateColor.copy(alpha = 0.38f * (1f - p)),
                        radius = core + (outer - core) * p,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            HaloRuntimeState.Stopped -> {
                drawCircle(color = HaloGlassColors.Danger.copy(alpha = 0.55f), radius = outer, style = Stroke(width = 2.dp.toPx()))
            }
        }

        // Glassy core: a luminous center rather than a generic filled dot.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.95f),
                0.18f to stateColor.copy(alpha = 0.98f),
                0.70f to stateColor.copy(alpha = 0.72f),
                1f to stateColor.copy(alpha = 0.20f),
                center = Offset(center.x - core * 0.22f, center.y - core * 0.26f),
                radius = core * 1.65f
            ),
            radius = core * (if (state == HaloRuntimeState.Idle) 0.92f else 1.04f)
        )

        // Tiny specular highlight gives the orb a physical glass presence.
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = core * 0.16f,
            center = Offset(center.x - core * 0.30f, center.y - core * 0.34f)
        )
    }
}

@Preview
@Composable
fun PreviewHaloStateOrb() {
    Column(
        modifier = Modifier.background(HaloGlassColors.Background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HaloStateOrb(HaloRuntimeState.Idle)
            HaloStateOrb(HaloRuntimeState.Listening)
            HaloStateOrb(HaloRuntimeState.Reasoning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HaloStateOrb(HaloRuntimeState.Executing)
            HaloStateOrb(HaloRuntimeState.Speaking)
            HaloStateOrb(HaloRuntimeState.Recovering)
        }
    }
}

private fun stateColor(state: HaloRuntimeState): Color = when (state) {
    HaloRuntimeState.Idle, HaloRuntimeState.Stopped -> HaloGlassColors.TextMuted
    HaloRuntimeState.Listening, HaloRuntimeState.Wakeword, HaloRuntimeState.Speaking -> HaloGlassColors.Accent
    HaloRuntimeState.Capturing -> HaloGlassColors.Warning
    HaloRuntimeState.Recognizing -> HaloGlassColors.Info
    HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> HaloGlassColors.AccentStrong
    HaloRuntimeState.Executing -> HaloGlassColors.Accent.copy(alpha = 0.85f)
    HaloRuntimeState.Recovering -> HaloGlassColors.Danger
}
