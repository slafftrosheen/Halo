package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Metric visualization for Glass UI.
 */
@Composable
fun HaloGlassMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = HaloGlassColors.Accent
) {
    Column(
        modifier = modifier
            .padding(HaloGlassTokens.sm),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            color = HaloGlassColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Status indicator for Glass UI.
 */
@Composable
fun HaloGlassStatus(
    label: String,
    status: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(HaloGlassTokens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = HaloGlassColors.TextPrimary,
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (active) HaloGlassColors.Success else HaloGlassColors.TextMuted,
                        shape = HaloGlassTokens.circleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status,
                color = if (active) HaloGlassColors.TextPrimary else HaloGlassColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
