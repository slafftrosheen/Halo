package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.studio.StudioService
import com.halo.app.ui.screens.notebook.CanvasInkSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
internal fun StudioInkDialog(service: StudioService, session: StudioInkSession,
    onSaveCopy: () -> Unit, onDismiss: () -> Unit) {
    // Freeze the input owner's initial state. Background Room emissions must not reset a contact in progress.
    val scope = rememberCoroutineScope()
    val project = session.initial
    val layer = requireNotNull(project.session.current.layers.find { it.id == session.layerId })
    val failed by session.failed.collectAsStateWithLifecycle()
    var selectionJob by remember(session) { mutableStateOf<Job?>(null) }
    var previewFailed by remember(session) { mutableStateOf(false) }
    val background by produceState<Bitmap?>(null, session) {
        try { value = service.inkBackdrop(project.session.current, layer) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { previewFailed = true }
    }
    DisposableEffect(session) { onDispose { selectionJob?.cancel(); service.detachInkSelectionOwner(session.ownerToken) } }
    DisposableEffect(background) { onDispose { background?.takeUnless { it.isRecycled }?.recycle() } }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(layer.name) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.studio_ink_help), style = MaterialTheme.typography.bodySmall)
            when {
                failed -> Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                previewFailed -> Text(stringResource(R.string.studio_preview_error), color = MaterialTheme.colorScheme.error)
                background == null -> CircularProgressIndicator()
                else -> CanvasInkSurface(initial = layer.ink, onChanged = session::submit, background = background?.asImageBitmap(),
                    selectionOwnerId = project.id, selectionPageId = layer.id, selectionBaseRevision = project.revision.toString(),
                    strokeColor = Color(layer.color), strokeWidthFraction = service.inkWidthFraction(project.session.current, layer),
                    onSelectionChanged = { context ->
                        selectionJob?.cancel()
                        if (context == null) service.clearSelection(project.id, session.ownerToken)
                        else selectionJob = scope.launch {
                            if (!session.awaitPersisted() || !service.publishSelection(project.id, layer.id, project.revision, session.ownerToken, context))
                                service.clearSelection(project.id, session.ownerToken)
                        }
                    })
            }
        }
    }, confirmButton = {
        if (failed) TextButton(onClick = onSaveCopy) { Text(stringResource(R.string.studio_save_as)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_close)) } })
}
