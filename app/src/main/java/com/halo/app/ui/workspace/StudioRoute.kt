package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.R
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.flow.map

/** Host composition only: the route assembles existing camera/provider/navigation owners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioRoute(projectId: String?, sourceId: String?, attachmentId: String?, returnCanvasId: String?,
    savedState: SavedStateHandle, onBack: () -> Unit, onOpenFile: (String) -> Unit,
    onOpenCanvas: (String, String?) -> Unit, sourceVersion: String? = null, handoffId: String? = null) {
    val model: StudioViewModel = viewModel(key = "studio", factory = StudioViewModel.Factory(Graph.studioService,
        Graph.workspaceFileRepository, Graph.workspaceImageService, Graph.notebookAssetRepository, projectId, savedState))
    val state by model.state.collectAsStateWithLifecycle()
    val camera = rememberStudioCamera(model, Graph.captures, Graph.notebookAssetRepository)
    var pendingOutputId by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultName = stringResource(R.string.studio_new_project)
    val aiTitle = stringResource(R.string.studio_ai)
    val uiRequest by Graph.studioService.uiRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(uiRequest?.id, state.busy, camera.busy, state.aiReview) {
        val request = uiRequest
        if (request != null && !state.busy && !camera.busy && !state.aiReview) {
            Graph.studioService.uiRequests.claim(request.id)?.let { model.acceptUiRequest(it, aiTitle) }
        }
    }
    LaunchedEffect(model, sourceId, attachmentId, sourceVersion, handoffId) {
        sourceId?.let { id ->
            val source = Graph.workspaceRepository.get(WorkspaceObjectId(id))
            if (source == null) model.reportError()
            else model.openSource(id, attachmentId, source.title.take(120).ifBlank { defaultName }, sourceVersion, handoffId)
        }
    }
    LaunchedEffect(model) { model.openedCanvases.collect { id -> onOpenCanvas(id, null) } }
    StudioScreen(model, onBack, { onOpenFile(it.objectId.value) }, { output ->
        if (returnCanvasId != null) model.insertOutput(returnCanvasId, output) { node -> onOpenCanvas(returnCanvasId, node) }
        else pendingOutputId = output.objectId.value
    }, camera.take, { project -> model.prepareAi(project, aiTitle) },
        onRecoverCamera = camera.retry.takeIf { camera.recoveryAvailable }, externalBusy = camera.busy,
        onOpenCanvas = { id -> onOpenCanvas(id, null) })
    if (state.aiReview) WorkspaceImageDialog(model.imageStudio, saveLabel = stringResource(R.string.studio_keep_copy),
        scopeLabel = stringResource(R.string.studio_ai_copy_scope), dismissLabel = stringResource(R.string.studio_discard_proposal)) { model.dismissAi() }
    pendingOutputId?.let { outputId ->
        val canvases by remember {
            Graph.workspaceRepository.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.CANVAS, WorkspaceObjectType.ARTIFACT)))
                .map { rows -> rows.filter { it.type == WorkspaceObjectType.CANVAS || it.contentRef.startsWith("canvas:") } }
        }.collectAsStateWithLifecycle(emptyList())
        val output by produceState<com.halo.app.data.workspace.WorkspaceFile?>(null, outputId) {
            value = Graph.workspaceFileRepository.getFile(WorkspaceObjectId(outputId))
        }
        ModalBottomSheet(onDismissRequest = { if (!state.busy) pendingOutputId = null }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.studio_insert_canvas), style = MaterialTheme.typography.titleMedium)
                if (state.error) Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                if (canvases.isEmpty()) Text(stringResource(R.string.studio_no_canvas))
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(canvases, key = { it.id.value }) { canvas ->
                        TextButton(onClick = { output?.let { file -> model.insertOutput(canvas.id.value, file) { node ->
                            pendingOutputId = null; onOpenCanvas(canvas.id.value, node)
                        } } }, enabled = !state.busy && output != null, modifier = Modifier.fillMaxWidth()) { Text(canvas.title) }
                    }
                }
            }
        }
    }
}
