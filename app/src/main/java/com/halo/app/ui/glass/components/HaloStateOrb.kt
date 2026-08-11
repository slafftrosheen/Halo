package com.halo.app.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloRuntimeState

/**
 * Halo State Orb - Visualizes runtime state with sophisticated animations.
 * Replaces legacy HapticFloatingMic with glass-enhanced presentation.
 */
@Composable
fun HaloStateOrb(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
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
            StateOrbContent(state, size)
        }
    }
}

@Composable
private fun StateOrbContent(state: HaloRuntimeState, size: Dp) {
    when (state) {
        HaloRuntimeState.Idle, HaloRuntimeState.Stopped -> {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Idle",
                tint = HaloGlassColors.TextPrimary,
                modifier = Modifier.size(size * 0.25f)
            )
        }
        HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Listening",
                tint = Color.White,
                modifier = Modifier.size(size * 0.3f)
            )
        }
        HaloRuntimeState.Capturing -> {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Capturing",
                tint = Color.White,
                modifier = Modifier.size(size * 0.35f)
            )
        }
        else -> {
            val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
            )
            
            Canvas(modifier = Modifier.size(size * 0.4f)) {
                drawArc(
                    color = Color.White.copy(alpha = 0.7f),
                    startAngle = -90f,
                    sweepAngle = rotation,
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

/**
 * Convenience constructors for common states.
 */
object HaloStateOrb {
    @Composable
    fun Idle(
        modifier: Modifier = Modifier,
        size: Dp = 80.dp
    ) = HaloStateOrb(state = HaloRuntimeState.Idle, modifier = modifier, size = size)
    
    @Composable
    fun Listening(
        modifier: Modifier = Modifier,
        size: Dp = 80.dp
    ) = HaloStateOrb(state = HaloRuntimeState.Listening, modifier = modifier, size = size)
}
