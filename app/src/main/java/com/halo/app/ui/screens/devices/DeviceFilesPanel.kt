package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.workspace.WorkspaceContinueButton
import com.halo.sdk.runtime.wearables.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Exact selected device files become ordinary Library files and use its existing actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceFilesPanel(deviceId: String, ready: Boolean) {
    val owner = Graph.hostGraph.captureCoordinator?.files
    val state by (owner?.state ?: remember { MutableStateFlow(DeviceMediaState()) }).collectAsStateWithLifecycle()
    val local = state.takeIf { it.deviceId == deviceId } ?: remember(deviceId) { DeviceMediaState() }
    val context = LocalContext.current
    val wifiState by (Graph.hostGraph.captureCoordinator?.fileWifiState ?: remember {
        MutableStateFlow(com.halo.drivers.heycyan.wifi.WifiConnectionState.DISCONNECTED)
    }).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by remember(deviceId, local.sourceVersion) { mutableStateOf(emptySet<String>()) }
    var page by remember(deviceId, local.sourceVersion) { mutableIntStateOf(0) }
    var kind by remember(deviceId) { mutableStateOf("ALL") }
    var failed by remember(deviceId) { mutableStateOf(false) }
    var wifiDisabled by remember(deviceId) { mutableStateOf(false) }
    val wifiSettings = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        wifiDisabled = context.getSystemService(android.net.wifi.WifiManager::class.java)?.isWifiEnabled != true
    }
    fun run(block: suspend () -> Unit) {
        failed = false
        wifiDisabled = false
        scope.launch { try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                wifiDisabled = error is com.halo.app.capture.HeyCyanWifiDisabledException
                failed = !wifiDisabled
            } }
    }
    val wifiPermission = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.NEARBY_WIFI_DEVICES
        else android.Manifest.permission.ACCESS_FINE_LOCATION
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) run { owner?.refresh(deviceId) } else failed = true
    }
    fun browse() {
        if (context.checkSelfPermission(wifiPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            run { owner?.refresh(deviceId) }
        else permission.launch(wifiPermission)
    }
    val files = local.files.filter { kind == "ALL" || it.file.kind == kind }
    val pages = maxOf(1, (files.size + 24) / 25)
    val visible = files.drop(page.coerceAtMost(pages - 1) * 25).take(25)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.device_files_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.device_files_help), style = MaterialTheme.typography.bodySmall)
        if (!local.busy && wifiState == com.halo.drivers.heycyan.wifi.WifiConnectionState.CONNECTED)
            Text(stringResource(R.string.a2_wifi_ready), style = MaterialTheme.typography.bodySmall)
        if (wifiDisabled) {
            Text(stringResource(R.string.a2_wifi_disabled))
            OutlinedButton(onClick = { wifiSettings.launch(android.content.Intent(
                if (android.os.Build.VERSION.SDK_INT >= 29) android.provider.Settings.Panel.ACTION_WIFI
                else android.provider.Settings.ACTION_WIFI_SETTINGS)) }) { Text(stringResource(R.string.a2_wifi_settings)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { browse() }, enabled = ready && owner != null && !state.busy,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.device_files_browse)) }
            TextButton(onClick = { com.halo.app.ui.shell.HaloScreenLauncher.dispatch(context, "notebook") },
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.device_files_library)) }
        }
        if (local.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(when (local.phase) {
                "CONNECTING" -> R.string.device_files_connecting
                "LISTING" -> R.string.device_files_listing
                "SAVING" -> R.string.device_files_saving
                "CANCELLING" -> R.string.device_files_cancelling
                else -> R.string.device_files_downloading
            }))
            if (local.currentItem > 0) Text(stringResource(R.string.device_files_progress, local.currentItem, local.totalItems,
                android.text.format.Formatter.formatShortFileSize(context, local.bytesReceived)))
            OutlinedButton(onClick = { local.operationId?.let { owner?.cancel(deviceId, it) } },
                enabled = local.phase != "CANCELLING", modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.device_files_cancel)) }
        }
        if (!wifiDisabled && (failed || local.phase == "FAILED")) Text(stringResource(R.string.device_files_failed), color = MaterialTheme.colorScheme.error)
        if (local.phase == "CANCELLED") Text(stringResource(R.string.device_files_cancelled))
        if (local.phase == "PARTIAL") Text(stringResource(R.string.device_files_partial, local.failures), color = MaterialTheme.colorScheme.error)
        local.failedFiles.forEach { Text(it.name, color = MaterialTheme.colorScheme.error) }
        if (local.phase == "COMPLETED") Text(stringResource(R.string.device_files_complete, local.saved.size))
        if (local.files.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to R.string.device_files_all, "PHOTO" to R.string.device_files_photos,
                    "VIDEO" to R.string.device_files_videos, "AUDIO" to R.string.device_files_audio, "UNKNOWN" to R.string.device_files_other).forEach { (value, label) ->
                    FilterChip(selected = kind == value, onClick = { kind = value; page = 0 }, label = { Text(stringResource(label)) })
                }
            }
            visible.forEach { row ->
                val fileSize = row.file.size
                val selectable = fileSize == null || fileSize <= DeviceMediaManager.MAX_FILE_BYTES
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = row.token in selected, onCheckedChange = { checked -> selected =
                        if (checked) selected + row.token else selected - row.token },
                        enabled = !state.busy && selectable && (row.token in selected || selected.size < DeviceMediaManager.MAX_SELECTION))
                    Column(Modifier.weight(1f)) {
                        Text(row.file.name, maxLines = 2)
                        Text(row.file.size?.let { android.text.format.Formatter.formatShortFileSize(context, it) }
                            ?: stringResource(R.string.device_files_unknown_size), style = MaterialTheme.typography.bodySmall)
                        if (!selectable) Text(stringResource(R.string.device_files_too_large), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (pages > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text(stringResource(R.string.device_files_previous)) }
                Text("${page.coerceAtMost(pages - 1) + 1} / $pages", Modifier.padding(top = 12.dp))
                TextButton(onClick = { page++ }, enabled = page < pages - 1) { Text(stringResource(R.string.device_files_next)) }
            }
            Button(onClick = { val tokens = selected; val version = local.sourceVersion
                run { owner?.importSelected(deviceId, version, tokens) } },
                enabled = selected.isNotEmpty() && !state.busy && ready, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.device_files_import, selected.size))
            }
            Text(stringResource(R.string.device_files_limits), style = MaterialTheme.typography.bodySmall)
        } else if (local.phase == "READY") Text(stringResource(R.string.device_files_empty))
        if (local.saved.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.device_files_saved), style = MaterialTheme.typography.titleSmall)
            local.saved.forEach { saved ->
                Text(saved.name)
                if (saved.inTrash) Text(stringResource(R.string.device_files_in_trash))
                else WorkspaceContinueButton(saved.objectId, saved.sourceVersion, enabled = !local.busy)
                if (saved.mimeType == "application/octet-stream") Text(stringResource(R.string.device_files_original), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.device_files_local_only), style = MaterialTheme.typography.bodySmall)
    }
}

/** Shared prompt also serves file operations started by the assistant from another screen. */
@Composable
fun HeyCyanWifiPeerDialog() {
    val capture = Graph.hostGraph.captureCoordinator ?: return
    val peers by capture.wifiPeerSelection.collectAsStateWithLifecycle()
    val operation by capture.files.state.collectAsStateWithLifecycle()
    val deviceId = peers.deviceId ?: return
    if (operation.deviceId != deviceId || !operation.busy) return
    fun cancelSelection() { operation.operationId?.let { capture.files.cancel(deviceId, it) } }
    AlertDialog(onDismissRequest = { cancelSelection() },
        title = { Text(stringResource(R.string.a2_wifi_choose)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.a2_wifi_choose_help))
            if (peers.peers.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.a2_wifi_searching)) }
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                peers.peers.forEach { peer -> TextButton(onClick = { capture.selectWifiPeer(deviceId, peer.address) },
                    modifier = Modifier.fillMaxWidth()) {
                    Column { Text(peer.name); Text(peer.address, style = MaterialTheme.typography.bodySmall) }
                } }
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { cancelSelection() }) { Text(stringResource(R.string.device_files_cancel)) } })
}
