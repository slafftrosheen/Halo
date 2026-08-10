package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Glass metric component for displaying label-value pairs.
 * Replaces the legacy HaloMetric with glass styling.
 *
 * @param label The metric label (secondary text)
 * @param value The metric value (primary text)
 * @param modifier The modifier to apply to the metric
 * @param valueColor Optional override for the value text color
 */
@Composable
fun HaloGlassMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HaloGlassColors.TextPrimary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = HaloGlassColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Glass status row component for showing status indicators.
 */
@Composable
fun HaloGlassStatus(
    label: String,
    statusText: String,
    isHealthy: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = HaloGlassColors.TextPrimary, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isHealthy) HaloGlassColors.Accent else HaloGlassColors.Danger)
                    .then(
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = statusText, color = HaloGlassColors.TextSecondary, fontSize = 14.sp)
        }
    }
}