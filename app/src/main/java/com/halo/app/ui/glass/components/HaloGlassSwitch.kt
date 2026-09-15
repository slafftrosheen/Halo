package com.halo.app.ui.glass.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMotion
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor = when {
        !enabled -> HaloGlassColors.Glass.copy(alpha = 0.4f)
        checked -> HaloGlassColors.Accent.copy(alpha = 0.5f)
        else -> HaloGlassColors.GlassElevated
    }

    val thumbColor = when {
        !enabled -> HaloGlassColors.TextMuted.copy(alpha = 0.5f)
        checked -> HaloGlassColors.AccentStrong
        else -> HaloGlassColors.TextSecondary
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(durationMillis = HaloGlassMotion.standard),
        label = "switch_thumb"
    )

    val content = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = { onCheckedChange(!checked) }
                )
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .background(
                        color = trackColor,
                        shape = HaloGlassTokens.pillShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = thumbOffset)
                        .size(24.dp)
                        .background(
                            color = thumbColor,
                            shape = HaloGlassTokens.circleShape
                        )
                )
            }

            if (label != null) {
                Text(
                    text = label,
                    style = HaloTypography.bodyMedium,
                    color = if (enabled) HaloGlassColors.TextPrimary else HaloGlassColors.TextMuted,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }

    content()
}
