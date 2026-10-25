@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.halo.app.ui.shell

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
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.home.ComposerDraftNotice
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.provider.CognitiveSessionState

/** An Orb-opened editor; admission, conversation and cancellation stay with HomeViewModel. */
@Composable
internal fun ShellTypedMessageSheet(home: HomeViewModel, visible: Boolean, onDismiss: () -> Unit) {
    val ui by home.uiState.collectAsStateWithLifecycle()
    val submission by home.textSubmission.collectAsStateWithLifecycle()
    val changing by home.conversationChanging.collectAsStateWithLifecycle()
    val starting by home.listeningRequested.collectAsStateWithLifecycle()
    val drafts by home.composerDrafts.collectAsStateWithLifecycle()
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val key = ui.conversationId.orEmpty()
    val text = drafts[key]?.text.orEmpty()
    LaunchedEffect(key) { home.loadComposerDraft(key) }
    if (!visible) return
    val active = ui.sessionState != CognitiveSessionState.Idle &&
        ui.sessionState !is CognitiveSessionState.Completed && ui.sessionState !is CognitiveSessionState.Failed
    val busy = submission.busy || active || starting || ui.inputStatus.capturing || ui.speechActive ||
        ui.speechProcessing || ui.runtimeState == HaloRuntimeState.Recognizing || ui.runtimeState == HaloRuntimeState.Speaking
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Type a message"), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = text, onValueChange = { next -> home.updateComposerDraft(key, next) },
                label = { Text(uiText("Message")) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
            ComposerDraftNotice(home, key)
            if (submission.interactionId == pendingId) submission.message?.let { Text(uiText(it)) }
            ui.presentedIdentity?.takeIf { pendingId != null && it.interactionId == pendingId && it.conversationId == ui.conversationId }
                ?.let { Text(it.text, style = MaterialTheme.typography.bodyLarge) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) OutlinedButton(onClick = home::stopOrbInteraction, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Stop")) }
                Button(onClick = {
                    val previousId = home.textSubmission.value.interactionId
                    home.submitComposerText(key, drafts[key]?.revision, text)
                    val accepted = home.textSubmission.value
                    if (accepted.interactionId != previousId && accepted.draft == text.trim() && accepted.busy) {
                        pendingId = accepted.interactionId
                    }
                }, enabled = text.isNotBlank() && !busy && !changing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Send")) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Done")) }
            }
        }
    }
}
