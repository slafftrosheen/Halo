package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.R
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.*
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.flow.map

/** Android composition and navigation only. Domain effects stay in the canonical owners. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceFileToolsRoute(objectId: String?, savedState: SavedStateHandle, onBack: () -> Unit,
    onOpenNote: (String) -> Unit, onOpenFile: (String) -> Unit, onOpenStudio: (String?) -> Unit,
    onStudy: (String) -> Unit, onOpenCanvas: (String, String) -> Unit) {
    val model: WorkspaceFileToolsViewModel = viewModel(key = "file-tools", factory = WorkspaceFileToolsViewModel.Factory(Graph.workspaceFileTools, objectId, savedState))
    val state by model.state.collectAsStateWithLifecycle()
    val request by model.tools.requests.pending.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var attachSource by remember { mutableStateOf<WorkspaceFileToolsObject?>(null) }
    LaunchedEffect(request?.id, state.busy, lifecycle) {
        val pending = request
        if (pending != null && !state.busy && lifecycle == Lifecycle.State.RESUMED) model.tools.requests.claim(pending)?.let {
            if (it.objectId == null) model.closeSelection() else model.open(it.objectId)
        }
    }
    WorkspaceFileToolsScreen(model, onBack, onOpenNote, onOpenFile, onOpenStudio, onStudy, { attachSource = it })
    val output = state.output
    if (output != null && attachSource == null) {
        StudioAlertDialog(onDismissRequest = model::dismissOutput, title = { Text(stringResource(R.string.file52_output_saved)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(output.displayName, style = MaterialTheme.typography.titleMedium)
                WorkspaceContinueButton(output.objectId.value, output.version)
                Text(stringResource(R.string.file52_output_source_scope), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.dismissOutput(); onOpenFile(output.objectId.value) }) { Text(stringResource(R.string.file52_open_saved)) }
                    TextButton(onClick = {
                        attachSource = WorkspaceFileToolsObject(output.objectId.value, output.displayName, requireNotNull(output.version), WorkspaceFileToolsKind.FILE, output.mimeType)
                        model.dismissOutput()
                    }) { Text(stringResource(R.string.file52_attach_canvas)) }
                    TextButton(onClick = { model.dismissOutput(); onStudy(output.objectId.value) }) { Text(stringResource(R.string.file52_attach_study)) }
                }
            }
        }, confirmButton = { TextButton(onClick = model::dismissOutput) { Text(stringResource(R.string.file52_done)) } }, dismissButton = {})
    }
    attachSource?.let { source ->
        val canvases by remember(model) { model.tools.workspace.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.CANVAS, WorkspaceObjectType.ARTIFACT)))
            .map { rows -> rows.filter { it.type == WorkspaceObjectType.CANVAS || it.contentRef.startsWith("canvas:") } } }
            .collectAsStateWithLifecycle(emptyList())
        ModalBottomSheet(onDismissRequest = { if (!state.busy) attachSource = null }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.file52_choose_canvas), style = MaterialTheme.typography.titleMedium)
                Text(source.title)
                if (state.error) Text(stringResource(R.string.file52_error), color = MaterialTheme.colorScheme.error)
                if (canvases.isEmpty()) Text(stringResource(R.string.file52_no_canvas))
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(canvases, key = { it.id.value }) { canvas ->
                        TextButton(onClick = { model.attach(canvas.id.value, source) { node -> attachSource = null; onOpenCanvas(canvas.id.value, node) } },
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(canvas.title) }
                    }
                }
            }
        }
    }
}
