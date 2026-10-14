package com.halo.app.ui.mini

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.state.toSignalPresentation

/** Same conversation, runtime and durable approvals as the full Home screen. */
@Composable
fun MiniHaloScreen(home: HomeViewModel, onMicrophone: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val ui by home.uiState.collectAsStateWithLifecycle()
    val submission by home.textSubmission.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val incoming by MiniHaloEntry.pendingDraft.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { incoming?.let { draft = it; MiniHaloEntry.pendingDraft.compareAndSet(it, null) } }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) home.cancelVoiceInput()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); home.cancelVoiceInput() }
    }
    MiniHaloContent(ui.sessionState.toSignalPresentation(fallback = ui.runtimeState), draft, { draft = it },
        submission.busy, ui.presentedResponse, submission.message ?: ui.rejectionNotice?.reasonText,
        onTalk = { if (ui.microphonePermissionGranted) home.toggleListening() else onMicrophone() },
        onSend = { home.submitText(draft) }, onStop = home::cancelSession,
        onTool = home::submitText, onOpen = onOpen, onClose = onClose)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MiniHaloContent(signal: HaloSignalPresentation, draft: String, onDraft: (String) -> Unit,
    busy: Boolean, response: String?, notice: String?, onTalk: () -> Unit, onSend: () -> Unit,
    onStop: () -> Unit, onTool: (String) -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit) {
    var tools by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("Mini Halo"), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = { onOpen("home") }) { Text(uiText("Open Halo")) }
            TextButton(onClick = onClose) { Text(uiText("Close")) }
        }
        HaloSignalFace(signal, size = 88.dp, contentDescription = uiText("Talk to Halo"), onClick = onTalk)
        Text(uiText(signal.label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        OutlinedTextField(draft, onDraft, label = { Text(uiText("Ask Halo")) },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTalk, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Talk")) }
            OutlinedButton(onClick = onSend, enabled = draft.isNotBlank() && !busy,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Send")) }
            OutlinedButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop")) }
            TextButton(onClick = { tools = !tools }) { Text(uiText("My tools")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!response.isNullOrBlank()) Surface(shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(response, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        if (tools) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTool("open camera") }, enabled = !busy) { Text(uiText("Open camera")) }
            OutlinedButton(onClick = { onOpen("notebook") }) { Text(uiText("Notebook")) }
            OutlinedButton(onClick = { onOpen("study") }) { Text(uiText("Continue learning")) }
        }
    }
}
