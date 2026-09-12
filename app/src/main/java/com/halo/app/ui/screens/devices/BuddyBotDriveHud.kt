package com.halo.app.ui.screens.devices

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.halo.app.embodiment.BuddyBotTelemetry
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography
import kotlin.math.max
import kotlin.math.min

/** Compact, glanceable driving HUD. Pure presentation: safety authority stays on BuddyBot. */
@Composable
fun BuddyBotDriveHud(
    telemetry: BuddyBotTelemetry,
    connectionReady: Boolean,
    direction: String,
    modifier: Modifier = Modifier
) {
    val safety = telemetry.safetyState?.uppercase() ?: "UNKNOWN"
    val safetyColor = when (safety) {
        "ARMED", "READY" -> HaloGlassColors.Success
        "CAUTION", "BLOCKED" -> HaloGlassColors.Warning
        "FAULT", "ESTOP" -> HaloGlassColors.Danger
        else -> HaloGlassColors.TextMuted
    }
    val range = telemetry.rangeMm
    val rangeLabel = when {
        range == null -> "—"
        range <= 150 -> "${range} mm"
        range <= 500 -> "${range} mm"
        else -> "${range} mm"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, HaloGlassColors.BorderSubtle, HaloGlassTokens.smallShape)
            .background(HaloGlassColors.BackgroundSecondary, HaloGlassTokens.smallShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("DRIVE HUD", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (connectionReady) direction else "OFFLINE",
                    style = HaloTypography.titleMedium,
                    color = if (connectionReady) HaloGlassColors.TextPrimary else HaloGlassColors.TextMuted
                )
            }
            StatusPill(safety, safetyColor)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudMetric("RANGE", rangeLabel, rangeColor(range))
            HudMetric("OBSTACLE", obstacleLabel(telemetry), obstacleColor(telemetry))
            HudMetric("MOTORS", if (telemetry.motorsArmed) "ARMED" else "OFF", if (telemetry.motorsArmed) HaloGlassColors.Success else HaloGlassColors.TextMuted)
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(HaloGlassColors.BackgroundTertiary, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = HaloTypography.labelMedium, color = valueColor)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(Modifier.size(6.dp)) { drawCircle(color) }
        Text(text, style = HaloTypography.labelSmall, color = color)
    }
}

private fun rangeColor(range: Int?): Color = when {
    range == null -> HaloGlassColors.TextMuted
    range <= 150 -> HaloGlassColors.Danger
    range <= 500 -> HaloGlassColors.Warning
    else -> HaloGlassColors.Success
}

private fun obstacleLabel(telemetry: BuddyBotTelemetry): String = when {
    telemetry.forwardMotionBlocked -> "BLOCKED"
    telemetry.obstacleDetected -> "DETECTED"
    telemetry.rangeValid -> "CLEAR"
    else -> "UNKNOWN"
}

private fun obstacleColor(telemetry: BuddyBotTelemetry): Color = when {
    telemetry.forwardMotionBlocked || telemetry.obstacleDetected -> HaloGlassColors.Warning
    telemetry.rangeValid -> HaloGlassColors.Success
    else -> HaloGlassColors.TextMuted
}
