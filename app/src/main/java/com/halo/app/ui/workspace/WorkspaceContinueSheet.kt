package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.*
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Metadata review captures one version. Every later effect revalidates it through the shared owner. */
@Composable
fun WorkspaceContinueButton(objectId: String, expectedVersion: String? = null, enabled: Boolean = true) {
    var showing by remember(objectId, expectedVersion) { mutableStateOf(false) }
    TextButton(onClick = { showing = true }, enabled = enabled) { Text(stringResource(R.string.link53_continue)) }
    if (showing) WorkspaceContinueSheet(objectId, expectedVersion) { showing = false }
}

@Composable
private fun WorkspaceContinueSheet(objectId: String, expectedVersion: String?, onDismiss: () -> Unit) {
    val owner = Graph.workspaceContinuity
    val scope = rememberCoroutineScope()
    var item by remember(objectId, expectedVersion) { mutableStateOf<WorkspaceContinuityItem?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val canvases by owner.canvases.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(objectId, expectedVersion) {
        try { item = if (expectedVersion == null) owner.describe(objectId) else owner.current(objectId, expectedVersion) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    fun operation(block: suspend (WorkspaceContinuityItem) -> Unit) {
        val snapshot = item ?: return
        if (busy || failed) return
        busy = true
        scope.launch {
            try { block(snapshot) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    WorkspaceContinueDialog(item, canvases, busy, failed, chooser, saved,
        onDestination = { destination -> operation { source -> owner.requestOpen(source.objectId, source.version, destination); onDismiss() } },
        onChooseCanvas = { chooser = true },
        onCanvas = { canvas -> operation { source -> owner.insert(canvas.objectId, canvas.version, source.objectId, source.version); saved = true; chooser = false } },
        onWorkingSet = { operation { source -> owner.selectWorkingSet(source.objectId, source.version); onDismiss() } },
        onRecover = { onDismiss(); com.halo.app.ui.shell.HaloScreenLauncher.dispatch(Graph.applicationContext, "notebook") },
        onDismiss = onDismiss)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceContinueDialog(item: WorkspaceContinuityItem?, canvases: List<WorkspaceCanvasDestination>, busy: Boolean,
    failed: Boolean, choosingCanvas: Boolean, saved: Boolean, onDestination: (WorkspaceDestination) -> Unit,
    onChooseCanvas: () -> Unit, onCanvas: (WorkspaceCanvasDestination) -> Unit, onWorkingSet: () -> Unit,
    onRecover: () -> Unit, onDismiss: () -> Unit) {
    StudioAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.link53_continue)) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Text(stringResource(R.string.link53_unavailable), color = MaterialTheme.colorScheme.error)
                Button(onClick = onRecover, enabled = !busy) { Text(stringResource(R.string.link53_workspace)) }
            } else if (item == null) CircularProgressIndicator()
            else {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.link53_memory_separate), style = MaterialTheme.typography.bodySmall)
                if (saved) Text(stringResource(R.string.link53_saved), color = MaterialTheme.colorScheme.primary)
                if (choosingCanvas) {
                    Text(stringResource(R.string.link53_canvas), style = MaterialTheme.typography.titleSmall)
                    if (canvases.isEmpty()) Text(stringResource(R.string.link53_no_canvas))
                    canvases.forEach { canvas -> TextButton(onClick = { onCanvas(canvas) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(canvas.title) } }
                } else {
                    item.destinations.forEach { destination -> TextButton(onClick = { onDestination(destination) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(when (destination) {
                            WorkspaceDestination.OPEN -> R.string.link53_open
                            WorkspaceDestination.FILE_TOOLS -> R.string.link53_files
                            WorkspaceDestination.STUDIO -> R.string.link53_studio
                            WorkspaceDestination.STUDY -> R.string.link53_study
                            WorkspaceDestination.CHAT -> R.string.link53_chat
                        })) } }
                    if (item.type in setOf(WorkspaceObjectType.NOTE, WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA))
                        TextButton(onClick = onChooseCanvas, enabled = !busy) { Text(stringResource(R.string.link53_canvas)) }
                    TextButton(onClick = onWorkingSet, enabled = !busy) { Text(stringResource(R.string.link53_context)) }
                    if (item.sources.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.link53_sources), style = MaterialTheme.typography.titleSmall)
                        item.sources.forEach { source ->
                            Column {
                                source.title?.let { Text(it) }
                                Text(stringResource(when (source.state) {
                                    WorkspaceSourceState.CURRENT -> R.string.link53_source_current
                                    WorkspaceSourceState.EARLIER_VERSION -> R.string.link53_source_earlier
                                    WorkspaceSourceState.UNAVAILABLE -> R.string.link53_source_missing
                                    WorkspaceSourceState.VERSION_UNVERIFIED -> R.string.link53_source_unverified
                                }), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.link53_done)) } }, dismissButton = {})
}
