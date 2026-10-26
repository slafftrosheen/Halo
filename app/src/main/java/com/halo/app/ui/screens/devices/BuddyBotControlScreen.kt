package com.halo.app.ui.screens.devices

import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun BuddyBotControlScreen(deviceId: String, viewModel: DevicesViewModel,
    buddyBotHost: BuddyBotHost? = viewModel.buddyBotHost, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val device = state.devices.firstOrNull { it.id == deviceId }
    val fallback = remember { MutableStateFlow(BuddyBotTelemetry()) }
    val telemetry by (buddyBotHost?.telemetry ?: fallback).collectAsStateWithLifecycle()
    val connected = device?.isConnected == true
    val fault = telemetry.safetyState?.uppercase() in setOf("FAULT", "ESTOP") ||
        !telemetry.safetyFault.isNullOrBlank() && !telemetry.safetyFault.equals("none", true)
    val driving = connected && telemetry.motorsArmed && !fault
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastSend by remember { mutableLongStateOf(0L) }
    var connection by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(buddyBotHost?.getTargetUrl().orEmpty()) }
    var code by remember { mutableStateOf("") }
    var connectionError by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    var armPending by remember { mutableStateOf(false) }
    var armUnconfirmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun dispatch(kind: IntentKind, mode: DriveMode = DriveMode.STOPPED, duration: Int = 0) {
        viewModel.dispatchEmbodimentIntent(deviceId, EmbodimentIntent(UUID.randomUUID().toString(),
            deviceId, kind, driveMode = mode, durationMs = duration))
    }
    fun stop() { armPending = false; armUnconfirmed = false; dispatch(IntentKind.STOP); offset = Offset.Zero }
    val stopAndDisarm = rememberUpdatedState { stop(); dispatch(IntentKind.DISARM) }
    androidx.compose.runtime.LaunchedEffect(armPending, driving, fault, connected) {
        if (driving || fault || !connected) armPending = false
        else if (armPending) {
            kotlinx.coroutines.delay(10_000)
            armPending = false
            armUnconfirmed = true
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, deviceId) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stopAndDisarm.value() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stopAndDisarm.value() }
    }
    androidx.compose.runtime.LaunchedEffect(fault, connected) {
        if (fault || !connected) stopAndDisarm.value()
    }
    val shellHosted = BindShellChildPage(device?.name ?: "BuddyBot", onBack, objectId = deviceId.takeIf { it == buddyBotHost?.deviceId && it.length <= 240 && it.matches(Regex("[A-Za-z0-9_.:/+-]+")) }, titleIsSource = true,
        sourceIdentity = "$deviceId:$connected:$driving:$fault", actions = listOf(
            ShellPageAction("buddy.connection", "Connection settings", HaloGlyph.DEVICE) { connection = true },
            ShellPageAction("buddy.stop", "Stop", HaloGlyph.STOP, onInvoke = ::stop),
        ))
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!shellHosted) HaloWorkspaceHeader(device?.name ?: "BuddyBot", uiText("Robot · manual control"), onBack)
            HaloWorkspaceCard {
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column { Text(uiText("Battery")); Text(telemetry.batteryPercent?.let { "$it%" } ?: device?.battery ?: uiText("Unknown"), style = MaterialTheme.typography.titleLarge) }
                    Column { Text(uiText("Ahead")); Text(telemetry.rangeMm?.takeIf { telemetry.rangeValid }?.let { "${it / 1000.0} m" } ?: uiText("No reading"), style = MaterialTheme.typography.titleLarge) }
                    Column { Text(uiText("Mood")); Text(uiText(when (telemetry.moodName?.lowercase()) { "happy" -> "Happy"; "curious" -> "Curious"; "calm" -> "Calm"; else -> "Unknown" }), style = MaterialTheme.typography.titleLarge) }
                }
            }
            com.halo.app.ui.presence.HaloSignalFace(com.halo.app.ui.presence.HaloSignalPresentation(label = "BuddyBot"), size = 112.dp)
            Text(uiText(when {
                !connected -> "Find BuddyBot on Wi-Fi"
                fault -> "Movement stopped"
                telemetry.forwardMotionBlocked -> "Something is in the way"
                driving -> "Drag to steer · release to stop"
                telemetry.safetyState.equals("READY", true) -> "Ready to drive"
                else -> "Movement is off"
            }), style = MaterialTheme.typography.headlineSmall)
            Text(uiText(when {
                !connected -> "Connect BuddyBot to the same Wi-Fi network, then check its address."
                fault -> "Turn off driving and check BuddyBot before trying again."
                telemetry.forwardMotionBlocked -> "Clear the space in front of BuddyBot before moving forward."
                else -> "Make sure the floor is clear. Release the steering control to stop."
            }), style = MaterialTheme.typography.bodyLarge)
            if (connected && !fault && !telemetry.motorsArmed) {
                if (armUnconfirmed) Text(uiText("Driving was not confirmed. Check BuddyBot, then hold to try again."),
                    color = MaterialTheme.colorScheme.error)
                val label = uiText(if (armPending) "Waiting for BuddyBot…" else "Hold to start driving")
                Surface(Modifier.fillMaxWidth().heightIn(min = 56.dp).combinedClickable(
                    enabled = !armPending, onClick = {}, onLongClickLabel = label, onLongClick = {
                        armUnconfirmed = false; armPending = true; dispatch(IntentKind.ARM) }),
                    shape = com.halo.app.ui.design.HaloShapes.pill, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(label, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            if (driving) {
                BuddyBotJoystick(true, telemetry.forwardMotionBlocked, offset, onMove = { raw ->
                    offset = raw
                    val magnitude = min(1f, hypot(raw.x, raw.y) / 100f)
                    if (magnitude < .14f) { stop(); return@BuddyBotJoystick }
                    val x = raw.x; val y = -raw.y
                    val mode = when { y >= abs(x) -> DriveMode.FORWARD; y <= -abs(x) -> DriveMode.REVERSE
                        x < 0 -> DriveMode.ROTATE_LEFT; else -> DriveMode.ROTATE_RIGHT }
                    if (mode == DriveMode.FORWARD && telemetry.forwardMotionBlocked) { stop(); return@BuddyBotJoystick }
                    val now = System.currentTimeMillis()
                    if (now - lastSend >= JOYSTICK_SEND_INTERVAL_MS) {
                        lastSend = now
                        dispatch(IntentKind.MOVE, mode, (JOYSTICK_MIN_PULSE_MS + (JOYSTICK_MAX_PULSE_MS - JOYSTICK_MIN_PULSE_MS) * magnitude).toInt())
                    }
                }, onRelease = ::stop)
                HaloWorkspaceButton(uiText("Turn off driving"), { stopAndDisarm.value() }, Modifier.fillMaxWidth(), primary = false)
            }
            if (!shellHosted) HaloWorkspaceButton(uiText(if (connected) "Connection settings" else "Find on Wi-Fi"),
                { connection = true }, Modifier.fillMaxWidth(), primary = !connected)
            if (connection) HaloWorkspaceCard {
                HaloTextField(address, { address = it; connectionError = false }, label = { Text(uiText("Wi-Fi address")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, kind = VoiceFieldKind.TEXT)
                HaloWorkspaceButton(uiText("Save address"), {
                    val uri = android.net.Uri.parse(address.trim())
                    if (uri.scheme !in setOf("ws", "wss") || uri.host.isNullOrBlank() || uri.userInfo != null) connectionError = true
                    else { buddyBotHost?.setTargetUrl(address.trim()); connectionError = false }
                }, Modifier.fillMaxWidth(), enabled = buddyBotHost != null && address.isNotBlank())
                if (connectionError) Text(uiText("Check the address or pairing code and try again."), color = MaterialTheme.colorScheme.error)
                HaloTextField(code, { code = it.take(9) }, label = { Text(uiText("Pairing code")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, kind = VoiceFieldKind.CODE)
                HaloWorkspaceButton(uiText("Pair"), {
                    pairing = true; connectionError = false
                    scope.launch {
                        try { buddyBotHost?.pair(code.replace(" ", "")) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { connectionError = true }
                        finally { pairing = false }
                    }
                }, Modifier.fillMaxWidth(), enabled = connected && buddyBotHost != null && code.isNotBlank() && !pairing)
                HaloWorkspaceToggleRow(title = "Connect automatically", checked = device?.autoConnect == true,
                    onCheckedChange = { viewModel.toggleAutoConnect(deviceId, it) })
                TextButton(onClick = { forget = true }) { Text(uiText("Forget device")) }
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            com.halo.app.ui.components.HaloActionButton(onClick = ::stop,
                modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().heightIn(min = 56.dp),
                style = com.halo.app.ui.components.HaloActionStyle.STOP) { Text(uiText("STOP")) }
        }
    }
    if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { Text(uiText("Forget device?")) },
        text = { Text(uiText("BuddyBot will stop and be removed from your saved devices.")) },
        confirmButton = { TextButton(onClick = { stopAndDisarm.value(); forget = false; viewModel.forgetDevice(deviceId); onBack() }) { Text(uiText("Forget")) } },
        dismissButton = { TextButton(onClick = { forget = false }) { Text(uiText("Cancel")) } })
}

@Composable
private fun BuddyBotJoystick(
    enabled: Boolean,
    forwardBlocked: Boolean,
    offset: Offset,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit
) {
    val move = rememberUpdatedState(onMove)
    val release = rememberUpdatedState(onRelease)
    DisposableEffect(Unit) { onDispose { release.value() } }
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
                            onDragStart = { move.value(Offset.Zero) },
                            onDragCancel = { release.value() },
                            onDragEnd = { release.value() },
                            onDrag = { change, _ ->
                                change.consume()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val requested = change.position - center
                                val radius = min(size.width, size.height) * 0.34f
                                val length = hypot(requested.x, requested.y)
                                val scale = if (length > radius) radius / length else 1f
                                move.value(
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

internal fun buddyBotMotorLabel(connected: Boolean, armed: Boolean): String =
    if (!connected) "Not confirmed" else if (armed) "Armed" else "Off"
