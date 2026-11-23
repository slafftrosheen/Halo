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
import com.halo.app.ui.screens.notebook.InkMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
internal fun StudioInkDialog(service: StudioService, session: StudioInkSession,
    onSaveCopy: () -> Unit, onDismiss: () -> Unit, initialMode: InkMode = InkMode.DRAW,
    reference: @Composable BoxScope.() -> Unit = {}) {
    // Freeze the input owner's initial state. Background Room emissions must not reset a contact in progress.
    val scope = rememberCoroutineScope()
    val project = session.initial
    val layer = requireNotNull(project.session.current.layers.find { it.id == session.layerId })
    var previewInk by remember(session) { mutableStateOf(layer.ink) }
    val failed by session.failed.collectAsStateWithLifecycle()
    var selectionJob by remember(session) { mutableStateOf<Job?>(null) }
    var previewFailed by remember(session) { mutableStateOf(false) }
    val background by produceState<Bitmap?>(null, session) {
        try { value = service.inkBackdrop(project.session.current, layer) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { previewFailed = true }
    }
    DisposableEffect(session) { onDispose { selectionJob?.cancel(); service.detachInkSelectionOwner(session.ownerToken) } }
    // The renderer may still hold published pixels after this composition leaves.
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(layer.name) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.studio_ink_help), style = MaterialTheme.typography.bodySmall)
            when {
                failed -> Text(stringResource(R.string.studio_failed_ink), color = MaterialTheme.colorScheme.error)
                previewFailed -> Text(stringResource(R.string.studio_preview_error), color = MaterialTheme.colorScheme.error)
                background == null -> CircularProgressIndicator()
                else -> Box {
                    CanvasInkSurface(initial = layer.ink, onChanged = { encoded ->
                        session.submit(encoded)
                        if (layer.symmetry != null) previewInk = encoded
                    }, background = background?.asImageBitmap(),
                    selectionOwnerId = project.id, selectionPageId = layer.id, selectionBaseRevision = project.revision.toString(),
                    strokeColor = Color(layer.color), strokeWidthFraction = service.inkWidthFraction(project.session.current, layer),
                    initialMode = initialMode,
                    hardPencil = layer.pencil,
                    showBrushes = true,
                    onSelectionChanged = { context ->
                        selectionJob?.cancel()
                        if (context == null) service.clearSelection(project.id, session.ownerToken)
                        else selectionJob = scope.launch {
                            if (!session.awaitPersisted() || !service.publishSelection(project.id, layer.id, project.revision,
                                    session.ownerToken, context, session.persistedRevision))
                                service.clearSelection(project.id, session.ownerToken)
                        }
                    })
                    reference()
                }
            }
            if (layer.symmetry != null) StudioSymmetryPreview(service, project.session.current, layer.copy(ink = previewInk))
        }
    }, confirmButton = {
        if (failed) TextButton(onClick = onSaveCopy) { Text(stringResource(R.string.studio_save_as)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_close)) } })
}
