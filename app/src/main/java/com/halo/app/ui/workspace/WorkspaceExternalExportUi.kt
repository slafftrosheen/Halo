package com.halo.app.ui.workspace

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.components.HaloAlertDialog as AlertDialog
import com.halo.app.data.workspace.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val LocalWorkspaceExport = staticCompositionLocalOf<(suspend (WorkspaceFile) -> Unit)?> { null }

@Composable
internal fun WorkspaceExportButton(file: WorkspaceFile) {
    val request = LocalWorkspaceExport.current ?: return
    val scope = rememberCoroutineScope()
    var failed by remember(file.objectId) { mutableStateOf(false) }
    TextButton(onClick = { scope.launch {
        try { request(file); failed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.external_export_choose)) }
    if (failed) Text(stringResource(R.string.external_export_start_failed), color = MaterialTheme.colorScheme.error)
}

/** System UI handoff stays at the shell, so closing an editor cannot lose an outstanding result. */
@Composable
internal fun WorkspaceExternalExportHost(owner: WorkspaceExternalExportSession, permitted: Boolean) {
    val state by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var localFailure by remember { mutableStateOf(false) }
    var loadFailure by remember { mutableStateOf(false) }
    val registry = requireNotNull(LocalActivityResultRegistryOwner.current).activityResultRegistry
    var registered by remember { mutableStateOf<Pair<String, androidx.activity.result.ActivityResultLauncher<Intent>>?>(null) }
    var launchRequested by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(owner) {
        try { owner.load(); owner.abandonUnlaunchedPicker() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailure = true }
    }
    DisposableEffect(owner) { onDispose { owner.abandonUnlaunchedPicker() } }
    val registrationToken = state?.pickerToken
    DisposableEffect(registry, registrationToken) {
        val token = registrationToken
        if (token == null) onDispose { }
        else {
            // A restored result retains its original registration key and token, even if a newer picker exists.
            val launcher = registerWorkspaceExportPicker(registry, token) { result ->
                val uri = result.data?.data?.takeIf { result.resultCode == android.app.Activity.RESULT_OK }
                if (uri?.scheme == "content") {
                    val flags = result.data!!.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    try { context.contentResolver.takePersistableUriPermission(uri, flags) }
                    catch (_: Exception) { /* Later verification reports lost access honestly. */ }
                }
                owner.picked(uri, token)
            }
            registered = token to launcher
            onDispose { launcher.unregister(); if (registered?.first == token) registered = null }
        }
    }
    LaunchedEffect(launchRequested, registered) {
        val request = launchRequested ?: return@LaunchedEffect
        val (token, launcher) = registered?.takeIf { it.first == request } ?: return@LaunchedEffect
        val selected = owner.state.value?.takeIf { it.pickerToken == token && it.phase == ExternalExportPhase.PICKER }
            ?: return@LaunchedEffect
        launchRequested = null // Restore never automatically opens or replays a picker.
        try {
            check(permitted && owner.isAllowed() && owner.markPickerLaunched(token))
            launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(selected.mime).putExtra(Intent.EXTRA_TITLE, selected.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            localFailure = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { owner.picked(null, token); localFailure = true }
    }
    if (!permitted) return
    fun dismiss() { scope.launch {
        try { owner.dismiss(); localFailure = false; loadFailure = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { localFailure = true }
    } }
    if (loadFailure) {
        AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.external_export_title)) },
            text = { Text(stringResource(R.string.external_export_unknown)) },
            confirmButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.external_export_close)) } })
        return
    }
    val current = state ?: return
    AlertDialog(onDismissRequest = { if (!current.busy) dismiss() },
        title = { Text(stringResource(R.string.external_export_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(current.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when (current.phase) {
                ExternalExportPhase.CHOOSE, ExternalExportPhase.PICKER -> R.string.external_export_explain
                ExternalExportPhase.WRITING -> R.string.external_export_writing
                ExternalExportPhase.VERIFYING -> R.string.external_export_verifying
                ExternalExportPhase.VERIFIED -> R.string.external_export_verified
                ExternalExportPhase.CANCELLED -> R.string.external_export_cancelled
                ExternalExportPhase.UNKNOWN -> R.string.external_export_unknown
                ExternalExportPhase.FAILED -> when (current.outcome) {
                    ExternalExportOutcome.SOURCE_UNAVAILABLE -> R.string.external_export_source
                    ExternalExportOutcome.ACCESS_DENIED -> R.string.external_export_access
                    else -> R.string.external_export_failed
                }
            }))
            if (current.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (localFailure) Text(stringResource(R.string.external_export_open_failed), color = MaterialTheme.colorScheme.error)
            if (current.pickerCancelled && current.phase != ExternalExportPhase.CANCELLED) Text(stringResource(R.string.external_export_cancelled))
            if (current.destination != null && !current.busy) {
                TextButton(onClick = {
                    try {
                        val uri = android.net.Uri.parse(current.destination)
                        openExportOutsideHalo(context, uri, current.mime, current.name, owner::isAllowed)
                        localFailure = false
                    } catch (_: Exception) { localFailure = true }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(
                    if (current.phase == ExternalExportPhase.VERIFIED) R.string.external_export_open else R.string.external_export_inspect)) }
                if (current.phase != ExternalExportPhase.VERIFIED) TextButton(onClick = owner::verify,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.external_export_check)) }
            }
            current.priorDestinations.forEach { earlier -> TextButton(onClick = {
                try { openExportOutsideHalo(context, android.net.Uri.parse(earlier), current.mime, current.name, owner::isAllowed); localFailure = false }
                catch (_: Exception) { localFailure = true }
            }, enabled = !current.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.external_export_prior)) } }
        } },
        confirmButton = { if (!current.busy && current.phase != ExternalExportPhase.VERIFIED) TextButton(onClick = { scope.launch {
            try { launchRequested = owner.picking().pickerToken; localFailure = false }
            catch (cancelled: CancellationException) { owner.abandonUnlaunchedPicker(); throw cancelled }
            catch (_: Exception) { localFailure = true }
        } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.external_export_choose)) } },
        dismissButton = { TextButton(onClick = ::dismiss, enabled = !current.busy,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.external_export_close)) } })
}

/** An exported copy is explicitly opened in another app, never reimported through Halo's VIEW filter. */
internal fun openExportOutsideHalo(context: android.content.Context, uri: android.net.Uri, mime: String, name: String, allowed: () -> Boolean) {
    check(allowed())
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(name, uri) }
    val targets = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.exported }
        .map { Intent(intent).setComponent(android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
        .distinctBy { it.component }
    check(targets.isNotEmpty())
    check(allowed())
    context.startActivity(Intent.createChooser(targets.first(), context.getString(R.string.external_export_open))
        .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray()))
}

/** Stable per-launch identity; callbacks never consult a mutable current-picker token. */
internal fun registerWorkspaceExportPicker(registry: androidx.activity.result.ActivityResultRegistry, token: String,
    onResult: (androidx.activity.result.ActivityResult) -> Unit): androidx.activity.result.ActivityResultLauncher<Intent> =
    registry.register("halo.workspace.export.$token", ActivityResultContracts.StartActivityForResult(), onResult)
