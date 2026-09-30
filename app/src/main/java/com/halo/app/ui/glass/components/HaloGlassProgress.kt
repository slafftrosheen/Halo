package com.halo.app.ui.glass.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = HaloGlassColors.GlassElevated,
    progressColor: Color = HaloGlassColors.Accent,
    height: Dp = 4.dp
) {
    val clamped = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barHeight = size.height
        val cornerRadius = barHeight / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(size.width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        if (clamped > 0f) {
            drawRoundRect(
                color = progressColor,
                topLeft = Offset.Zero,
                size = Size(size.width * clamped, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
        }
    }
}

@Composable
fun HaloGlassIndeterminateProgress(
    modifier: Modifier = Modifier,
    trackColor: Color = HaloGlassColors.GlassElevated,
    progressColor: Color = HaloGlassColors.Accent,
    height: Dp = 4.dp
) {
    val reducedMotion by com.halo.app.ui.theme.rememberReducedMotion()
    if (reducedMotion) {
        Canvas(modifier.fillMaxWidth().height(height)) {
            drawRoundRect(trackColor, size = this.size)
            drawRoundRect(progressColor, size = Size(this.size.width * 0.3f, this.size.height))
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "progress")
    val offset = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_offset"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barHeight = size.height
        val cornerRadius = barHeight / 2f
        val indicatorWidth = size.width * 0.3f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(size.width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        val startX = (size.width + indicatorWidth) * offset.value - indicatorWidth
        drawRoundRect(
            color = progressColor,
            topLeft = Offset(startX.coerceIn(0f, size.width - 1f), 0f),
            size = Size(indicatorWidth.coerceAtMost(size.width - startX.coerceAtLeast(0f)), barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
    }
}

@Composable
fun HaloGlassCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = HaloGlassColors.GlassElevated,
    progressColor: Color = HaloGlassColors.Accent
) {
    val clamped = progress.coerceIn(0f, 1f)

    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        if (clamped > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun HaloGlassIndeterminateCircularProgress(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = HaloGlassColors.GlassElevated,
    progressColor: Color = HaloGlassColors.Accent
) {
    val reducedMotion by com.halo.app.ui.theme.rememberReducedMotion()
    if (reducedMotion) {
        Canvas(modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(progressColor, -90f, 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "circular_progress")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        drawArc(
            color = progressColor,
            startAngle = rotation.value - 90f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
