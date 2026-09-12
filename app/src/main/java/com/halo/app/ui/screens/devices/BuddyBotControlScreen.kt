package com.halo.app.ui.screens.devices

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.halo.app.embodiment.BuddyBotHost
import com.halo.app.embodiment.BuddyBotTelemetry
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassDialog
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.runtime.embodiment.ActionId
import com.halo.sdk.runtime.embodiment.DriveMode
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind
import com.halo.sdk.runtime.embodiment.Mood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun BuddyBotControlScreen(
    deviceId: String,
    viewModel: DevicesViewModel,
    buddyBotHost: BuddyBotHost? = viewModel.buddyBotHost,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.devices.find { it.id == deviceId }
    val isConnected = device?.isConnected == true
    val fallbackTelemetry = remember { MutableStateFlow(BuddyBotTelemetry()) }
    val telemetry by (buddyBotHost?.telemetry ?: fallbackTelemetry).collectAsState()
    val scope = rememberCoroutineScope()

    var pairingCode by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var targetUrl by remember { mutableStateOf(buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL) }
    var joystickOffset by remember { mutableStateOf(Offset.Zero) }
    var joystickActive by remember { mutableStateOf(false) }
    var joystickDirection by remember { mutableStateOf("STANDBY") }
    var lastJoystickSend by remember { mutableLongStateOf(0L) }

    fun dispatch(
        kind: IntentKind,
        mode: DriveMode = DriveMode.STOPPED,
        durationMs: Int = 0,
        action: ActionId = ActionId.NONE,
        mood: Mood = Mood.IDLE
    ) {
        viewModel.dispatchEmbodimentIntent(
            deviceId,
            EmbodimentIntent(
                intentId = UUID.randomUUID().toString(),
                targetDeviceId = deviceId,
                kind = kind,
                driveMode = mode,
                durationMs = durationMs,
                action = action,
                mood = mood
            )
        )
    }

    fun stopMotion() {
        if (isConnected) dispatch(IntentKind.STOP)
        joystickOffset = Offset.Zero
        joystickActive = false
        joystickDirection = "STOPPED"
    }

    if (showUrlDialog) {
        HaloGlassDialog(title = "BuddyBot Target", onDismissRequest = { showUrlDialog = false }) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Local WebSocket endpoint", style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("WebSocket URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HaloGlassColors.TextPrimary,
                        unfocusedTextColor = HaloGlassColors.TextPrimary,
                        focusedBorderColor = HaloGlassColors.Accent,
                        unfocusedBorderColor = HaloGlassColors.BorderStandard,
                        cursorColor = HaloGlassColors.Accent
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HaloGlassButton("Reset", { targetUrl = BuddyBotHost.DEFAULT_BUDDYBOT_URL }, Modifier.weight(1f), isPrimary = false)
                    HaloGlassButton("Save", { buddyBotHost?.setTargetUrl(targetUrl.trim()); showUrlDialog = false }, Modifier.weight(1f))
                }
            }
        }
    }

    HaloGlassLazyPage(title = "") {
        item { HaloGlassBackHeader(title = device?.name ?: "BuddyBot", onBack = onBack) }

        item {
            BuddyBotHero(device, telemetry, isConnected, ::stopMotion)
        }

        item {
            HaloGlassSection(title = "MANUAL CONTROL") {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val driveEnabled = isConnected && telemetry.motorsArmed
                    val hint = when {
                        !isConnected -> "Connect BuddyBot"
                        !telemetry.motorsArmed -> "Arm motors to drive"
                        telemetry.forwardMotionBlocked -> "Forward motion blocked by safety"
                        else -> "Drag to steer • release to stop"
                    }

                    BuddyBotJoystick(
                        enabled = driveEnabled,
                        offset = joystickOffset,
                        onMove = { raw ->
                            joystickOffset = raw
                            joystickActive = raw != Offset.Zero
                            if (!driveEnabled) return@BuddyBotJoystick

                            val magnitude = min(1f, hypot(raw.x, raw.y) / 100f)
                            if (magnitude < 0.14f) {
                                stopMotion()
                                return@BuddyBotJoystick
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastJoystickSend < 220L) return@BuddyBotJoystick
                            lastJoystickSend = now

                            val x = (raw.x / 100f).coerceIn(-1f, 1f)
                            val y = (-raw.y / 100f).coerceIn(-1f, 1f)
                            val mode = when {
                                y >= abs(x) -> DriveMode.FORWARD
                                y <= -abs(x) -> DriveMode.REVERSE
                                x < 0f -> DriveMode.ROTATE_LEFT
                                else -> DriveMode.ROTATE_RIGHT
                            }
                            joystickDirection = when (mode) {
                                DriveMode.FORWARD -> "FORWARD"
                                DriveMode.REVERSE -> "REVERSE"
                                DriveMode.ROTATE_LEFT -> "LEFT"
                                else -> "RIGHT"
                            }
                            dispatch(IntentKind.MOVE, mode, (220 + 380 * magnitude).toInt())
                        },
                        onRelease = ::stopMotion
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(joystickDirection, style = HaloTypography.labelMedium, color = if (driveEnabled) HaloGlassColors.Accent else HaloGlassColors.TextMuted)
                        Text(hint, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    }

                    HaloGlassButton(
                        text = "EMERGENCY STOP",
                        onClick = ::stopMotion,
                        enabled = isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = HaloGlassColors.Danger,
                        material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Danger.copy(alpha = 0.12f))
                    )
                    Text("Safety Supervisor remains authoritative over every movement.", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                }
            }
        }

        item {
            HaloGlassSection(title = "DRIVE PAD") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DriveButton("▲  Forward", isConnected && telemetry.motorsArmed) { dispatch(IntentKind.MOVE, DriveMode.FORWARD, 500) }
                        DriveButton("↶  Left", isConnected && telemetry.motorsArmed) { dispatch(IntentKind.MOVE, DriveMode.ROTATE_LEFT, 300) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DriveButton("■  Stop", isConnected) { stopMotion() }
                        DriveButton("Right  ↷", isConnected && telemetry.motorsArmed) { dispatch(IntentKind.MOVE, DriveMode.ROTATE_RIGHT, 300) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DriveButton("▼  Reverse", isConnected && telemetry.motorsArmed) { dispatch(IntentKind.MOVE, DriveMode.REVERSE, 500) }
                        DriveButton(if (telemetry.motorsArmed) "DISARM" else "ARM", isConnected) {
                            dispatch(if (telemetry.motorsArmed) IntentKind.DISARM else IntentKind.ARM)
                        }
                    }
                }
            }
        }

        item {
            HaloGlassSection(title = "SAFETY") {
                val safety = telemetry.safetyState ?: "UNKNOWN"
                val safetyColor = when (safety.uppercase()) {
                    "ARMED", "READY" -> HaloGlassColors.Success
                    "CAUTION", "BLOCKED" -> HaloGlassColors.Warning
                    "FAULT", "ESTOP" -> HaloGlassColors.Danger
                    else -> HaloGlassColors.TextSecondary
                }
                TelemetryLine("Supervisor", safety, safetyColor)
                HaloGlassDivider()
                TelemetryLine(
                    "Obstacle",
                    when {
                        telemetry.forwardMotionBlocked -> "Forward blocked"
                        telemetry.obstacleDetected -> "Detected"
                        telemetry.rangeValid -> "Clear"
                        else -> "No reading"
                    },
                    if (telemetry.forwardMotionBlocked || telemetry.obstacleDetected) HaloGlassColors.Warning else HaloGlassColors.TextSecondary
                )
                if (telemetry.rangeMm != null) {
                    HaloGlassDivider()
                    TelemetryLine("Range", "${telemetry.rangeMm} mm", HaloGlassColors.TextPrimary)
                }
                if (!telemetry.safetyFault.isNullOrEmpty() && telemetry.safetyFault != "none") {
                    HaloGlassDivider()
                    TelemetryLine("Fault", telemetry.safetyFault ?: "Unknown", HaloGlassColors.Danger)
                }
                HaloGlassDivider()
                TelemetryLine("Motors", if (telemetry.motorsArmed) "ARMED" else "DISARMED", if (telemetry.motorsArmed) HaloGlassColors.Success else HaloGlassColors.TextSecondary)
            }
        }

        item {
            HaloGlassSection(title = "PERSONALITY") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HaloGlassSettingRow(title = "Persona", value = telemetry.personaName ?: "BuddyBot")
                    HaloGlassDivider()
                    HaloGlassSettingRow(title = "Mood", value = (telemetry.moodName ?: "idle").replaceFirstChar { it.uppercase() })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoodButton("Happy", Mood.HAPPY, isConnected) { dispatch(IntentKind.SET_MOOD, mood = it) }
                        MoodButton("Curious", Mood.CURIOUS, isConnected) { dispatch(IntentKind.SET_MOOD, mood = it) }
                        MoodButton("Sleepy", Mood.SLEEPY, isConnected) { dispatch(IntentKind.SET_MOOD, mood = it) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DriveButton("Celebrate", isConnected) { dispatch(IntentKind.ACTION, action = ActionId.CELEBRATE) }
                        DriveButton("Look left", isConnected) { dispatch(IntentKind.ACTION, action = ActionId.LOOK_LEFT) }
                        DriveButton("Next persona", isConnected) { dispatch(IntentKind.NEXT_PERSONA) }
                    }
                }
            }
        }

        item {
            HaloGlassSection(title = "CONNECTION") {
                val paired = buddyBotHost?.session?.hasToken == true || telemetry.controllerPresent
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusLine("Status", device?.connectionStateText ?: "Unknown", isConnected)
                    HaloGlassDivider()
                    StatusLine("Controller lease", if (paired) "PAIRED & ACTIVE" else "UNPAIRED", paired)
                    HaloGlassDivider()
                    HaloGlassSettingRow(
                        title = "Target URL",
                        value = buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL,
                        onClick = { showUrlDialog = true }
                    )
                    HaloGlassDivider()
                    HaloGlassSettingRow(
                        title = "Auto-connect",
                        value = if (device?.autoConnect == true) "ON" else "OFF",
                        onClick = { viewModel.toggleAutoConnect(deviceId, !(device?.autoConnect ?: false)) }
                    )
                    HaloGlassDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { if (it.length <= 9) pairingCode = it },
                            placeholder = { Text("1234 5678", color = HaloGlassColors.TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = HaloGlassColors.TextPrimary,
                                unfocusedTextColor = HaloGlassColors.TextPrimary,
                                focusedBorderColor = HaloGlassColors.Accent,
                                unfocusedBorderColor = HaloGlassColors.BorderStandard,
                                cursorColor = HaloGlassColors.Accent
                            )
                        )
                        HaloGlassButton(
                            text = "Pair",
                            onClick = {
                                val code = pairingCode.replace(" ", "")
                                if (code.isNotBlank()) scope.launch { buddyBotHost?.pair(code) }
                            },
                            enabled = isConnected && pairingCode.isNotBlank()
                        )
                    }
                    HaloGlassButton(
                        text = "Forget device",
                        onClick = { viewModel.forgetDevice(deviceId) },
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = HaloGlassColors.Danger,
                        material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Danger.copy(alpha = 0.10f))
                    )
                }
            }
        }

        item {
            HaloGlassSection(title = "DEVICE") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    HaloGlassSettingRow(title = "Battery", value = telemetry.batteryPercent?.let { "$it%" } ?: device?.battery ?: "Unknown")
                    HaloGlassDivider()
                    HaloGlassSettingRow(title = "Firmware", value = device?.firmware ?: "v0.3.0")
                    HaloGlassDivider()
                    HaloGlassSettingRow(title = "Uptime", value = if (telemetry.uptimeMs > 0) "${telemetry.uptimeMs / 1000}s" else "Unknown")
                    HaloGlassDivider()
                    HaloGlassSettingRow(title = "Wi-Fi clients", value = telemetry.wifiClientCount.toString())
                    HaloGlassDivider()
                    HaloGlassSettingRow(title = "Health", value = device?.healthStateText ?: "Unknown")
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun BuddyBotHero(device: GenericDeviceUiState?, telemetry: BuddyBotTelemetry, isConnected: Boolean, onStop: () -> Unit) {
    val status = telemetry.safetyState ?: if (isConnected) "READY" else "OFFLINE"
    val tone = when (status.uppercase()) {
        "ARMED", "READY" -> HaloGlassColors.Success
        "CAUTION", "BLOCKED" -> HaloGlassColors.Warning
        "FAULT", "ESTOP" -> HaloGlassColors.Danger
        else -> HaloGlassColors.TextMuted
    }
    HaloGlassSection(title = "BUDDYBOT") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)).border(1.dp, tone.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("B", style = HaloTypography.titleLarge, color = tone)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(device?.name ?: "BuddyBot", style = HaloTypography.titleLarge, color = HaloGlassColors.TextPrimary)
                    Text(if (isConnected) "${device?.connectionStateText ?: "Connected"} • ${device?.battery ?: "Battery unknown"}" else "Disconnected", style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
                }
                Text(status.uppercase(), style = HaloTypography.labelMedium, color = tone)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("RANGE", telemetry.rangeMm?.let { "${it}mm" } ?: "—")
                Metric("MOTORS", if (telemetry.motorsArmed) "ARMED" else "OFF")
                Metric("MOOD", (telemetry.moodName ?: "idle").uppercase())
            }
            if (isConnected) {
                HaloGlassButton(
                    text = "STOP ALL MOTION",
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = HaloGlassColors.Danger,
                    material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Danger.copy(alpha = 0.10f))
                )
            }
        }
    }
}

@Composable
private fun BuddyBotJoystick(enabled: Boolean, offset: Offset, onMove: (Offset) -> Unit, onRelease: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth.value, 250f).dp
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(diameter).pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onMove(Offset.Zero) },
                        onDragCancel = onRelease,
                        onDragEnd = onRelease,
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val requested = change.position - center
                            val radius = size.minDimension * 0.34f
                            val length = hypot(requested.x, requested.y)
                            val scale = if (length > radius) radius / length else 1f
                            onMove(Offset(requested.x * scale, requested.y * scale))
                        }
                    )
                }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outer = size.minDimension * 0.46f
                val dead = outer * 0.26f
                drawCircle(HaloGlassColors.GlassElevated, outer, center)
                drawCircle(HaloGlassColors.BorderStandard, outer, center, style = Stroke(2.dp.toPx()))
                drawCircle(HaloGlassColors.TextMuted.copy(alpha = 0.18f), dead, center, style = Stroke(1.dp.toPx()))
                drawLine(HaloGlassColors.BorderSubtle, Offset(center.x, center.y - outer), Offset(center.x, center.y + outer), 1.dp.toPx())
                drawLine(HaloGlassColors.BorderSubtle, Offset(center.x - outer, center.y), Offset(center.x + outer, center.y), 1.dp.toPx())
                val knobScale = outer / (size.minDimension * 0.34f)
                val knob = Offset(center.x + offset.x * knobScale, center.y + offset.y * knobScale)
                drawCircle(if (enabled) HaloGlassColors.Accent.copy(alpha = 0.9f) else HaloGlassColors.TextMuted.copy(alpha = 0.45f), size.minDimension * 0.14f, knob)
                drawCircle(HaloGlassColors.TextPrimary.copy(alpha = if (enabled) 0.3f else 0.12f), size.minDimension * 0.14f, knob, style = Stroke(2.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▲", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                Spacer(Modifier.height(72.dp))
                Row {
                    Text("◀", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                    Spacer(Modifier.width(116.dp))
                    Text("▶", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                }
                Spacer(Modifier.height(72.dp))
                Text("▼", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun DriveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    HaloGlassButton(text = text, onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f))
}

@Composable
private fun MoodButton(label: String, mood: Mood, enabled: Boolean, onClick: (Mood) -> Unit) {
    HaloGlassButton(text = label, onClick = { onClick(mood) }, enabled = enabled, modifier = Modifier.weight(1f), isPrimary = false)
}

@Composable
private fun TelemetryLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
        Text(value, style = HaloTypography.bodyMedium, color = color)
    }
}

@Composable
private fun StatusLine(label: String, value: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
        Text(value, style = HaloTypography.labelMedium, color = if (positive) HaloGlassColors.Success else HaloGlassColors.TextMuted)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Box(Modifier.weight(1f).clip(HaloGlassTokens.smallShape).background(HaloGlassColors.BackgroundSecondary).border(1.dp, HaloGlassColors.BorderSubtle, HaloGlassTokens.smallShape).padding(10.dp)) {
        Column {
            Text(label, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
            Spacer(Modifier.height(3.dp))
            Text(value, style = HaloTypography.labelMedium, color = HaloGlassColors.TextPrimary)
        }
    }
}
