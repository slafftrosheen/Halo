package com.halo.app.ui.screens.devices

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.detectDragGestures
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
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
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

    var pairingCodeInput by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var targetUrlInput by remember { mutableStateOf(buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL) }
    var joystickOffset by remember { mutableStateOf(Offset.Zero) }
    var joystickActive by remember { mutableStateOf(false) }
    var lastJoystickCommand by remember { mutableStateOf("STANDBY") }
    var lastJoystickDispatchMs by remember { mutableLongStateOf(0L) }

    fun dispatchIntent(
        kind: com.halo.sdk.runtime.embodiment.IntentKind,
        driveMode: com.halo.sdk.runtime.embodiment.DriveMode = com.halo.sdk.runtime.embodiment.DriveMode.STOPPED,
        durationMs: Int = 0,
        action: com.halo.sdk.runtime.embodiment.ActionId = com.halo.sdk.runtime.embodiment.ActionId.NONE,
        mood: com.halo.sdk.runtime.embodiment.Mood = com.halo.sdk.runtime.embodiment.Mood.IDLE,
        vectorX: Float = 0f,
        vectorY: Float = 0f
    ) {
        viewModel.dispatchEmbodimentIntent(
            deviceId,
            com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                intentId = UUID.randomUUID().toString(),
                targetDeviceId = deviceId,
                kind = kind,
                driveMode = driveMode,
                vectorX = vectorX,
                vectorY = vectorY,
                durationMs = durationMs,
                action = action,
                mood = mood
            )
        )
    }

    fun stopRobot() {
        if (isConnected) dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.STOP)
        joystickOffset = Offset.Zero
        joystickActive = false
        lastJoystickCommand = "STOPPED"
    }

    if (showUrlDialog) {
        HaloGlassDialog(title = "BuddyBot Target WebSocket URL", onDismissRequest = { showUrlDialog = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Configure the local WebSocket endpoint used by Halo to reach BuddyBot.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextSecondary
                )
                OutlinedTextField(
                    value = targetUrlInput,
                    onValueChange = { targetUrlInput = it },
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
                    HaloGlassButton(
                        text = "Reset",
                        onClick = { targetUrlInput = BuddyBotHost.DEFAULT_BUDDYBOT_URL },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                    HaloGlassButton(
                        text = "Save",
                        onClick = {
                            buddyBotHost?.setTargetUrl(targetUrlInput.trim())
                            showUrlDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    HaloGlassLazyPage(title = "") {
        item { HaloGlassBackHeader(title = device?.name ?: "BuddyBot", onBack = onBack) }

        item {
            BuddyBotHero(
                device = device,
                telemetry = telemetry,
                isConnected = isConnected,
                onStop = ::stopRobot
            )
        }

        item {
            HaloGlassSection(title = "DRIVE CONTROL") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val driveEnabled = isConnected && telemetry.motorsArmed
                    val safetyHint = when {
                        !isConnected -> "Connect BuddyBot to enable manual control"
                        !telemetry.motorsArmed -> "Arm motors to enable manual control"
                        telemetry.forwardMotionBlocked -> "Forward motion is blocked by the safety supervisor"
                        else -> "Touch and drag the control surface"
                    }

                    BuddyBotJoystick(
                        enabled = driveEnabled,
                        offset = joystickOffset,
                        onOffsetChanged = { offset ->
                            joystickOffset = offset
                            joystickActive = offset != Offset.Zero

                            if (!driveEnabled) return@BuddyBotJoystick
                            val magnitude = min(1f, hypot(offset.x, offset.y) / 100f)
                            if (magnitude < 0.12f) {
                                stopRobot()
                                return@BuddyBotJoystick
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastJoystickDispatchMs < 180L) return@BuddyBotJoystick
                            lastJoystickDispatchMs = now

                            val normalizedX = (offset.x / 100f).coerceIn(-1f, 1f)
                            val normalizedY = (-offset.y / 100f).coerceIn(-1f, 1f)
                            val forward = normalizedY >= kotlin.math.abs(normalizedX)
                            val reverse = normalizedY <= -kotlin.math.abs(normalizedX)
                            val left = normalizedX < 0f
                            val mode = when {
                                forward -> com.halo.sdk.runtime.embodiment.DriveMode.FORWARD
                                reverse -> com.halo.sdk.runtime.embodiment.DriveMode.REVERSE
                                left -> com.halo.sdk.runtime.embodiment.DriveMode.ROTATE_LEFT
                                else -> com.halo.sdk.runtime.embodiment.DriveMode.ROTATE_RIGHT
                            }
                            val label = when (mode) {
                                com.halo.sdk.runtime.embodiment.DriveMode.FORWARD -> "FORWARD"
                                com.halo.sdk.runtime.embodiment.DriveMode.REVERSE -> "REVERSE"
                                com.halo.sdk.runtime.embodiment.DriveMode.ROTATE_LEFT -> "LEFT"
                                else -> "RIGHT"
                            }
                            lastJoystickCommand = label
                            dispatchIntent(
                                kind = com.halo.sdk.runtime.embodiment.IntentKind.MOVE,
                                driveMode = mode,
                                durationMs = (180 + 360 * magnitude).toInt(),
                                vectorX = normalizedX,
                                vectorY = normalizedY
                            )
                        },
                        onRelease = ::stopRobot
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (joystickActive) lastJoystickCommand else "MANUAL",
                            style = HaloTypography.labelMedium,
                            color = if (driveEnabled) HaloGlassColors.Accent else HaloGlassColors.TextMuted
                        )
                        Text(safetyHint, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    }

                    HaloGlassButton(
                        text = "EMERGENCY STOP",
                        onClick = ::stopRobot,
                        enabled = isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = HaloGlassColors.Danger,
                        material = HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Danger.copy(alpha = 0.12f)
                        )
                    )

                    Text(
                        "Release the joystick to command STOP. Safety Supervisor remains authoritative over every movement.",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            }
        }

        item {
            HaloGlassSection(title = "QUICK DRIVE") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickDriveButton("▲", "Forward", isConnected && telemetry.motorsArmed) {
                            dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.MOVE, com.halo.sdk.runtime.embodiment.DriveMode.FORWARD, 500)
                        }
                        QuickDriveButton("↻", "Left", isConnected && telemetry.motorsArmed) {
                            dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.MOVE, com.halo.sdk.runtime.embodiment.DriveMode.ROTATE_LEFT, 300)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickDriveButton("■", "Stop", isConnected) { stopRobot() }
                        QuickDriveButton("↺", "Right", isConnected && telemetry.motorsArmed) {
                            dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.MOVE, com.halo.sdk.runtime.embodiment.DriveMode.ROTATE_RIGHT, 300)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickDriveButton("▼", "Reverse", isConnected && telemetry.motorsArmed) {
                            dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.MOVE, com.halo.sdk.runtime.embodiment.DriveMode.REVERSE, 500)
                        }
                        QuickDriveButton("ARM", if (telemetry.motorsArmed) "Disarm" else "Arm", isConnected) {
                            dispatchIntent(
                                if (telemetry.motorsArmed) com.halo.sdk.runtime.embodiment.IntentKind.DISARM
                                else com.halo.sdk.runtime.embodiment.IntentKind.ARM
                            )
                        }
                    }
                }
            }
        }

        item {
            HaloGlassSection(title = "SAFETY & TELEMETRY") {
                val safetyText = telemetry.safetyState ?: "UNKNOWN"
                val safetyColor = when (safetyText.uppercase()) {
                    "ARMED" -> HaloGlassColors.Success
                    "CAUTION" -> HaloGlassColors.Warning
                    "ESTOP", "FAULT", "BLOCKED" -> HaloGlassColors.Danger
                    else -> HaloGlassColors.TextSecondary
                }
                TelemetryLine("Safety Supervisor", safetyText, safetyColor)
                HaloGlassDivider()
                TelemetryLine(
                    "Obstacle",
                    when {
                        telemetry.forwardMotionBlocked -> "Forward blocked"
                        telemetry.obstacleDetected -> "Detected"
                        telemetry.rangeValid -> "Clear"
                        else -> "No reading"
                    },
                    when {
                        telemetry.forwardMotionBlocked || telemetry.obstacleDetected -> HaloGlassColors.Warning
                        telemetry.rangeValid -> HaloGlassColors.Success
                        else -> HaloGlassColors.TextSecondary
                    }
                )
                if (telemetry.rangeMm != null) {
                    HaloGlassDivider()
                    TelemetryLine("Range", "${telemetry.rangeMm} mm", HaloGlassColors.TextPrimary)
                }
                if (!telemetry.safetyFault.isNullOrEmpty() && telemetry.safetyFault != "none") {
                    HaloGlassDivider()
                    TelemetryLine("Safety fault", telemetry.safetyFault ?: "Unknown", HaloGlassColors.Danger)
                }
                HaloGlassDivider()
                TelemetryLine("Motors", if (telemetry.motorsArmed) "ARMED" else "DISARMED", if (telemetry.motorsArmed) HaloGlassColors.Success else HaloGlassColors.TextSecondary)
                HaloGlassDivider()
                TelemetryLine("Battery", telemetry.batteryPercent?.let { "$it%" } ?: device?.battery ?: "Unknown", HaloGlassColors.TextPrimary)
            }
        }

        item {
            HaloGlassSection(title = "PERSONALITY") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HaloGlassSettingRow(
                        title = "Persona",
                        value = telemetry.personaName ?: "BuddyBot"
                    )
                    HaloGlassDivider()
                    HaloGlassSettingRow(
                        title = "Mood",
                        value = (telemetry.moodName ?: "idle").replaceFirstChar { it.uppercase() }
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoodButton("Happy", com.halo.sdk.runtime.embodiment.Mood.HAPPY, isConnected) { dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.SET_MOOD, mood = it) }
                        MoodButton("Curious", com.halo.sdk.runtime.embodiment.Mood.CURIOUS, isConnected) { dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.SET_MOOD, mood = it) }
                        MoodButton("Sleepy", com.halo.sdk.runtime.embodiment.Mood.SLEEPY, isConnected) { dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.SET_MOOD, mood = it) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HaloGlassButton("Celebrate", { dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.ACTION, action = com.halo.sdk.runtime.embodiment.ActionId.CELEBRATE) }, isConnected, modifier = Modifier.weight(1f))
                        HaloGlassButton("Look left", { dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.ACTION, action = com.halo.sdk.runtime.embodiment.ActionId.LOOK_LEFT) }, isConnected, modifier = Modifier.weight(1f))
                        HaloGlassButton("Next persona", { dispatchIntent(com.halo.sdk.runtime.embodiment.IntentKind.NEXT_PERSONA) }, isConnected, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            HaloGlassSection(title = "CONNECTION & SECURITY") {
                val isPaired = buddyBotHost?.session?.hasToken == true || telemetry.controllerPresent
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusLine("Connection", device?.connectionStateText ?: "Unknown", isConnected)
                    HaloGlassDivider()
                    StatusLine("Controller lease", if (isPaired) "PAIRED & ACTIVE" else "UNPAIRED", isPaired)
                    HaloGlassDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { showUrlDialog = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Target URL", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                            Text(buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                        }
                        Text("Edit", style = HaloTypography.labelMedium, color = HaloGlassColors.Accent)
                    }
                    HaloGlassDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.toggleAutoConnect(deviceId, !(device?.autoConnect ?: false)) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-connect", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                        Text(if (device?.autoConnect == true) "ON" else "OFF", style = HaloTypography.labelMedium, color = if (device?.autoConnect == true) HaloGlassColors.Accent else HaloGlassColors.TextMuted)
                    }
                    HaloGlassDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { if (it.length <= 9) pairingCodeInput = it },
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
                                val cleaned = pairingCodeInput.replace(" ", "")
                                if (cleaned.isNotEmpty()) scope.launch { buddyBotHost?.pair(cleaned) }
                            },
                            enabled = isConnected && pairingCodeInput.isNotBlank()
                        )
                    }
                    Text("Pairing authorizes motor control; the device-side safety supervisor still has final authority.", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    HaloGlassDivider()
                    HaloGlassButton(
                        text = "Forget device",
                        onClick = { viewModel.forgetDevice(deviceId) },
                        enabled = true,
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
                    HaloGlassSettingRow("Firmware", device?.firmware ?: "v0.3.0")
                    HaloGlassDivider()
                    HaloGlassSettingRow("Uptime", if (telemetry.uptimeMs > 0L) "${telemetry.uptimeMs / 1000}s" else "Unknown")
                    HaloGlassDivider()
                    HaloGlassSettingRow("Wi-Fi clients", telemetry.wifiClientCount.toString())
                    HaloGlassDivider()
                    HaloGlassSettingRow("Last seen", device?.lastSeen ?: "Never")
                    HaloGlassDivider()
                    HaloGlassSettingRow("Health", device?.healthStateText ?: "Unknown")
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun BuddyBotHero(
    device: GenericDeviceUiState?,
    telemetry: BuddyBotTelemetry,
    isConnected: Boolean,
    onStop: () -> Unit
) {
    val status = telemetry.safetyState ?: if (isConnected) "READY" else "OFFLINE"
    val statusColor = when (status.uppercase()) {
        "ARMED", "READY" -> HaloGlassColors.Success
        "CAUTION", "BLOCKED" -> HaloGlassColors.Warning
        "FAULT", "ESTOP" -> HaloGlassColors.Danger
        else -> HaloGlassColors.TextMuted
    }
    HaloGlassSection(title = "BUDDYBOT") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)).border(1.dp, statusColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", style = HaloTypography.titleLarge, color = statusColor)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(device?.name ?: "BuddyBot", style = HaloTypography.titleLarge, color = HaloGlassColors.TextPrimary)
                    Text(if (isConnected) "${device?.connectionStateText ?: "Connected"} • ${device?.battery ?: "Battery unknown"}" else "Disconnected", style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
                }
                Text(status.uppercase(), style = HaloTypography.labelMedium, color = statusColor)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("RANGE", telemetry.rangeMm?.let { "${it}mm" } ?: "—")
                MiniMetric("MOTORS", if (telemetry.motorsArmed) "ARMED" else "OFF")
                MiniMetric("MOOD", (telemetry.moodName ?: "idle").uppercase())
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
private fun MiniMetric(label: String, value: String) {
    Box(
        Modifier.weight(1f).clip(HaloGlassTokens.smallShape).background(HaloGlassColors.BackgroundSecondary).border(1.dp, HaloGlassColors.BorderSubtle, HaloGlassTokens.smallShape).padding(10.dp)
    ) {
        Column {
            Text(label, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
            Spacer(Modifier.height(3.dp))
            Text(value, style = HaloTypography.labelMedium, color = HaloGlassColors.TextPrimary)
        }
    }
}

@Composable
private fun BuddyBotJoystick(
    enabled: Boolean,
    offset: Offset,
    onOffsetChanged: (Offset) -> Unit,
    onRelease: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth.value, 250f).dp
        Canvas(
            modifier = Modifier
                .size(diameter)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onOffsetChanged(Offset.Zero) },
                        onDragCancel = onRelease,
                        onDragEnd = onRelease,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val requested = Offset(
                                change.position.x - center.x,
                                change.position.y - center.y
                            )
                            val radius = size.minDimension * 0.34f
                            val length = hypot(requested.x, requested.y)
                            val scale = if (length > radius) radius / length else 1f
                            onOffsetChanged(Offset(requested.x * scale, requested.y * scale))
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.46f
            val dead = outer * 0.27f
            drawCircle(
                color = if (enabled) HaloGlassColors.GlassElevated else HaloGlassColors.Glass,
                radius = outer,
                center = center
            )
            drawCircle(
                color = if (enabled) HaloGlassColors.BorderStandard else HaloGlassColors.BorderSubtle,
                radius = outer,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = HaloGlassColors.TextMuted.copy(alpha = 0.20f),
                radius = dead,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawLine(HaloGlassColors.BorderSubtle, Offset(center.x, center.y - outer), Offset(center.x, center.y + outer), 1.dp.toPx())
            drawLine(HaloGlassColors.BorderSubtle, Offset(center.x - outer, center.y), Offset(center.x + outer, center.y), 1.dp.toPx())
            val knob = Offset(
                center.x + offset.x * (outer / (size.minDimension * 0.34f)),
                center.y + offset.y * (outer / (size.minDimension * 0.34f))
            )
            drawCircle(
                color = if (enabled) HaloGlassColors.Accent.copy(alpha = 0.88f) else HaloGlassColors.TextMuted.copy(alpha = 0.45f),
                radius = size.minDimension * 0.14f,
                center = knob
            )
            drawCircle(
                color = HaloGlassColors.TextPrimary.copy(alpha = if (enabled) 0.35f else 0.15f),
                radius = size.minDimension * 0.14f,
                center = knob,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▲", style = HaloTypography.labelSmall, color = if (enabled) HaloGlassColors.TextSecondary else HaloGlassColors.TextMuted)
            Spacer(Modifier.height(78.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◀", style = HaloTypography.labelSmall, color = if (enabled) HaloGlassColors.TextSecondary else HaloGlassColors.TextMuted)
                Spacer(Modifier.width(118.dp))
                Text("▶", style = HaloTypography.labelSmall, color = if (enabled) HaloGlassColors.TextSecondary else HaloGlassColors.TextMuted)
            }
            Spacer(Modifier.height(78.dp))
            Text("▼", style = HaloTypography.labelSmall, color = if (enabled) HaloGlassColors.TextSecondary else HaloGlassColors.TextMuted)
        }
    }
}

@Composable
private fun QuickDriveButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    HaloGlassButton(
        text = "$symbol  $label",
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun MoodButton(
    label: String,
    mood: com.halo.sdk.runtime.embodiment.Mood,
    enabled: Boolean,
    onClick: (com.halo.sdk.runtime.embodiment.Mood) -> Unit
) {
    HaloGlassButton(
        text = label,
        onClick = { onClick(mood) },
        enabled = enabled,
        modifier = Modifier.weight(1f),
        isPrimary = false
    )
}

@Composable
private fun TelemetryLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
        Text(value, style = HaloTypography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun StatusLine(label: String, value: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
        Text(value, style = HaloTypography.labelMedium, color = if (positive) HaloGlassColors.Success else HaloGlassColors.TextMuted)
    }
}

private fun HaloGlassButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    isPrimary: Boolean = true
) = HaloGlassButton(
    text = text,
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    isPrimary = isPrimary
)
