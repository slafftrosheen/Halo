package com.halo.app.ui.glass.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassShapes
import com.halo.app.ui.glass.material.HaloRuntimeState

/**
 * Halo State Orb - Visualizes runtime state with sophisticated animations.
 * Replaces legacy HapticFloatingMic with glass-enhanced presentation.
 * 
 * Design ref: HALO-GLASS-DESIGN.md
 */
@Composable
fun HaloStateOrb(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "state_orb")
    
    // Animations
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = getStateScale(state),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scale"
    )
    
    val color by animateColorAsState(
        targetValue = getStateColor(state),
        animationSpec = tween(500), label = "color"
    )
    
    Box(
        modifier = modifier.size(size).graphicsLayer(scaleX = scale, scaleY = scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(size * 0.5f).background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            createStateOrbContent(state, size)
        }
    }
}

@Composable
private fun createStateOrbContent(state: HaloRuntimeState, size: androidx.compose.ui.unit.Dp) {
    when (state) {
        HaloRuntimeState.Idle, HaloRuntimeState.Stopped -> {
            Icon(
                imageVector = androidx.compose.foundation.icons.Icons.Default.Search,
                contentDescription = "Idle",
                tint = HaloGlassColors.TextPrimary,
                modifier = Modifier.size(size * 0.25f)
            )
        }
        HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> {
            Icon(
                imageVector = androidx.compose.foundation.icons.Icons.Default.Mic,
                contentDescription = "Listening",
                tint = Color.White,
                modifier = Modifier.size(size * 0.3f)
            )
        }
        HaloRuntimeState.Capturing -> {
            Icon(
                imageVector = androidx.compose.foundation.icons.Icons.Default.Mic,
                contentDescription = "Capturing",
                tint = Color.White,
                modifier = Modifier.size(size * 0.35f)
            )
        }
        else -> {
            // Show progress indicator for other states
            val progress = getProgressForState(state)
            val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(2000, LinearEasing))
            )
            
            Canvas(modifier = Modifier.size(size * 0.4f)) {
                drawArc(
                    color = Color.White.copy(alpha = 0.7f),
                    startAngle = -90f,
                    sweepAngle = rotation % 360f,
                    useCenter = false,
                    style = Stroke(2.dp.toPx())
                )
            }
        }
    }
}

private fun getStateScale(state: HaloRuntimeState): Float = when (state) {
    HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> 1.0f
    HaloRuntimeState.Capturing, HaloRuntimeState.Recognizing, 
    HaloRuntimeState.Reasoning, HaloRuntimeState.Planning, 
    HaloRuntimeState.Executing, HaloRuntimeState.Speaking -> 1.1f
    else -> 1f
}

private fun getStateColor(state: HaloRuntimeState): Color = when (state) {
    HaloRuntimeState.Idle, HaloRuntimeState.Stopped -> HaloGlassColors.Background
    HaloRuntimeState.Listening, HaloRuntimeState.Wakeword, HaloRuntimeState.Speaking -> HaloGlassColors.Accent
    HaloRuntimeState.Capturing -> HaloGlassColors.Warning
    HaloRuntimeState.Recognizing -> HaloGlassColors.Info
    HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> HaloGlassColors.AccentStrong
    HaloRuntimeState.Executing, HaloRuntimeState.Recovering -> HaloGlassColors.Accent.copy(alpha = 0.8f)
}

private fun getProgressForState(state: HaloRuntimeState): Float = when (state) {
    HaloRuntimeState.Recognizing, HaloRuntimeState.Planning, 
    HaloRuntimeState.Executing, HaloRuntimeState.Speaking -> 1f
    HaloRuntimeState.Recovering -> 0.3f
    else -> 0.5f
}

/**
 * Convenience constructors for common states.
 */
object HaloStateOrb {
    @Composable
    fun Idle(
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 80.dp
    ) = HaloStateOrb(state = HaloRuntimeState.Idle, modifier = modifier, size = size)
    
    @Composable
    fun Listening(
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 80.dp
    ) = HaloStateOrb(state = HaloRuntimeState.Listening, modifier = modifier, size = size)
}