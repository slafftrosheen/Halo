package com.halo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.*

enum class HaloRuntimeState {
    Idle, Listening, WakeWord, Capturing, Processing, Speaking
}

@Composable
fun HaloFloatingMic(
    state: HaloRuntimeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_transition")

    // Pulse scale for processing or capturing
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == HaloRuntimeState.Processing || state == HaloRuntimeState.Capturing) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    // Dynamic color based on state
    val containerColor by animateColorAsState(
        targetValue = when (state) {
            HaloRuntimeState.Idle -> HaloSurface
            HaloRuntimeState.Listening -> HaloPrimary
            HaloRuntimeState.WakeWord -> HaloPrimary
            HaloRuntimeState.Capturing -> HaloWarning
            HaloRuntimeState.Processing -> HaloInformation
            HaloRuntimeState.Speaking -> HaloPrimary
        },
        animationSpec = tween(500),
        label = "mic_color"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .background(containerColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = "Trigger Halo",
            tint = HaloTextPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}
