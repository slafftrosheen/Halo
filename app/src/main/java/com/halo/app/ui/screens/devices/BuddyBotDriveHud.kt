package com.halo.app.ui.screens.devices

import com.halo.app.language.uiText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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

/** Compact, glanceable driving HUD. Pure presentation: safety authority stays on BuddyBot. */
@Composable
fun BuddyBotDriveHud(
    telemetry: BuddyBotTelemetry,
    connectionReady: Boolean,
    direction: String,
    modifier: Modifier = Modifier
) {
    val safety = if (!connectionReady) "OFFLINE" else telemetry.safetyState?.uppercase() ?: "UNKNOWN"
    val safetyColor = when (safety) {
        "ARMED", "READY" -> HaloGlassColors.Success
        "CAUTION", "BLOCKED" -> HaloGlassColors.Warning
        "FAULT", "ESTOP" -> HaloGlassColors.Danger
        else -> HaloGlassColors.TextMuted
    }
    val range = telemetry.rangeMm.takeIf { connectionReady && telemetry.rangeValid }
    val obstacle = connectionReady && (telemetry.forwardMotionBlocked || telemetry.obstacleDetected)

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
                Text(uiText("DRIVE HUD"), style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (connectionReady) uiText(direction) else uiText("OFFLINE"),
                    style = HaloTypography.titleMedium,
                    color = if (connectionReady) HaloGlassColors.TextPrimary else HaloGlassColors.TextMuted
                )
            }
            StatusPill(safety, safetyColor)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudMetric("RANGE", range?.let { uiText("%1\$d mm", it) } ?: "—", rangeColor(range))
            HudMetric(
                "FRONT",
                when {
                    !connectionReady -> "UNKNOWN"
                    telemetry.forwardMotionBlocked -> "BLOCKED"
                    telemetry.obstacleDetected -> "OBSTACLE"
                    telemetry.rangeValid -> "CLEAR"
                    else -> "UNKNOWN"
                },
                when {
                    !connectionReady -> HaloGlassColors.TextMuted
                    obstacle -> HaloGlassColors.Warning
                    telemetry.rangeValid -> HaloGlassColors.Success
                    else -> HaloGlassColors.TextMuted
                }
            )
            HudMetric("Motors", buddyBotMotorLabel(connectionReady, telemetry.motorsArmed), if (connectionReady && telemetry.motorsArmed) HaloGlassColors.Success else HaloGlassColors.TextMuted)
        }

        FrontRangeIndicator(
            rangeMm = range,
            blocked = obstacle,
            valid = connectionReady && telemetry.rangeValid,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RowScope.HudMetric(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(HaloGlassColors.BackgroundSecondary, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(uiText(label), style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(uiText(value), style = HaloTypography.labelMedium, color = valueColor)
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
        Text(uiText(text), style = HaloTypography.labelSmall, color = color)
    }
}

@Composable
private fun FrontRangeIndicator(
    rangeMm: Int?,
    blocked: Boolean,
    valid: Boolean,
    modifier: Modifier = Modifier
) {
    val tone = when {
        blocked -> HaloGlassColors.Warning
        !valid || rangeMm == null -> HaloGlassColors.TextMuted
        rangeMm <= 150 -> HaloGlassColors.Danger
        rangeMm <= 500 -> HaloGlassColors.Warning
        else -> HaloGlassColors.Success
    }
    val progress = (rangeMm?.coerceIn(0, 2000)?.div(2000f) ?: 0f)

    val ringColor = HaloGlassColors.BorderSubtle
    Row(
        modifier
            .height(52.dp)
            .background(HaloGlassColors.BackgroundSecondary.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(Modifier.size(32.dp)) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.38f
            drawCircle(ringColor, radius, center, style = Stroke(1.dp.toPx()))
            drawArc(tone.copy(alpha = 0.9f), -90f, 360f * progress, false, style = Stroke(3.dp.toPx()))
            drawCircle(tone, 3.dp.toPx(), center)
        }
        Column(Modifier.weight(1f)) {
            Text(uiText("FRONT CLEARANCE"), style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
            Text(
                when {
                    blocked -> uiText("Forward motion blocked")
                    !valid || rangeMm == null -> uiText("Sensor unavailable")
                    else -> uiText("%1\$d mm detected", rangeMm)
                },
                style = HaloTypography.bodySmall,
                color = if (blocked) HaloGlassColors.Warning else HaloGlassColors.TextSecondary
            )
        }
    }
}

@Composable
private fun rangeColor(range: Int?): Color = when {
    range == null -> HaloGlassColors.TextMuted
    range <= 150 -> HaloGlassColors.Danger
    range <= 500 -> HaloGlassColors.Warning
    else -> HaloGlassColors.Success
}
