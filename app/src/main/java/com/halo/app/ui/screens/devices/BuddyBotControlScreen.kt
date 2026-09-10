package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.runtime.embodiment.ActionId
import com.halo.sdk.runtime.embodiment.DriveMode
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind
import com.halo.sdk.runtime.embodiment.Mood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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
    var targetUrlInput by remember {
        mutableStateOf(buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL)
    }

    fun dispatchIntent(
        kind: IntentKind,
        driveMode: DriveMode = DriveMode.STOPPED,
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
                driveMode = driveMode,
                durationMs = durationMs,
                action = action,
                mood = mood
            )
        )
    }

    if (showUrlDialog) {
        HaloGlassDialog(
            title = "BuddyBot Target WebSocket URL",
            onDismissRequest = { showUrlDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Configure the WebSocket endpoint for BuddyBot (e.g., when connecting over local Wi-Fi router).",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HaloGlassButton(
                        text = "Reset",
                        onClick = {
                            targetUrlInput = BuddyBotHost.DEFAULT_BUDDYBOT_URL
                        },
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
        item {
            HaloGlassBackHeader(
                title = device?.name ?: "BuddyBot",
                onBack = onBack
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ─── CONNECTION SECTION ─────────────────────────────────────
        item {
            HaloGlassSection(title = "CONNECTION") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isConnected) viewModel.disconnectDevice(deviceId)
                            else viewModel.connectDevice(deviceId)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Status", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isConnected) HaloGlassColors.Success else HaloGlassColors.TextMuted,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                device?.connectionStateText ?: "Unknown",
                                style = HaloTypography.bodySmall,
                                color = if (isConnected) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                            )
                        }
                    }
                    Text(
                        if (isConnected) "Disconnect" else "Connect",
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.Accent
                    )
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUrlDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Target URL", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            buddyBotHost?.getTargetUrl() ?: BuddyBotHost.DEFAULT_BUDDYBOT_URL,
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextSecondary
                        )
                    }
                    Text("Edit", style = HaloTypography.bodyMedium, color = HaloGlassColors.Accent)
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAutoConnect(deviceId, !(device?.autoConnect ?: false)) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-connect", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                    Text(
                        if (device?.autoConnect == true) "ON" else "OFF",
                        style = HaloTypography.bodyMedium,
                        color = if (device?.autoConnect == true) HaloGlassColors.Accent else HaloGlassColors.TextSecondary
                    )
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.forgetDevice(deviceId) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("Forget device", style = HaloTypography.bodyLarge, color = HaloGlassColors.Danger)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ─── PAIRING & AUTHENTICATION ────────────────────────────────
        item {
            HaloGlassSection(title = "PAIRING & SECURITY") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isPaired = buddyBotHost?.session?.hasToken == true || telemetry.controllerPresent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Controller Lease",
                            style = HaloTypography.bodyMedium,
                            color = HaloGlassColors.TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPaired) HaloGlassColors.Success.copy(alpha = 0.2f)
                                    else HaloGlassColors.Warning.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isPaired) "PAIRED & ACTIVE" else "UNPAIRED",
                                style = HaloTypography.labelSmall,
                                color = if (isPaired) HaloGlassColors.Success else HaloGlassColors.Warning
                            )
                        }
                    }

                    Text(
                        "BuddyBot displays an 8-digit code on boot (e.g. 1234 5678). Pairing is required to authorize motor movements.",
                        style = HaloTypography.bodySmall,
                        color = HaloGlassColors.TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                if (cleaned.isNotEmpty()) {
                                    scope.launch {
                                        buddyBotHost?.pair(cleaned)
                                    }
                                }
                            },
                            enabled = isConnected && pairingCodeInput.isNotBlank()
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ─── TELEMETRY & SENSORS ────────────────────────────────────
        item {
            HaloGlassSection(title = "SENSORS & SAFETY") {
                val safetyText = telemetry.safetyState ?: "UNKNOWN"
                val safetyColor = when (safetyText.uppercase()) {
                    "ARMED" -> HaloGlassColors.Success
                    "CAUTION" -> HaloGlassColors.Warning
                    "ESTOP", "FAULT", "BLOCKED" -> HaloGlassColors.Danger
                    else -> HaloGlassColors.TextSecondary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Safety Supervisor", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    Text(safetyText, style = HaloTypography.titleMedium, color = safetyColor)
                }

                if (!telemetry.safetyFault.isNullOrEmpty() && telemetry.safetyFault != "none") {
                    HaloGlassDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Safety Fault", style = HaloTypography.bodySmall, color = HaloGlassColors.Danger)
                        Text(telemetry.safetyFault ?: "", style = HaloTypography.bodySmall, color = HaloGlassColors.Danger)
                    }
                }

                HaloGlassDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Ultrasonic Range", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (telemetry.forwardMotionBlocked || telemetry.obstacleDetected) "Obstacle Blocked"
                            else if (telemetry.rangeValid) "Clear"
                            else "Sensor Idle",
                            style = HaloTypography.bodySmall,
                            color = if (telemetry.forwardMotionBlocked || telemetry.obstacleDetected) HaloGlassColors.Warning
                            else if (telemetry.rangeValid) HaloGlassColors.Success
                            else HaloGlassColors.TextSecondary
                        )
                    }
                    Text(
                        if (telemetry.rangeMm != null) "${telemetry.rangeMm} mm" else if (telemetry.rangeValid) "Valid" else "N/A",
                        style = HaloTypography.titleMedium,
                        color = HaloGlassColors.TextPrimary
                    )
                }

                HaloGlassDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Motors Status", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (telemetry.motorsArmed) "ARMED" else "DISARMED",
                            style = HaloTypography.bodyMedium,
                            color = if (telemetry.motorsArmed) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                        )
                        HaloGlassButton(
                            text = if (telemetry.motorsArmed) "Disarm" else "Arm",
                            onClick = {
                                if (telemetry.motorsArmed) dispatchIntent(IntentKind.DISARM)
                                else dispatchIntent(IntentKind.ARM)
                            },
                            enabled = isConnected,
                            isPrimary = !telemetry.motorsArmed
                        )
                    }
                }

                HaloGlassDivider()

                HaloGlassSettingRow(
                    title = "Persona / Mood",
                    value = "${telemetry.personaName ?: "BuddyBot"} • ${(telemetry.moodName ?: "idle").replaceFirstChar { it.uppercase() }}"
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ─── MANUAL KINEMATICS & DRIVE PAD ──────────────────────────
        item {
            HaloGlassSection(title = "KINEMATICS & DRIVE") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Forward
                    HaloGlassButton(
                        text = "▲ Forward",
                        onClick = {
                            dispatchIntent(
                                kind = IntentKind.MOVE,
                                driveMode = DriveMode.FORWARD,
                                durationMs = 500
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        enabled = isConnected
                    )

                    // Left - STOP - Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HaloGlassButton(
                            text = "◀ Left",
                            onClick = {
                                dispatchIntent(
                                    kind = IntentKind.MOVE,
                                    driveMode = DriveMode.ROTATE_LEFT,
                                    durationMs = 300
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )

                        HaloGlassButton(
                            text = "STOP",
                            onClick = { dispatchIntent(IntentKind.STOP) },
                            modifier = Modifier.weight(1.3f),
                            enabled = isConnected,
                            material = HaloGlassMaterials.Interactive,
                            contentColor = HaloGlassColors.Danger
                        )

                        HaloGlassButton(
                            text = "Right ▶",
                            onClick = {
                                dispatchIntent(
                                    kind = IntentKind.MOVE,
                                    driveMode = DriveMode.ROTATE_RIGHT,
                                    durationMs = 300
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                    }

                    // Reverse
                    HaloGlassButton(
                        text = "▼ Reverse",
                        onClick = {
                            dispatchIntent(
                                kind = IntentKind.MOVE,
                                driveMode = DriveMode.REVERSE,
                                durationMs = 500
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        enabled = isConnected
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ─── GESTURES & MOODS ───────────────────────────────────────
        item {
            HaloGlassSection(title = "EXPRESSIONS & GESTURES") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Physical Gestures", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HaloGlassButton(
                            text = "Wave",
                            onClick = { dispatchIntent(IntentKind.ACTION, action = ActionId.NOD_YES) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                        HaloGlassButton(
                            text = "Celebrate",
                            onClick = { dispatchIntent(IntentKind.ACTION, action = ActionId.CELEBRATE) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                        HaloGlassButton(
                            text = "Look L/R",
                            onClick = { dispatchIntent(IntentKind.ACTION, action = ActionId.LOOK_LEFT) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Mood & Personality", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HaloGlassButton(
                            text = "Happy",
                            onClick = { dispatchIntent(IntentKind.SET_MOOD, mood = Mood.HAPPY) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                        HaloGlassButton(
                            text = "Curious",
                            onClick = { dispatchIntent(IntentKind.SET_MOOD, mood = Mood.CURIOUS) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                        HaloGlassButton(
                            text = "Alert",
                            onClick = { dispatchIntent(IntentKind.SET_MOOD, mood = Mood.ANGRY) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HaloGlassButton(
                            text = "Sleepy",
                            onClick = { dispatchIntent(IntentKind.SET_MOOD, mood = Mood.SLEEPY) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                        HaloGlassButton(
                            text = "Next Persona",
                            onClick = { dispatchIntent(IntentKind.NEXT_PERSONA) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ─── DEVICE STATE ───────────────────────────────────────────
        item {
            HaloGlassSection(title = "DEVICE STATE") {
                HaloGlassSettingRow(
                    title = "Battery",
                    value = telemetry.batteryPercent?.let { "$it%" } ?: device?.battery ?: "Unknown"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Firmware",
                    value = device?.firmware ?: "v0.3.0"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Uptime",
                    value = if (telemetry.uptimeMs > 0L) "${telemetry.uptimeMs / 1000}s" else "Unknown"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Wi-Fi Clients",
                    value = "${telemetry.wifiClientCount}"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Last seen",
                    value = device?.lastSeen ?: "Never"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Health",
                    value = device?.healthStateText ?: "Unknown"
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}
