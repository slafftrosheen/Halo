package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText

/** Normal persistence stays quiet; failures never imply that text reached disk. */
@Composable
fun ComposerDraftNotice(home: HomeViewModel, conversationId: String) {
    val drafts by home.composerDrafts.collectAsStateWithLifecycle()
    val cleanupFailed by home.composerCleanupFailed.collectAsStateWithLifecycle()
    val state = drafts[conversationId]
    var replace by remember(conversationId) { mutableStateOf<Long?>(null) }
    if (replace != null) AlertDialog(onDismissRequest = { replace = null },
        title = { Text(uiText("Replace unreadable saved drafts?")) },
        text = { Text(uiText("The unreadable saved drafts will be replaced with this draft. Other saved draft text may be lost.")) },
        confirmButton = { TextButton(onClick = { val revision = replace; replace = null; home.replaceUnreadableComposerDraft(conversationId, revision) }) { Text(uiText("Replace drafts")) } },
        dismissButton = { TextButton(onClick = { replace = null }) { Text(uiText("Cancel")) } })
    state?.failure?.let { failure ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(uiText(when (failure) {
                ComposerDraftFailure.READ -> "Saved drafts could not be read. Your new text has not replaced them."
                ComposerDraftFailure.WRITE -> "Your draft could not be saved. Keep this text and try again."
                ComposerDraftFailure.TOO_LARGE -> "This draft is too large to save. Shorten it or copy it somewhere safe."
                ComposerDraftFailure.FULL -> "Draft storage is full. Clear an older draft, then try again."
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (failure == ComposerDraftFailure.READ) {
                TextButton(onClick = { home.reloadComposerDraft(conversationId) }) { Text(uiText("Try loading again")) }
                if (state.text.isNotEmpty()) TextButton(onClick = { replace = state.revision }) { Text(uiText("Replace unreadable drafts")) }
            } else if (failure == ComposerDraftFailure.WRITE || failure == ComposerDraftFailure.FULL)
                TextButton(onClick = { home.retryComposerDraft(conversationId) }) { Text(uiText("Try saving again")) }
        }
    }
    if (conversationId.isNotBlank() && state?.failure == null && state?.text.isNullOrEmpty() && !drafts[""]?.text.isNullOrEmpty())
        Column(Modifier.fillMaxWidth()) {
            Text(uiText("A draft from startup is available."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { home.recoverStartupDraft(conversationId) }) { Text(uiText("Recover startup draft")) }
        }
    if (cleanupFailed) Column(Modifier.fillMaxWidth()) {
        Text(uiText("A deleted conversation draft could not be removed. Try again."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = home::retryDeletedDraftCleanup) { Text(uiText("Try clearing deleted drafts")) }
    }
}
