package com.halo.app.ui.screens.devices

import android.app.KeyguardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ink.CrossDeviceInkController
import com.halo.app.ink.CoachInkLocalNetworkAccess
import com.halo.app.ink.coachInkHasLocalNetwork
import com.halo.app.ui.screens.notebook.CanvasInkSurface
import com.halo.host.android.ink.AndroidCoachInkConnection
import com.halo.host.ink.CoachInkWire
import com.halo.host.ink.InkConnectionInvitation
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.ink.InkActivationPolicy
import com.halo.sdk.runtime.ink.InkCodec
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The installed Halo tablet app is the endpoint. The Coach worksheet and scratch never enter its Library. */
@Composable
internal fun CoachInkCompanion(owner: CrossDeviceInkController) {
    if (!owner.enabled) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val family = Graph.kidsPreferences
    val child by family.enabled.collectAsStateWithLifecycle()
    val ready by family.loadedState.collectAsStateWithLifecycle()
    val pause = Graph.haloPause
    val stopping by pause.state.collectAsStateWithLifecycle()
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val localNetwork = CoachInkLocalNetworkAccess(ready && !child && !stopping.paused)
    val scope = rememberCoroutineScope()
    val mounted = remember { AtomicBoolean(true) }
    var trusted by remember { mutableStateOf<List<DeviceRecord>?>(null) }
    var host by remember { mutableStateOf<DeviceRecord?>(null) }
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var connection by remember { mutableStateOf<AndroidCoachInkConnection?>(null) }
    fun close() {
        trusted = null
        host = null
        address = ""
        code = ""
        connection?.close()
        connection = null
    }
    fun eligible() = mounted.get() && owner.enabled && family.loadedState.value && !family.enabled.value &&
        coachInkHasLocalNetwork(context) &&
        !pause.isPaused() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) close() }
        lifecycle.addObserver(observer)
        onDispose { mounted.set(false); lifecycle.removeObserver(observer); close() }
    }
    LaunchedEffect(child, ready, stopping.version, mode, localNetwork) { close() }

    fun join(invitation: InkConnectionInvitation, selected: DeviceRecord) {
        if (busy || !eligible()) return
        busy = true
        failure = false
        scope.launch {
            try {
                val approval = family.adultActionRevision()
                val privacyRevision = Graph.privacyModes.revision
                val pauseRevision = pause.state.value.version
                fun authorized() = eligible() && Graph.privacyModes.revision == privacyRevision &&
                    pause.state.value.version == pauseRevision && runCatching { family.requireAdultAction(approval) }.isSuccess
                val newConnection = AndroidCoachInkConnection.open(invitation, scope, InkActivationPolicy(::authorized)) {
                    authorized() && owner.devices().any { it.identity.deviceId == selected.identity.deviceId &&
                        it.inkAssociation?.certificateSha256 == invitation.hostCertificateSha256 }
                }
                if (authorized()) {
                    connection = newConnection
                    trusted = null
                    host = null
                    address = ""
                    code = ""
                } else newConnection.close()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = true }
            finally { busy = false }
        }
    }
    TextButton(enabled = ready && !child && !stopping.paused && localNetwork && !busy && connection == null, onClick = {
        if (eligible()) scope.launch {
            failure = false
            val devices = owner.devices()
            if (eligible()) trusted = devices
        }
    }) { Text(stringResource(R.string.halo11_coach_ink_join)) }
    if (failure && trusted == null) Text(stringResource(R.string.halo11_coach_ink_mirror_failed), color = MaterialTheme.colorScheme.error)

    trusted?.let { devices ->
        val selected = host
        val endpoint = address.trim().split(':', limit = 2)
        val invitation = runCatching {
            val trustedPin = requireNotNull(selected?.inkAssociation?.certificateSha256)
            InkConnectionInvitation(CoachInkWire.sessionId(code), endpoint[0], endpoint[1].toInt(), trustedPin)
        }.getOrNull()
        AlertDialog(onDismissRequest = { if (!busy) close() },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.halo11_coach_ink_join)) },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected == null) {
                    Text(stringResource(R.string.halo11_coach_ink_choose_host))
                    if (devices.isEmpty()) Text(stringResource(R.string.halo11_coach_ink_mirror_no_devices))
                    devices.take(12).forEach { device ->
                        TextButton({ host = device }) { Text(device.identity.displayName) }
                    }
                } else {
                    Text(selected.identity.displayName, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(address, { address = it.take(24) }, label = {
                        Text(stringResource(R.string.halo11_coach_ink_host_address))
                    }, enabled = !busy, singleLine = true)
                    OutlinedTextField(code, { code = it.filter { digit -> digit in '0'..'9' }.take(CoachInkWire.CODE_LENGTH) },
                        label = { Text(stringResource(R.string.halo11_coach_ink_host_code)) }, enabled = !busy, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failure) Text(stringResource(R.string.halo11_coach_ink_mirror_failed), color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !busy && invitation != null && selected != null, onClick = {
                if (invitation != null && selected != null) join(invitation, selected)
            }) { Text(stringResource(R.string.halo11_coach_ink_join)) } },
            dismissButton = { TextButton({ if (!busy) close() }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    }
    connection?.let { mirror -> CoachInkWorksheet(mirror, ::close) }
}

@Composable
private fun CoachInkWorksheet(connection: AndroidCoachInkConnection, close: () -> Unit) {
    val page by connection.page.collectAsStateWithLifecycle()
    val submitted by connection.lastSend.collectAsStateWithLifecycle()
    val current = page
    AlertDialog(onDismissRequest = close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).padding(16.dp),
        title = { Text(stringResource(R.string.halo11_coach_ink_mirror)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current == null) Text(stringResource(R.string.halo11_coach_ink_mirror_ended))
            else key(current.version) {
                Text(current.content.problem, style = MaterialTheme.typography.headlineSmall)
                Text(current.content.phase, style = MaterialTheme.typography.titleMedium)
                current.content.steps.forEach { Text(it) }
                if (current.content.acceptsWriting) {
                    var ink by remember { mutableStateOf("") }
                    var tooLong by remember { mutableStateOf(false) }
                    Text(stringResource(R.string.halo11_coach_ink_tablet_write))
                    CanvasInkSurface(initial = "", aspectMilli = 2100, onChanged = { ink = it; tooLong = false })
                    val document = remember(ink) { InkCodec.decode(ink) }
                    TextButton(enabled = document?.strokes?.isNotEmpty() == true, onClick = {
                        if (document != null) tooLong = !connection.send(current, document)
                    }) { Text(stringResource(R.string.halo11_coach_ink_tablet_send)) }
                    if (tooLong || submitted == false) Text(stringResource(R.string.halo11_coach_ink_tablet_rejected))
                    else if (submitted == true) Text(stringResource(R.string.halo11_coach_ink_tablet_sent))
                }
            }
        } },
        confirmButton = { TextButton(close) { Text(stringResource(R.string.halo11_coach_ink_mirror_stop)) } })
}
