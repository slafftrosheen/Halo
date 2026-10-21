package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.data.workspace.CanvasStitchSource
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException

/**
 * A Notebook card may hold many images. Choose exact attachment IDs here, before
 * any preview decode. Loaded only while this user-requested dialog is visible.
 */
@Composable
internal fun CanvasStitchSourcePicker(
    viewModel: CanvasEditorViewModel,
    nodeIds: Set<String>,
    onChoose: (List<Pair<String, CanvasStitchSource>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var failure by remember(nodeIds) { mutableStateOf(false) }
    val candidates by produceState<List<Pair<String, CanvasStitchSource>>?>(null, viewModel, nodeIds) {
        try { value = viewModel.stitchCandidates(nodeIds) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true; value = emptyList() }
    }
    var checked by remember(nodeIds) { mutableStateOf<Set<String>>(emptySet()) }
    val selected = candidates.orEmpty().filter { it.second.sourceKey in checked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Choose images")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Choose 2–6 images. A note can contribute more than one."),
                    style = MaterialTheme.typography.bodySmall)
                when {
                    candidates == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    failure -> Text(uiText("Could not load image attachments."),
                        color = MaterialTheme.colorScheme.error)
                    candidates!!.isEmpty() -> Text(uiText("No image attachments in selected cards."))
                    else -> {
                        LazyColumn(Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(candidates!!, key = { it.second.sourceKey }) { (_, source) ->
                                val id = source.sourceKey
                                val active = id in checked
                                val canChoose = active || checked.size < 6
                                Row(Modifier.fillMaxWidth().toggleable(
                                        value = active, enabled = canChoose, role = Role.Checkbox,
                                        onValueChange = { enabled ->
                                            checked = if (enabled && checked.size < 6) checked + id
                                                else if (!enabled) checked - id else checked
                                        }),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = active, onCheckedChange = null,
                                        enabled = canChoose)
                                    CanvasStitchSourceThumbnail(source)
                                    Column(Modifier.weight(1f)) {
                                        Text(source.title.take(80), style = MaterialTheme.typography.bodyMedium)
                                        val detail = when (source) {
                                            is CanvasStitchSource.WorkspaceImage -> uiText("Imported image")
                                            is CanvasStitchSource.NoteImage ->
                                                (source.attachment.caption?.take(80)?.takeIf { it.isNotBlank() }
                                                    ?: uiText("Notebook attachment"))
                                        }
                                        Text(detail, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(uiText("Selected") + ": " + selected.size + "/6",
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            com.halo.app.ui.components.HaloActionButton(onClick = { onChoose(selected) },
                enabled = !failure && selected.size in 2..6) { Text(uiText("Continue to preview")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}

@Composable
private fun CanvasStitchSourceThumbnail(source: CanvasStitchSource) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, source.sourceKey) {
        try { value = withContext(Dispatchers.IO) { source.decode(context, preview = true, thumbnail = true) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null,
            modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
    } else {
        Surface(Modifier.size(52.dp), tonalElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) {
                Text(uiText("Image"), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
