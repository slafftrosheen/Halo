package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.workspace.WorkspaceDestination
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect

/** Presentation bridge only. Consumes one exact request while resumed; no restoration/replay of commands. */
@Composable
fun WorkspaceHandoffRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val request by Graph.workspaceContinuity.requests.pending.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var failed by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle) {
        if (lifecycle != Lifecycle.State.RESUMED) return@LaunchedEffect
        Graph.workspaceContinuity.requests.pending.filterNotNull().collect { pending ->
            if (Graph.workspaceContinuity.requests.claim(pending)) {
                processing = true
                try {
                    val route = Graph.workspaceContinuity.route(pending.objectId, pending.version, pending.destination, pending.id)
                    if (pending.destination == WorkspaceDestination.CHAT) Graph.interactionContext.selectWorkspace(pending.objectId, pending.version)
                    onNavigate(route)
                    Graph.workspaceContinuity.requests.finish(pending, true)
                } catch (cancelled: CancellationException) {
                    Graph.workspaceContinuity.requests.finish(pending, false)
                    throw cancelled
                } catch (_: Exception) {
                    Graph.workspaceContinuity.requests.finish(pending, false)
                    failed = true
                } finally { processing = false }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (failed || request == null && !processing) {
            Text(stringResource(R.string.link53_unavailable))
            Button(onClick = onBack) { Text(stringResource(R.string.link53_workspace)) }
        } else {
            CircularProgressIndicator()
            Text(stringResource(R.string.link53_opening))
        }
    }
}
