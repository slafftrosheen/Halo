package com.halo.app.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ai.OfflinePackRole
import com.halo.app.ai.PrivateOfflinePack
import com.halo.app.ai.LocalRolePackControl
import com.halo.app.ai.OfflinePackInventory
import com.halo.app.ai.OfflineConversationExtensionState
import com.halo.app.ai.LocalRolePackCommand
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Reviews source pins and requests explicit acquisition through the same setup owner.
 * Never selects a role, runs native qualification or confers measured readiness.
 */
@Composable
internal fun PrivateOfflinePackDetails(totalMemoryBytes: Long, owner: LocalRolePackControl) {
    var reviewed by rememberSaveable(PrivateOfflinePack.fingerprint) { mutableStateOf(owner.sourceReviewAcknowledged) }
    var expanded by rememberSaveable(PrivateOfflinePack.fingerprint) { mutableStateOf(false) }
    var refreshRevision by rememberSaveable { mutableStateOf(0) }
    var extensionRevision by rememberSaveable { mutableStateOf(0) }
    val transaction by owner.state.collectAsStateWithLifecycle()
    // Explicit disk-only inspection. Never run native probes or qualify in the background.
    val inventory by produceState<OfflinePackInventory?>(null, refreshRevision, transaction.phase, transaction.transactionId) {
        value = null
        if (refreshRevision > 0 && !transaction.busy) value = try { owner.offlineInventory() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
    }
    val extension by produceState<OfflineConversationExtensionState?>(null, owner, refreshRevision, extensionRevision) {
        do {
            value = try { owner.conversationExtensionState() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (value?.busy == true) delay(750)
        } while (value?.busy == true)
    }
    val extensionRequest: (LocalRolePackCommand) -> Unit = { command ->
        extension?.let { owner.requestConversationExtension(command, it.transactionId) }
        extensionRevision++
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.offline59_incomplete), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.offline59_intent), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.offline59_memory, if (totalMemoryBytes > 0)
            Formatter.formatFileSize(LocalContext.current, totalMemoryBytes)
            else stringResource(R.string.offline59_unknown)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.offline59_license), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth().toggleable(value = reviewed, role = Role.Checkbox,
            onValueChange = { checked ->
                if (owner.acknowledgeSourceReview(PrivateOfflinePack.fingerprint, checked)) reviewed = checked
            }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = reviewed, onCheckedChange = null)
            Text(stringResource(R.string.offline59_acknowledge), style = MaterialTheme.typography.bodySmall)
        }
        if (transaction.message == com.halo.app.ai.LOCAL_ROLE_PACK_REVIEW_REQUIRED)
            Text(stringResource(R.string.offline59_review_required), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (expanded) R.string.offline59_hide else R.string.offline59_review))
        }
        TextButton(onClick = { refreshRevision++ }, enabled = !transaction.busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline59_check)) }
        inventory?.let { checked -> Text(stringResource(R.string.offline59_snapshot, checked.verifiedRoles.size,
            OfflinePackRole.entries.size), style = MaterialTheme.typography.bodySmall) }
        if (refreshRevision > 0 && inventory == null) Text(stringResource(R.string.offline59_pending),
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.offline59_chat_candidate), style = MaterialTheme.typography.bodySmall)
        extension?.let { chat ->
            if (chat.installedAndIntact) Text(stringResource(R.string.offline59_chat_intact),
                style = MaterialTheme.typography.bodySmall)
            else Text(stringResource(R.string.offline59_download_space,
                Formatter.formatFileSize(LocalContext.current, chat.remainingDownloadBytes),
                Formatter.formatFileSize(LocalContext.current, chat.requiredStorageBytes)),
                style = MaterialTheme.typography.bodySmall)
            if (!chat.hasEnoughStorage) Text(stringResource(R.string.offline59_low_storage),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (chat.recoveryRequired || chat.message != null) Text(stringResource(R.string.offline59_chat_recovery),
                style = MaterialTheme.typography.bodySmall)
            if (chat.busy) TextButton(onClick = { extensionRequest(LocalRolePackCommand.PAUSE) },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pack39_pause)) }
            else {
                if (!chat.installedAndIntact && !chat.recoveryRequired) TextButton(
                    onClick = { extensionRequest(LocalRolePackCommand.START_OR_RESUME) },
                    enabled = reviewed && chat.available && chat.hasEnoughStorage, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.offline59_chat_download))
                }
                if (chat.transactionId != "NONE" && !chat.recoveryRequired) TextButton(
                    onClick = { extensionRequest(LocalRolePackCommand.ROLLBACK) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.offline59_chat_rollback))
                }
                if (chat.transactionId != "NONE" || chat.recoveryRequired) TextButton(
                    onClick = { extensionRequest(LocalRolePackCommand.KEEP_FILES) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pack39_keep_files))
                }
                if (chat.transactionId != "NONE" || chat.recoveryRequired)
                    Text(stringResource(R.string.pack39_keep_explanation), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded) {
            Text(stringResource(R.string.offline59_license), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline59_languages), style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${PrivateOfflinePack.VERSION}\nSHA-256: ${PrivateOfflinePack.fingerprint}",
                        style = MaterialTheme.typography.bodySmall)
                    (PrivateOfflinePack.artifacts + PrivateOfflinePack.optionalArtifacts).forEach { artifact ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(when (artifact.role) {
                                OfflinePackRole.CONVERSATION -> R.string.offline59_chat
                                OfflinePackRole.ACTION_INTENT -> R.string.offline59_actions
                                OfflinePackRole.RECOGNITION -> R.string.offline59_listening
                                OfflinePackRole.VOICE_ACTIVITY -> R.string.offline59_vad
                                OfflinePackRole.SPEECH_OUTPUT -> R.string.offline59_output
                            }), style = MaterialTheme.typography.labelLarge)
                            Text(artifact.id, style = MaterialTheme.typography.bodySmall)
                            if (artifact.role == OfflinePackRole.CONVERSATION)
                                Text(stringResource(R.string.offline59_chat_candidate), style = MaterialTheme.typography.bodySmall)
                            Text(artifact.source, style = MaterialTheme.typography.bodySmall)
                            Text(artifact.declaredLicense ?: stringResource(R.string.offline59_unknown),
                                style = MaterialTheme.typography.bodySmall)
                            artifact.files.forEach { file ->
                                Text("${file.name}\n${Formatter.formatFileSize(LocalContext.current, file.bytes)}\nSHA-256: ${file.sha256 ?: stringResource(R.string.offline59_unknown)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
