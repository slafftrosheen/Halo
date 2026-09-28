package com.halo.app.ui.presence

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyphRenderer

enum class HaloSignalFaceSize {
    HERO,
    STANDARD,
    COMPACT,
    MICRO,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HaloSignalFace(
    presentation: HaloSignalPresentation,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    contentDescription: String = "Halo presence",
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val palette = HaloSignalPalette.resolve(presentation.state, isDark)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "signal_face_press"
    )

    val transition = rememberInfiniteTransition(label = "halo_signal_face_motion")
    val pulseAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )
    val orbitAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo_orbit",
    )

    val sizeProfile = when {
        size >= 140.dp -> HaloSignalFaceSize.HERO
        size >= 72.dp -> HaloSignalFaceSize.STANDARD
        size >= 32.dp -> HaloSignalFaceSize.COMPACT
        else -> HaloSignalFaceSize.MICRO
    }

    val activeActivityLevel = if (presentation.reducedMotion) 0f else presentation.activityLevel.coerceIn(0f, 1f)
    val motionOffset = if (presentation.reducedMotion) 0f else pulseAnimation

    val semanticStateDescription = buildString {
        append("Halo, ${presentation.label}")
        if (presentation.detail != null) append(", ${presentation.detail}")
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = semanticStateDescription
            }
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongClick?.invoke() }
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasSize = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            if (sizeProfile == HaloSignalFaceSize.MICRO) {
                val ringRadius = canvasSize * 0.44f
                val ringStroke = canvasSize * 0.12f
                drawCircle(
                    color = palette.halo,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = ringStroke)
                )
                drawCircle(
                    color = palette.display,
                    radius = ringRadius - ringStroke / 2f,
                    center = center
                )
                return@Canvas
            }

            val outerRadius = canvasSize * 0.46f
            val ringWidth = when (sizeProfile) {
                HaloSignalFaceSize.HERO -> canvasSize * 0.08f
                HaloSignalFaceSize.STANDARD -> canvasSize * 0.09f
                else -> canvasSize * 0.10f
            }

            val expansion = if (presentation.state == HaloSignalState.LISTENING || presentation.state == HaloSignalState.SPEAKING) {
                activeActivityLevel * (canvasSize * 0.04f)
            } else if (presentation.state == HaloSignalState.THINKING) {
                motionOffset * (canvasSize * 0.02f)
            } else 0f

            val haloRadius = outerRadius + expansion
            val ambientRadius = haloRadius + ringWidth * (1.55f + motionOffset * 0.18f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.halo.copy(alpha = 0.16f),
                        palette.haloSecondary.copy(alpha = 0.07f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = ambientRadius,
                ),
                radius = ambientRadius,
                center = center,
            )

            // A quiet outer rail gives the active color a physical boundary and
            // keeps the presence legible without relying on a large neon glow.
            drawCircle(
                color = palette.halo.copy(alpha = 0.18f),
                radius = haloRadius + ringWidth * 0.72f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            val haloGlowBrush = Brush.radialGradient(
                colors = listOf(
                    palette.halo.copy(alpha = 0.54f),
                    palette.haloSecondary.copy(alpha = 0.28f),
                    palette.halo.copy(alpha = 0f)
                ),
                center = center,
                radius = haloRadius + ringWidth
            )

            drawCircle(
                brush = haloGlowBrush,
                radius = haloRadius + ringWidth * 0.5f,
                center = center
            )

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(palette.halo, palette.haloSecondary, palette.halo),
                    center = center
                ),
                radius = haloRadius,
                center = center,
                style = Stroke(width = ringWidth)
            )

            val signalPixel = when (sizeProfile) {
                HaloSignalFaceSize.HERO -> 5.dp.toPx()
                HaloSignalFaceSize.STANDARD -> 4.dp.toPx()
                else -> 3.dp.toPx()
            }
            val signalOffset = haloRadius + ringWidth * 0.72f
            listOf(
                Offset(center.x, center.y - signalOffset),
                Offset(center.x + signalOffset, center.y),
                Offset(center.x, center.y + signalOffset),
                Offset(center.x - signalOffset, center.y),
            ).forEachIndexed { index, point ->
                drawRoundRect(
                    color = if (index == 0) palette.glyph.copy(alpha = 0.90f) else palette.halo.copy(alpha = 0.58f),
                    topLeft = point - Offset(signalPixel / 2f, signalPixel / 2f),
                    size = Size(signalPixel, signalPixel),
                )
            }

            val motionAlpha = if (presentation.reducedMotion) 0f else 1f
            when (presentation.state) {
                HaloSignalState.THINKING -> rotate(orbitAnimation, center) {
                    drawArc(
                        color = palette.glyph.copy(alpha = 0.72f * motionAlpha),
                        startAngle = -34f,
                        sweepAngle = 68f,
                        useCenter = false,
                        topLeft = Offset(center.x - haloRadius, center.y - haloRadius),
                        size = Size(haloRadius * 2f, haloRadius * 2f),
                        style = Stroke(width = ringWidth * 0.34f, cap = StrokeCap.Round),
                    )
                }
                HaloSignalState.CAPTURING, HaloSignalState.ACTING -> rotate(orbitAnimation * 1.25f, center) {
                    repeat(3) { index ->
                        drawArc(
                            color = palette.glyph.copy(alpha = (0.62f - index * 0.12f) * motionAlpha),
                            startAngle = index * 120f,
                            sweepAngle = 34f,
                            useCenter = false,
                            topLeft = Offset(center.x - haloRadius, center.y - haloRadius),
                            size = Size(haloRadius * 2f, haloRadius * 2f),
                            style = Stroke(width = ringWidth * 0.28f, cap = StrokeCap.Round),
                        )
                    }
                }
                HaloSignalState.LISTENING, HaloSignalState.SPEAKING -> {
                    val responseRadius = haloRadius + ringWidth * (0.72f + activeActivityLevel * 0.35f)
                    drawCircle(
                        color = palette.haloSecondary.copy(alpha = (0.12f + activeActivityLevel * 0.18f) * motionAlpha),
                        radius = responseRadius,
                        center = center,
                        style = Stroke(width = ringWidth * 0.22f),
                    )
                }
                HaloSignalState.WAITING, HaloSignalState.WARNING, HaloSignalState.ERROR -> {
                    drawCircle(
                        color = palette.haloSecondary.copy(alpha = (0.10f + pulseAnimation * 0.12f) * motionAlpha),
                        radius = haloRadius + ringWidth * (0.55f + pulseAnimation * 0.22f),
                        center = center,
                        style = Stroke(width = ringWidth * 0.18f),
                    )
                }
                else -> Unit
            }

            if (presentation.progress != null) {
                val progressSweep = presentation.progress.coerceIn(0f, 1f) * 360f
                drawArc(
                    color = Color.White.copy(alpha = 0.90f),
                    startAngle = -90f,
                    sweepAngle = progressSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - haloRadius, center.y - haloRadius),
                    size = Size(haloRadius * 2f, haloRadius * 2f),
                    style = Stroke(width = ringWidth * 1.2f, cap = StrokeCap.Round)
                )
            }

            val displayRadius = haloRadius - ringWidth * 0.8f
            drawCircle(
                color = Color.Black.copy(alpha = 0.20f),
                radius = displayRadius + 2.dp.toPx(),
                center = center + Offset(0f, 2.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.displayHighlight, palette.display),
                    center = center - Offset(displayRadius * 0.22f, displayRadius * 0.28f),
                    radius = displayRadius * 1.45f,
                ),
                radius = displayRadius,
                center = center
            )

            if (sizeProfile == HaloSignalFaceSize.HERO || sizeProfile == HaloSignalFaceSize.STANDARD) {
                val scanHalfWidth = displayRadius * 0.72f
                repeat(3) { index ->
                    val y = center.y + (index - 1) * displayRadius * 0.34f
                    drawLine(
                        color = palette.glyph.copy(alpha = 0.028f),
                        start = Offset(center.x - scanHalfWidth, y),
                        end = Offset(center.x + scanHalfWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            drawCircle(
                color = palette.halo.copy(alpha = 0.32f),
                radius = displayRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        val glyphSize = when (sizeProfile) {
            HaloSignalFaceSize.HERO -> size * 0.44f
            HaloSignalFaceSize.STANDARD -> size * 0.46f
            HaloSignalFaceSize.COMPACT -> size * 0.50f
            HaloSignalFaceSize.MICRO -> size * 0.58f
        }

        if (sizeProfile != HaloSignalFaceSize.MICRO) {
            Crossfade(
                targetState = presentation.glyph,
                animationSpec = tween(durationMillis = if (presentation.reducedMotion) 0 else 220),
                label = "halo_glyph_transition",
            ) { glyph ->
                HaloGlyphRenderer(
                    glyph = glyph,
                    modifier = Modifier.size(glyphSize),
                    tint = palette.glyph,
                )
            }
        }
    }
}
