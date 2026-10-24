package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.host.android.ink.AndroidInkTabletConnection
import com.halo.host.ink.InkSenderState
import com.halo.host.ink.InkLocalSaveState
import com.halo.host.ink.InkDocumentIdentity
import com.halo.sdk.runtime.ink.*

/** Uses the same real stylus/palm/eraser editor as local Canvas, with an independent local draft. */
@Composable
internal fun RemoteInkTabletDialog(connection: AndroidInkTabletConnection, onDismiss: () -> Unit) {
    val original = remember(connection) { InkCodec.encode(connection.initial) }
    var current by remember(connection) { mutableStateOf(original) }
    var sharing by remember(connection) { mutableStateOf(connection.initial == connection.offer.document) }
    val localState by connection.localState.collectAsState()
    val localSaved = localState == InkLocalSaveState.SAVED
    val localFailure = localState == InkLocalSaveState.FAILED
    val network by connection.sender.state.collectAsState()
    val confirmed by connection.sender.confirmedDocumentSha256.collectAsState()
    val currentSha256 = remember(current) { InkDocumentIdentity.encodedSha256(current) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(connection, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) connection.disconnect()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); connection.close() }
    }
    fun stop() { connection.disconnect(); if (localSaved) onDismiss() }
    AlertDialog(onDismissRequest = ::stop, properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth().padding(16.dp),
        title = { Text(stringResource(R.string.remote_ink_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CanvasInkSurface(original, onChanged = {
                current = it
                InkCodec.decode(it)?.let { document -> connection.edit(document, sharing) }
            }, selectionOwnerId = connection.offer.session.boardId, selectionPageId = connection.offer.session.documentId,
                onSelectionChanged = { selection -> if (selection != null && sharing) connection.sender.selection(selection) })
            Text(stringResource(when {
                localFailure -> R.string.remote_ink_local_failed
                !localSaved -> R.string.remote_ink_local_saving
                network == InkSenderState.CONFLICT -> R.string.remote_ink_conflict
                network == InkSenderState.DISCONNECTED -> R.string.remote_ink_local_fallback
                !sharing -> R.string.remote_ink_recovery
                confirmed == currentSha256 -> R.string.remote_ink_saved
                else -> R.string.remote_ink_waiting
            }))
            if (!sharing && network != InkSenderState.DISCONNECTED && network != InkSenderState.CONFLICT) {
                TextButton(onClick = {
                    InkCodec.decode(current)?.let { sharing = true; connection.edit(it, true) }
                }) { Text(stringResource(R.string.remote_ink_sync)) }
            }
        } },
        confirmButton = { Button(onClick = ::stop) { Text(stringResource(R.string.remote_ink_stop)) } })
}
