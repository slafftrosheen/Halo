package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Choose 2–6 images. A note can contribute more than one."),
                    style = MaterialTheme.typography.bodySmall)
                when {
                    candidates == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    failure -> Text(uiText("Could not load image attachments."),
                        color = MaterialTheme.colorScheme.error)
                    candidates!!.isEmpty() -> Text(uiText("No image attachments in selected cards."))
                    else -> candidates!!.forEach { (nodeId, source) ->
                        val id = source.sourceKey
                        val active = id in checked
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = active,
                                onCheckedChange = { enabled ->
                                    checked = if (enabled && checked.size < 6) checked + id
                                        else if (!enabled) checked - id else checked
                                }, enabled = active || checked.size < 6)
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
                Text(uiText("Selected") + ": " + selected.size + "/6",
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { onChoose(selected) },
                enabled = !failure && selected.size in 2..6) { Text(uiText("Continue to preview")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}
