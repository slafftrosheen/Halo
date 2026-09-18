package com.halo.app.ui.screens.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.embodiment.BuddyBotHost
import com.halo.app.embodiment.BuddyBotTelemetry
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceRow
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.sdk.runtime.embodiment.ActionId
import com.halo.sdk.runtime.embodiment.DriveMode
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind
import com.halo.sdk.runtime.embodiment.Mood
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val DRIVE_PULSE_MS = 180
private const val JOYSTICK_MIN_PULSE_MS = 90
private const val JOYSTICK_MAX_PULSE_MS = 250
private const val JOYSTICK_SEND_INTERVAL_MS = 120L

@Composable
fun BuddyBotControlScreen(
    deviceId: String,
    viewModel: DevicesViewModel,
    buddyBotHost: BuddyBotHost? = viewModel.buddyBotHost,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.devices.find { it.id == deviceId }
    val isConnected = device?.isConnected == true
    val fallbackTelemetry = remember { MutableStateFlow(BuddyBotTelemetry()) }
    val telemetry by (buddyBotHost?.telemetry ?: fallbackTelemetry).collectAsState()
    val scope = rememberCoroutineScope()

    var pairingCode by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var targetUrl by remember {
        mutableStateOf(buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL)
    }
    var joystickOffset by remember { mutableStateOf(Offset.Zero) }
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
        joystickDirection = "STOPPED"
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("BuddyBot target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Local WebSocket endpoint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = { Text("WebSocket URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { targetUrl = BuddyBotHost.DEFAULT_BUDDYBOT_URL }) {
                    Text("Reset")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        buddyBotHost?.setTargetUrl(targetUrl.trim())
                        showUrlDialog = false
                    }
                ) {
                    Text("Save")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = device?.name ?: "BuddyBot",
            subtitle = "Embodiment · manual control",
            onBack = onBack
        )

        Spacer(Modifier.height(18.dp))

        BuddyBotHero(
            device = device,
            telemetry = telemetry,
            isConnected = isConnected,
            onStop = ::stopMotion
        )

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Manual control") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val driveEnabled = isConnected && telemetry.motorsArmed
                val hint = when {
                    !isConnected -> "Connect BuddyBot"
                    !telemetry.motorsArmed -> "Arm motors to drive"
                    telemetry.forwardMotionBlocked -> "Forward locked by safety"
                    else -> "Drag to steer · release to stop"
                }

                BuddyBotDriveHud(
                    telemetry = telemetry,
                    connectionReady = driveEnabled,
                    direction = joystickDirection,
                    modifier = Modifier.fillMaxWidth()
                )

                BuddyBotJoystick(
                    enabled = driveEnabled,
                    forwardBlocked = telemetry.forwardMotionBlocked,
                    offset = joystickOffset,
                    onMove = { raw ->
                        joystickOffset = raw
                        if (!driveEnabled) return@BuddyBotJoystick

                        val magnitude = min(1f, hypot(raw.x, raw.y) / 100f)
                        if (magnitude < 0.14f) {
                            stopMotion()
                            return@BuddyBotJoystick
                        }

                        val x = (raw.x / 100f).coerceIn(-1f, 1f)
                        val y = (-raw.y / 100f).coerceIn(-1f, 1f)
                        val mode = when {
                            y >= abs(x) -> DriveMode.FORWARD
                            y <= -abs(x) -> DriveMode.REVERSE
                            x < 0f -> DriveMode.ROTATE_LEFT
                            else -> DriveMode.ROTATE_RIGHT
                        }

                        if (mode == DriveMode.FORWARD && telemetry.forwardMotionBlocked) {
                            joystickDirection = "FORWARD LOCKED"
                            stopMotion()
                            return@BuddyBotJoystick
                        }

                        joystickDirection = when (mode) {
                            DriveMode.FORWARD -> "FORWARD"
                            DriveMode.REVERSE -> "REVERSE"
                            DriveMode.ROTATE_LEFT -> "LEFT"
                            else -> "RIGHT"
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastJoystickSend < JOYSTICK_SEND_INTERVAL_MS) {
                            return@BuddyBotJoystick
                        }
                        lastJoystickSend = now
                        val pulseMs = (
                            JOYSTICK_MIN_PULSE_MS +
                                (JOYSTICK_MAX_PULSE_MS - JOYSTICK_MIN_PULSE_MS) * magnitude
                            ).toInt()
                        dispatch(IntentKind.MOVE, mode, pulseMs)
                    },
                    onRelease = ::stopMotion
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = joystickDirection,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (driveEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.forwardMotionBlocked) {
                            HaloWarning
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                BuddyBotEmergencyStop(
                    enabled = isConnected,
                    onClick = ::stopMotion
                )

                Text(
                    text = "Safety Supervisor remains authoritative over every movement.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Drive pad") {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val driveEnabled = isConnected && telemetry.motorsArmed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DriveButton(
                        text = "▲  Forward",
                        enabled = driveEnabled && !telemetry.forwardMotionBlocked
                    ) {
                        dispatch(IntentKind.MOVE, DriveMode.FORWARD, DRIVE_PULSE_MS)
                    }
                    DriveButton("↶  Left", driveEnabled) {
                        dispatch(IntentKind.MOVE, DriveMode.ROTATE_LEFT, DRIVE_PULSE_MS)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DriveButton("■  Stop", isConnected, ::stopMotion)
                    DriveButton("Right  ↷", driveEnabled) {
                        dispatch(IntentKind.MOVE, DriveMode.ROTATE_RIGHT, DRIVE_PULSE_MS)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DriveButton("▼  Reverse", driveEnabled) {
                        dispatch(IntentKind.MOVE, DriveMode.REVERSE, DRIVE_PULSE_MS)
                    }
                    DriveButton(
                        if (telemetry.motorsArmed) "DISARM" else "ARM",
                        isConnected
                    ) {
                        dispatch(
                            if (telemetry.motorsArmed) IntentKind.DISARM
                            else IntentKind.ARM
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Safety") {
            val safety = telemetry.safetyState ?: "UNKNOWN"
            val safetyColor = when (safety.uppercase()) {
                "ARMED", "READY" -> MaterialTheme.colorScheme.primary
                "CAUTION", "BLOCKED" -> HaloWarning
                "FAULT", "ESTOP" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            TelemetryLine("Supervisor", safety, safetyColor)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TelemetryLine(
                "Obstacle",
                when {
                    telemetry.forwardMotionBlocked -> "Forward blocked"
                    telemetry.obstacleDetected -> "Detected"
                    telemetry.rangeValid -> "Clear"
                    else -> "No reading"
                },
                if (telemetry.forwardMotionBlocked || telemetry.obstacleDetected) {
                    HaloWarning
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            telemetry.rangeMm?.let { range ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TelemetryLine(
                    "Range",
                    "$range mm",
                    MaterialTheme.colorScheme.onSurface
                )
            }
            if (!telemetry.safetyFault.isNullOrEmpty() && telemetry.safetyFault != "none") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TelemetryLine(
                    "Fault",
                    telemetry.safetyFault ?: "Unknown",
                    MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TelemetryLine(
                "Motors",
                if (telemetry.motorsArmed) "ARMED" else "DISARMED",
                if (telemetry.motorsArmed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Personality") {
            HaloWorkspaceRow(
                title = "Persona",
                trailingText = telemetry.personaName ?: "BuddyBot"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Mood",
                trailingText = (telemetry.moodName ?: "idle").replaceFirstChar { it.uppercase() }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoodButton("Happy", Mood.HAPPY, isConnected) {
                        dispatch(IntentKind.SET_MOOD, mood = it)
                    }
                    MoodButton("Curious", Mood.CURIOUS, isConnected) {
                        dispatch(IntentKind.SET_MOOD, mood = it)
                    }
                    MoodButton("Sleepy", Mood.SLEEPY, isConnected) {
                        dispatch(IntentKind.SET_MOOD, mood = it)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DriveButton("Celebrate", isConnected) {
                        dispatch(IntentKind.ACTION, action = ActionId.CELEBRATE)
                    }
                    DriveButton("Look left", isConnected) {
                        dispatch(IntentKind.ACTION, action = ActionId.LOOK_LEFT)
                    }
                    DriveButton("Next persona", isConnected) {
                        dispatch(IntentKind.NEXT_PERSONA)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Connection") {
            val paired = buddyBotHost?.session?.hasToken == true || telemetry.controllerPresent
            StatusLine("Status", device?.connectionStateText ?: "Unknown", isConnected)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatusLine(
                "Controller lease",
                if (paired) "PAIRED & ACTIVE" else "UNPAIRED",
                paired
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Target URL",
                subtitle = buddyBotHost?.getTargetUrl() ?: "BuddyBot host unavailable",
                enabled = buddyBotHost != null,
                onClick = { showUrlDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceToggleRow(
                title = "Auto-connect",
                subtitle = "Reconnect when BuddyBot becomes available.",
                checked = device?.autoConnect == true,
                onCheckedChange = { enabled ->
                    viewModel.toggleAutoConnect(deviceId, enabled)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { if (it.length <= 9) pairingCode = it },
                    placeholder = { Text("1234 5678") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                HaloWorkspaceButton(
                    text = "Pair",
                    onClick = {
                        val code = pairingCode.replace(" ", "")
                        if (code.isNotBlank()) {
                            scope.launch { buddyBotHost?.pair(code) }
                        }
                    },
                    enabled = buddyBotHost != null && isConnected && pairingCode.isNotBlank()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.forgetDevice(deviceId) },
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "Forget device",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Device") {
            HaloWorkspaceRow(
                title = "Battery",
                trailingText = telemetry.batteryPercent?.let { "$it%" }
                    ?: device?.battery
                    ?: "Unknown"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Firmware",
                trailingText = device?.firmware ?: "v0.3.0"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Uptime",
                trailingText = if (telemetry.uptimeMs > 0) {
                    "${telemetry.uptimeMs / 1000}s"
                } else {
                    "Unknown"
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Wi-Fi clients",
                trailingText = telemetry.wifiClientCount.toString()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Health",
                trailingText = device?.healthStateText ?: "Unknown"
            )
        }

        Spacer(Modifier.height(32.dp))
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
    val tone = when (status.uppercase()) {
        "ARMED", "READY" -> MaterialTheme.colorScheme.primary
        "CAUTION", "BLOCKED" -> HaloWarning
        "FAULT", "ESTOP" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    HaloWorkspaceCard(accent = tone) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = tone.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, tone.copy(alpha = 0.45f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "B",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = tone
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device?.name ?: "BuddyBot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isConnected) {
                        "${device?.connectionStateText ?: "Connected"} · ${device?.battery ?: "Battery unknown"}"
                    } else {
                        "Disconnected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = status.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = tone
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Metric("RANGE", telemetry.rangeMm?.let { "${it}mm" } ?: "—")
            Metric("MOTORS", if (telemetry.motorsArmed) "ARMED" else "OFF")
            Metric("MOOD", (telemetry.moodName ?: "idle").uppercase())
        }

        if (isConnected) {
            BuddyBotEmergencyStop(
                enabled = true,
                onClick = onStop,
                text = "STOP ALL MOTION"
            )
        }
    }
}

@Composable
private fun BuddyBotDriveHud(
    telemetry: BuddyBotTelemetry,
    connectionReady: Boolean,
    direction: String,
    modifier: Modifier = Modifier
) {
    val warning = telemetry.forwardMotionBlocked || telemetry.obstacleDetected
    val tone = when {
        warning -> HaloWarning
        connectionReady -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = direction,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tone
                )
                Text(
                    text = telemetry.driveModeName ?: "Runtime standby",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = telemetry.rangeMm?.let { "${it} mm" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        telemetry.forwardMotionBlocked -> "FORWARD BLOCKED"
                        telemetry.obstacleDetected -> "OBSTACLE"
                        telemetry.rangeValid -> "CLEAR"
                        else -> "NO RANGE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tone
                )
            }
        }
    }
}

@Composable
private fun BuddyBotJoystick(
    enabled: Boolean,
    forwardBlocked: Boolean,
    offset: Offset,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val warning = HaloWarning
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.secondary
    val foreground = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val diameter = min(maxWidth.value, 250f).dp

        Box(
            modifier = Modifier.size(diameter),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(diameter)
                    .pointerInput(enabled, forwardBlocked) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { onMove(Offset.Zero) },
                            onDragCancel = onRelease,
                            onDragEnd = onRelease,
                            onDrag = { change, _ ->
                                change.consume()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val requested = change.position - center
                                val radius = min(size.width, size.height) * 0.34f
                                val length = hypot(requested.x, requested.y)
                                val scale = if (length > radius) radius / length else 1f
                                onMove(
                                    Offset(
                                        requested.x * scale,
                                        requested.y * scale
                                    )
                                )
                            }
                        )
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outer = size.minDimension * 0.46f
                val dead = outer * 0.26f

                drawCircle(surface, outer, center)
                drawCircle(
                    color = if (forwardBlocked) warning.copy(alpha = 0.62f) else outline,
                    radius = outer,
                    center = center,
                    style = Stroke(2.dp.toPx())
                )
                drawCircle(
                    color = muted.copy(alpha = 0.18f),
                    radius = dead,
                    center = center,
                    style = Stroke(1.dp.toPx())
                )
                drawLine(
                    muted.copy(alpha = 0.22f),
                    Offset(center.x, center.y - outer),
                    Offset(center.x, center.y + outer),
                    1.dp.toPx()
                )
                drawLine(
                    muted.copy(alpha = 0.22f),
                    Offset(center.x - outer, center.y),
                    Offset(center.x + outer, center.y),
                    1.dp.toPx()
                )

                if (forwardBlocked) {
                    drawLine(
                        warning,
                        Offset(center.x - outer * 0.35f, center.y - outer * 0.70f),
                        Offset(center.x + outer * 0.35f, center.y - outer * 0.70f),
                        3.dp.toPx()
                    )
                }

                val knobScale = outer / (size.minDimension * 0.34f)
                val knob = Offset(
                    center.x + offset.x * knobScale,
                    center.y + offset.y * knobScale
                )
                drawCircle(
                    if (enabled) accent.copy(alpha = 0.92f) else muted.copy(alpha = 0.42f),
                    size.minDimension * 0.14f,
                    knob
                )
                drawCircle(
                    foreground.copy(alpha = if (enabled) 0.30f else 0.12f),
                    size.minDimension * 0.14f,
                    knob,
                    style = Stroke(2.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (forwardBlocked) "×" else "▲",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (forwardBlocked) warning else muted
                )
                Spacer(Modifier.height(72.dp))
                Row {
                    Text("◀", style = MaterialTheme.typography.labelSmall, color = muted)
                    Spacer(Modifier.width(116.dp))
                    Text("▶", style = MaterialTheme.typography.labelSmall, color = muted)
                }
                Spacer(Modifier.height(72.dp))
                Text("▼", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

@Composable
private fun BuddyBotEmergencyStop(
    enabled: Boolean,
    onClick: () -> Unit,
    text: String = "EMERGENCY STOP"
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun RowScope.DriveButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    HaloWorkspaceButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f),
        primary = false
    )
}

@Composable
private fun RowScope.MoodButton(
    label: String,
    mood: Mood,
    enabled: Boolean,
    onClick: (Mood) -> Unit
) {
    HaloWorkspaceButton(
        text = label,
        onClick = { onClick(mood) },
        enabled = enabled,
        modifier = Modifier.weight(1f),
        primary = false
    )
}

@Composable
private fun TelemetryLine(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    positive: Boolean
) {
    TelemetryLine(
        label = label,
        value = value,
        color = if (positive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun RowScope.Metric(
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
