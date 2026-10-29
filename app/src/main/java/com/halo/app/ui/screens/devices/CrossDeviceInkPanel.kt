package com.halo.app.ui.screens.devices

import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ink.CrossDeviceInkController
import com.halo.host.android.ink.AndroidInkIdentity
import com.halo.host.ink.*
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activation is enforced again in the owner; this UI cannot promote qualification. */
@Composable
internal fun CrossDeviceInkPanel(owner: CrossDeviceInkController) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sessions by owner.sessions.collectAsState()
    val connections by owner.connections.collectAsState()
    var devices by remember { mutableStateOf(emptyList<com.halo.sdk.runtime.embodiment.DeviceRecord>()) }
    var pending by remember { mutableStateOf<InkPairingBundle?>(null) }
    var failed by remember { mutableStateOf(false) }
    var refreshed by remember { mutableIntStateOf(0) }
    var exportSession by remember { mutableStateOf<String?>(null) }
    var tablet by remember { mutableStateOf<com.halo.host.android.ink.AndroidInkTabletConnection?>(null) }
    LaunchedEffect(refreshed, sessions) { devices = owner.devices() }
    val imported = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && owner.enabled) scope.launch {
            pending = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        val bytes = it.readBytesBounded(InkPairingBundleCodec.MAX_BYTES)
                        InkPairingBundleCodec.decode(bytes)
                    }
                }.getOrNull()
            }
            failed = pending == null
        }
    }
    val exported = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && owner.enabled) scope.launch {
            failed = withContext(Dispatchers.IO) {
                runCatching {
                    val identity = AndroidInkIdentity(InkActivationPolicy { owner.enabled })
                    val certificate = identity.certificate()
                    val pen = InputDevice.getDeviceIds().asSequence().mapNotNull(InputDevice::getDevice)
                        .firstOrNull { it.supportsSource(InputDevice.SOURCE_STYLUS) }
                    val metrics = context.resources.displayMetrics
                    val endpoint = InkEndpoint(InkPairingBundle.deviceId(certificate),
                        pressure = pen?.getMotionRange(MotionEvent.AXIS_PRESSURE, InputDevice.SOURCE_STYLUS) != null,
                        tilt = pen?.getMotionRange(MotionEvent.AXIS_TILT, InputDevice.SOURCE_STYLUS) != null,
                        width = metrics.widthPixels.coerceIn(1, 65535), height = metrics.heightPixels.coerceIn(1, 65535),
                        rotationDegrees = (view.display?.rotation ?: 0) * 90)
                    val bundle = InkPairingBundle(android.os.Build.MODEL.take(80), endpoint, certificate)
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(InkPairingBundleCodec.encode(bundle)) }
                }.isFailure
            }
        }
    }
    val invitationExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val sessionId = exportSession
        if (uri != null && sessionId != null && owner.enabled) scope.launch {
            failed = runCatching {
                val session = owner.sessions.value.first { it.sessionId == sessionId }
                val pin = owner.devices().first { it.identity.deviceId == session.inputOwner }.inkAssociation!!.certificateSha256
                val identity = AndroidInkIdentity(InkActivationPolicy { owner.enabled })
                val address = withContext(Dispatchers.IO) {
                    java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                        .flatMap { it.inetAddresses.toList() }.first { it is java.net.Inet4Address && it.isSiteLocalAddress }
                }
                val tls = withContext(Dispatchers.IO) { identity.context(setOf(pin)) }
                val port = requireNotNull(owner.listen(sessionId, tls, address, 0,
                    com.halo.app.di.ManualDependencyGraph.infra.scope))
                withContext(Dispatchers.IO) {
                    val invitation = InkConnectionInvitation(sessionId, requireNotNull(address.hostAddress), port, identity.fingerprint())
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(invitation.encode()) }
                }
            }.isFailure
            if (failed) owner.stop(sessionId)
            exportSession = null
        }
    }
    val invitationImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && owner.enabled) scope.launch {
            failed = runCatching {
                val invitation = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        requireNotNull(InkConnectionInvitation.decode(it.readBytesBounded(InkConnectionInvitation.MAX_BYTES)))
                    }
                }
                val pins = owner.devices().mapNotNull { it.inkAssociation?.certificateSha256 }.toSet()
                tablet = com.halo.host.android.ink.AndroidInkTabletConnection.open(context.applicationContext,
                    invitation, pins, com.halo.app.di.ManualDependencyGraph.infra.scope, InkActivationPolicy { owner.enabled })
            }.isFailure
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.remote_ink_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (owner.enabled) R.string.remote_ink_pair_help else R.string.remote_ink_pending),
                style = MaterialTheme.typography.bodyMedium)
            if (owner.enabled) {
                TextButton(onClick = { exported.launch("halo-drawing-device.halopair") }) { Text(stringResource(R.string.remote_ink_export)) }
                TextButton(onClick = { imported.launch(arrayOf("application/octet-stream", "*/*")) }) { Text(stringResource(R.string.remote_ink_import)) }
                TextButton(onClick = { invitationImport.launch(arrayOf("application/octet-stream", "*/*")) }) { Text(stringResource(R.string.remote_ink_connect_file)) }
            }
                devices.forEach { device ->
                    Text(device.identity.displayName, style = MaterialTheme.typography.titleSmall)
                    val caps = requireNotNull(device.inkAssociation).endpoint
                    val basicAbilityLabel = stringResource(R.string.remote_ink_basic)
                    val abilities = buildList {
                        if (caps.pressure) add(stringResource(R.string.remote_ink_pressure))
                        if (caps.tilt) add(stringResource(R.string.remote_ink_tilt))
                        if (caps.eraser) add(stringResource(R.string.remote_ink_eraser))
                        if (caps.hover) add(stringResource(R.string.remote_ink_hover))
                    }
                    Text(abilities.joinToString(" · ").ifEmpty { basicAbilityLabel }, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.remote_ink_geometry, caps.width, caps.height, caps.rotationDegrees), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { scope.launch { owner.forget(device.identity.deviceId); refreshed++ } }) {
                        Text(stringResource(R.string.remote_ink_forget))
                    }
                }
            sessions.forEach { session ->
                Text(stringResource(if (connections[session.sessionId] == InkReceiverState.CONNECTED)
                    R.string.remote_ink_connected else R.string.remote_ink_waiting))
                Button(onClick = { scope.launch { owner.stop(session.sessionId) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.remote_ink_stop))
                }
                if (owner.enabled && connections[session.sessionId] == null) {
                    TextButton(onClick = { exportSession = session.sessionId; invitationExport.launch("halo-drawing-session.haloconnect") }) {
                        Text(stringResource(R.string.remote_ink_session_file))
                    }
                }
            }
            if (failed) Text(stringResource(R.string.remote_ink_pair_failed), color = MaterialTheme.colorScheme.error)
        }
    }
    pending?.let { bundle ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(stringResource(R.string.remote_ink_trust_title)) },
            text = { Text(stringResource(R.string.remote_ink_trust_body, bundle.displayName)) },
            confirmButton = { TextButton(onClick = { scope.launch {
                failed = !owner.associateConfirmed(bundle); pending = null; refreshed++
            } }) { Text(stringResource(R.string.remote_ink_trust)) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    tablet?.let { connection -> com.halo.app.ui.screens.notebook.RemoteInkTabletDialog(connection) { connection.close(); tablet = null } }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
