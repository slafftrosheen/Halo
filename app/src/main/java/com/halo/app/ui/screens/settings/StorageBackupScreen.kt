package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import com.halo.app.ui.design.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.backup.*
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceSection

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun StorageBackupScreen(viewModel: StorageBackupViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var includeFiles by rememberSaveable { mutableStateOf(false) }
    var protect by rememberSaveable { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var conflictPolicies by rememberSaveable(stateSaver = mapSaver<Map<String, ImportConflictPolicy>>(
        save = { it.mapValues { entry -> entry.value.name } },
        restore = { it.mapValues { entry -> ImportConflictPolicy.valueOf(entry.value as String) } }
    )) { mutableStateOf(emptyMap()) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveAccess by rememberSaveable { mutableStateOf(false) }
    var maintenance by rememberSaveable { mutableStateOf<String?>(null) }
    var relinkingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPicker by rememberSaveable { mutableStateOf<String?>(null) }
    val pickerPending = pendingPicker != null
    val available = !state.busy && !pickerPending
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val accepted = pendingPicker == "folder"; pendingPicker = null
        if (accepted) uri?.let(viewModel::selectBackupFolder)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val accepted = pendingPicker == "export"; pendingPicker = null
        if (accepted) uri?.let { viewModel.exportTo(it, if (includeFiles) BrainExportProfile.BRAIN_WITH_FILES else BrainExportProfile.BRAIN_ONLY, protect, password) }
        password = ""
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val accepted = pendingPicker == "import"; pendingPicker = null
        if (accepted) uri?.let { viewModel.inspect(it, importPassword) }
    }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val accepted = pendingPicker == "relink"; pendingPicker = null
        val id = relinkingId
        if (accepted && uri != null && id != null) viewModel.relinkFile(id, uri)
        relinkingId = null
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.importValidation) {
        val valid = state.importValidation as? BrainArchiveValidation.Valid
        conflictPolicies = valid?.plan?.conflicts?.associate { it.objectId to (conflictPolicies[it.objectId] ?: ImportConflictPolicy.KEEP_LOCAL) }.orEmpty()
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(uiText("Replace data on this phone?")) },
            text = { Text(uiText("This replaces your Notebook, conversations and memory. halo first keeps a local recovery copy. Export a backup to keep a separate copy.")) },
            confirmButton = {
                TextButton(enabled = available && state.importValidation is BrainArchiveValidation.Valid, onClick = { confirmReplace = false; viewModel.restore(BrainImportMode.REPLACE, importPassword) }) { Text(uiText("Replace data")) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(uiText("Cancel")) } },
        )
    }
    if (confirmRemoveAccess) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAccess = false },
            title = { Text(uiText("Remove folder access?")) },
            text = { Text(uiText("halo will stop saving backups to this folder. Existing backups stay there.")) },
            confirmButton = { TextButton(enabled = available, onClick = { confirmRemoveAccess = false; viewModel.removeBackupFolder() }) { Text(uiText("Remove access")) } },
            dismissButton = { TextButton(onClick = { confirmRemoveAccess = false }) { Text(uiText("Cancel")) } },
        )
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(com.halo.app.ui.design.HaloSpacing.screen)) {
        HaloWorkspaceHeader(uiText("Storage & Backup"), uiText("Your notes, conversations and memory stay on this phone. Choose where to keep your backups."), onBack)
        if (state.busy) HaloProgressPresentation(state.progressFeedback?.let { uiText(it.template, *it.arguments.toTypedArray()) } ?: uiText("Working with your data…"))
        if (pickerPending) TextButton(onClick = { pendingPicker = null; relinkingId = null; password = "" }) { Text(uiText("Cancel file selection")) }
        state.feedback?.let { feedback -> HaloFeedbackSurface(uiText(if (feedback.failed) "Data operation needs attention" else "Data operation result"),
            uiText(feedback.template, *feedback.arguments.toTypedArray()),
            kind = if (feedback.failed) HaloFeedbackKind.FAILURE else HaloFeedbackKind.INFORMATION) }
        Spacer(Modifier.height(18.dp))

        HaloSettingsSection(uiText("Local data")) {
            UsageRow("Notes, conversations & memory", state.usage.databaseBytes)
            UsageRow("Workspace files", state.usage.workspaceBytes)
            UsageRow("Models", state.usage.modelBytes)
            UsageRow("Speech", state.usage.speechModelBytes)
            UsageRow("Saved reports", state.usage.diagnosticBytes)
            UsageRow("Cache", state.usage.cacheBytes)
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceCard {
            Text(uiText("Backup folder"), style = MaterialTheme.typography.titleMedium)
            Text(backupLocationLabel(state.access.backupLocation), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when (val health = state.vaultStatus) {
                    VaultStatus.NotConfigured -> uiText("Automatic backup is off until you choose a folder")
                    VaultStatus.AccessLost -> uiText("Access lost · Choose the folder again")
                    VaultStatus.NeedsPassword -> uiText("Set a backup password first")
                    is VaultStatus.Failed -> uiText("Last backup failed. Check folder access and available storage.")
                    is VaultStatus.Healthy -> health.lastSuccessful?.let { uiText("Last successful backup · %1\$s", it.sizeBytes?.let(::formatBytes) ?: uiText("Size unavailable")) } ?: uiText("No successful backup yet")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloWorkspaceButton(uiText(if (state.access.backupLocation is BackupLocationState.Available) "Change folder" else "Choose backup folder"), { pendingPicker = "folder"; folderLauncher.launch(null) }, Modifier.fillMaxWidth(), enabled = available)
                if (state.access.backupLocation !is BackupLocationState.NotConfigured) {
                    TextButton(enabled = available, onClick = { confirmRemoveAccess = true }) { Text(uiText("Remove access")) }
                }
            }
            if (state.access.backupLocation is BackupLocationState.Available) {
                HaloTextField(backupPassword, { backupPassword = it }, enabled = available,
                    label = { Text(uiText("Backup password")) }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, kind = VoiceFieldKind.SECRET)
                Text(uiText("Keep this password to restore on another phone. halo stores it securely for scheduled backups. Changing it affects new backups only."),
                    style = MaterialTheme.typography.bodyMedium)
                HaloWorkspaceButton(uiText("Save backup password"), {
                    val submitted = backupPassword
                    viewModel.saveBackupPassword(submitted) { if (backupPassword == submitted) backupPassword = "" }
                }, Modifier.fillMaxWidth(), enabled = available && backupPassword.isNotBlank(), primary = false)
                HaloWorkspaceButton(uiText("Back up now"), viewModel::createVaultBackup, Modifier.fillMaxWidth(),
                    enabled = available && state.vaultStatus != VaultStatus.NeedsPassword)
            }
            Text(uiText("Existing backup files remain when access is removed."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(18.dp))
        HaloSettingsSection(uiText("Automatic backup")) {
            AutomaticBackupSchedule.entries.forEach { schedule ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(uiText(schedule.name.lowercase().replaceFirstChar(Char::uppercase)), modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    RadioButton(selected = state.automaticBackup == schedule, onClick = { viewModel.setAutomaticBackup(schedule) }, enabled = available)
                }
            }
            Text(uiText("Keep last"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 10, 20).forEach { count ->
                    FilterChip(selected = state.retention == count, onClick = { viewModel.setRetention(count) }, enabled = available, label = { Text("$count") })
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceCard {
            Text(uiText("Export backup"), style = MaterialTheme.typography.titleMedium)
            Text(uiText("Create a portable copy of your Notebook, memory and conversations."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            BackupExportOptions(includeFiles, protect, password, state.busy, pickerPending,
                { includeFiles = it }, { protect = it }, { password = it },
                { if (pendingPicker == null && !state.busy) { pendingPicker = "export"; exportLauncher.launch(StorageBackupViewModel.suggestedFilename()) } })
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceCard {
            Text(uiText("Restore a backup"), style = MaterialTheme.typography.titleMedium)
            Text(uiText("Choose a backup to review it first. Nothing changes until you choose how to restore it."))
            HaloTextField(importPassword, { importPassword = it }, enabled = available, label = { Text(uiText("Password, if protected")) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SECRET)
            HaloWorkspaceButton(uiText("Choose backup"), { pendingPicker = "import"; importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth(), enabled = available)
            val valid = state.importValidation as? BrainArchiveValidation.Valid
            if (valid != null) {
                Text(uiText("%1\$d new · %2\$d existing · %3\$d conflicts", valid.plan.create.size, valid.plan.update.size, valid.plan.conflicts.size), style = MaterialTheme.typography.bodyMedium)
                if (valid.plan.missingFiles.isNotEmpty()) Text(uiText("%1\$d files need attention", valid.plan.missingFiles.size), color = MaterialTheme.colorScheme.error)
                valid.plan.conflicts.forEach { conflict ->
                    val title = valid.plan.update.firstOrNull { it.objectId == conflict.objectId }
                        ?.description?.takeIf { it.isNotBlank() && conflict.type != "MEMORY" }
                        ?: uiText(if (conflict.type == "MEMORY") "Memory" else "Saved item")
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(uiText("Choose which version to keep"), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportConflictPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = conflictPolicies[conflict.objectId] == policy,
                                enabled = available,
                                onClick = { conflictPolicies = conflictPolicies + (conflict.objectId to policy) },
                                label = { Text(uiText(policy.label())) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloWorkspaceButton(uiText("Add to this phone"), { viewModel.restore(BrainImportMode.MERGE, importPassword, conflictPolicies) }, Modifier.fillMaxWidth(), enabled = available)
                    OutlinedButton(enabled = available, onClick = { confirmReplace = true }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Replace")) }
                }
                Text(uiText("Replace creates a local safety snapshot first."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(18.dp))
        HaloSettingsSection(uiText("Maintenance")) {
            TextButton(enabled = available, onClick = { maintenance = "models" }) { Text(uiText("Clear model downloads")) }
            TextButton(enabled = available, onClick = { maintenance = "diagnostics" }) { Text(uiText("Clear saved reports")) }
            TextButton(enabled = available, onClick = { maintenance = "cache" }) { Text(uiText("Clear cache")) }
        }
        if (state.relinkFiles.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            HaloSettingsSection(uiText("Files that moved")) {
                state.relinkFiles.forEach { file ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(file.displayName, modifier = Modifier.weight(1f))
                        TextButton(enabled = available, onClick = { relinkingId = file.objectId.value; pendingPicker = "relink"; relinkLauncher.launch(arrayOf("*/*")) }) { Text(uiText("Find the file")) }
                    }
                }
            }
        }
        if (state.canUndoRestore) {
            Text(uiText("You can undo a restore for 7 days on this phone."), style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = available, onClick = { maintenance = "undo" }) { Text(uiText("Undo restore")) }
        }
        Spacer(Modifier.height(80.dp))
    }
    maintenance?.let { operation -> AlertDialog(onDismissRequest = { maintenance = null },
        title = { Text(uiText(if (operation == "undo") "Restore previous snapshot?" else "Clear selected data?")) },
        text = { Text(uiText(when (operation) {
            "models" -> "Downloaded model files will be removed. Download them again before local inference. Notes and conversations are kept."
            "diagnostics" -> "Saved diagnostic files will be removed. Notes, conversations and backups are kept."
            "undo" -> "The previous local snapshot will replace your current Notebook, memory and conversations. Export current changes first if you need them."
            else -> "Temporary files and the currently inspected backup will be cleared. Saved workspace content is kept."
        })) },
        confirmButton = { TextButton(enabled = available, onClick = {
            maintenance = null
            when (operation) { "models" -> viewModel.clearModels(); "diagnostics" -> viewModel.clearDiagnostics(); "undo" -> viewModel.undoRestore(); else -> viewModel.clearCache() }
        }) { Text(uiText(if (operation == "undo") "Restore" else "Clear")) } },
        dismissButton = { TextButton(onClick = { maintenance = null }) { Text(uiText("Cancel")) } }) }
}

private fun ImportConflictPolicy.label(): String = when (this) {
    ImportConflictPolicy.KEEP_LOCAL -> "Keep this phone’s"
    ImportConflictPolicy.USE_BACKUP -> "Keep the backup’s"
    ImportConflictPolicy.DUPLICATE -> "Keep both"
}

@Composable private fun UsageRow(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(uiText(label), Modifier.weight(1f)); Text(formatBytes(bytes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun backupLocationLabel(location: BackupLocationState): String = when (location) {
    BackupLocationState.NotConfigured -> uiText("Backup folder · Not selected")
    is BackupLocationState.Available -> uiText("Backup folder: %1\$s", location.displayName ?: uiText("Connected"))
    is BackupLocationState.PermissionLost -> uiText("Backup folder access was removed")
    is BackupLocationState.Unavailable -> uiText("Backup folder unavailable. Choose the folder again.")
}
