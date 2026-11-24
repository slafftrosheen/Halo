package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.notebook.NoteBlock
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.notes.v13.NoteDrawing

@Composable
internal fun LinkedNoteDrawing(block: NoteBlock, onOpen: (String, String) -> Unit) {
    val (canvasId, nodeId) = NoteDrawing.target(block) ?: return
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val loaded by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    if (kids || !loaded) return
    val sheets by produceState<List<String>?>(null, canvasId, nodeId) {
        Graph.notebookDrawingLinks.observe(canvasId, nodeId).collect { value = it }
    }
    var page by remember(canvasId, nodeId) { mutableIntStateOf(0) }
    sheets?.let { ink ->
        if (ink.isEmpty()) Text(uiText("This reference has no target in the workspace."),
            style = MaterialTheme.typography.bodySmall)
        else {
            val visiblePage = page.coerceIn(0, ink.lastIndex)
            CanvasSketchPreview(ink[visiblePage], Modifier.fillMaxWidth().height(140.dp))
            if (ink.size > 1) {
                Text(stringResource(R.string.mk13_sheet_n, visiblePage + 1), style = MaterialTheme.typography.labelMedium)
                Row {
                    TextButton(onClick = { page = visiblePage - 1 }, enabled = visiblePage > 0) {
                        Text(stringResource(R.string.canvas50_previous_page))
                    }
                    TextButton(onClick = { page = visiblePage + 1 }, enabled = visiblePage < ink.lastIndex) {
                        Text(stringResource(R.string.canvas50_next_page))
                    }
                }
            }
            TextButton(onClick = { onOpen(canvasId, nodeId) }) { Text(stringResource(R.string.mk13_go_there)) }
        }
    }
}

@Composable
internal fun NoteDrawingPicker(canvasId: String, saving: Boolean, failed: Boolean, onPick: (String) -> Unit, onBack: () -> Unit) {
    val sketches by produceState<List<CanvasNode>?>(null, canvasId) {
        Graph.notebookDrawingLinks.observeSketches(canvasId).collect { value = it }
    }
    AlertDialog(onDismissRequest = { if (!saving) onBack() },
        title = { Text(stringResource(R.string.mk13_link_drawing)) }, text = {
            val choices = sketches
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                if (saving || choices == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (failed) item { Text(stringResource(R.string.ink_draft_rejected), color = MaterialTheme.colorScheme.error) }
                if (choices?.isEmpty() == true) item { Text(stringResource(R.string.mk13_no_linkable_drawings)) }
                items(choices.orEmpty(), key = CanvasNode::id) { sketch ->
                    TextButton(onClick = { onPick(sketch.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Column {
                            CanvasSketchPreview(sketch.text, Modifier.fillMaxWidth().height(90.dp))
                            Text(stringResource(R.string.mk13_link_this_drawing))
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = onBack, enabled = !saving) { Text(uiText("Back")) }
        })
}
