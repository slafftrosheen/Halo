package com.halo.app.ui.screens.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.backup.*
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceSection

@Composable
fun StorageBackupScreen(viewModel: StorageBackupViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var includeFiles by remember { mutableStateOf(false) }
    var protect by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var conflictPolicies by remember { mutableStateOf<Map<String, ImportConflictPolicy>>(emptyMap()) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmRemoveAccess by remember { mutableStateOf(false) }
    var relinkingId by remember { mutableStateOf<String?>(null) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::selectBackupFolder) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportTo(it, if (includeFiles) BrainExportProfile.BRAIN_WITH_FILES else BrainExportProfile.BRAIN_ONLY, protect, password) }
        password = ""
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.inspect(it, importPassword) }
    }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkingId
        if (uri != null && id != null) viewModel.relinkFile(id, uri)
        relinkingId = null
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.importValidation) {
        val valid = state.importValidation as? BrainArchiveValidation.Valid
        conflictPolicies = valid?.plan?.conflicts?.associate { it.objectId to ImportConflictPolicy.KEEP_LOCAL }.orEmpty()
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace local Halo Brain?") },
            text = { Text("This replaces current Notebook, conversations and memory. Halo will first create a local safety snapshot. Export a separate Brain Dump if you need a copy beyond this installation.") },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; viewModel.restore(BrainImportMode.REPLACE, importPassword) }) { Text("Replace brain") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(uiText("Cancel")) } },
        )
    }
    if (confirmRemoveAccess) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAccess = false },
            title = { Text("Remove folder access?") },
            text = { Text("Halo will stop updating this Vault. Existing backup files will remain in the folder.") },
            confirmButton = { TextButton(onClick = { confirmRemoveAccess = false; viewModel.removeBackupFolder() }) { Text("Remove access") } },
            dismissButton = { TextButton(onClick = { confirmRemoveAccess = false }) { Text(uiText("Cancel")) } },
        )
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp)) {
        HaloWorkspaceHeader("Storage & Backup", "Your live brain stays private on this phone. Vault backups belong to you.", onBack)
        Spacer(Modifier.height(18.dp))

        HaloWorkspaceSection("Local data") {
            UsageRow("Halo Brain", state.usage.databaseBytes)
            UsageRow("Workspace files", state.usage.workspaceBytes)
            UsageRow("Models", state.usage.modelBytes)
            UsageRow("Speech", state.usage.speechModelBytes)
            UsageRow("Diagnostics", state.usage.diagnosticBytes)
            UsageRow("Cache", state.usage.cacheBytes)
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceCard {
            Text("Halo Vault", style = MaterialTheme.typography.titleMedium)
            Text(backupLocationLabel(state.access.backupLocation), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when (val health = state.vaultStatus) {
                    VaultStatus.NotConfigured -> "Automatic backup is off until you choose a folder"
                    VaultStatus.AccessLost -> "Access lost · Choose the folder again"
                    is VaultStatus.Failed -> "Last backup failed · ${health.reason}"
                    is VaultStatus.Healthy -> health.lastSuccessful?.let { "Last successful backup · ${formatBytes(it.sizeBytes ?: 0)}" } ?: "No successful backup yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloWorkspaceButton(if (state.access.backupLocation is BackupLocationState.Available) "Change folder" else "Choose backup folder", { folderLauncher.launch(null) }, Modifier.weight(1f))
                if (state.access.backupLocation !is BackupLocationState.NotConfigured) {
                    TextButton(onClick = { confirmRemoveAccess = true }) { Text("Remove access") }
                }
            }
            if (state.access.backupLocation is BackupLocationState.Available) {
                HaloWorkspaceButton("Back up now", viewModel::createVaultBackup, Modifier.fillMaxWidth())
            }
            Text("Existing backup files remain when access is removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceSection("Automatic backup") {
            AutomaticBackupSchedule.entries.forEach { schedule ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(schedule.name.lowercase().replaceFirstChar(Char::uppercase), modifier = Modifier.padding(vertical = 12.dp))
                    RadioButton(selected = state.automaticBackup == schedule, onClick = { viewModel.setAutomaticBackup(schedule) })
                }
            }
            Text("Keep last", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 10, 20).forEach { count ->
                    FilterChip(selected = state.retention == count, onClick = { viewModel.setRetention(count) }, label = { Text("$count") })
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceCard {
            Text("Export Brain Dump", style = MaterialTheme.typography.titleMedium)
            Text("Create a portable copy of your Notebook, memory and conversations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Include workspace files"); Switch(includeFiles, { includeFiles = it }) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Protect with password"); Switch(protect, { protect = it }) }
            if (protect) OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            HaloWorkspaceButton("Export Brain Dump", { exportLauncher.launch(StorageBackupViewModel.suggestedFilename()) }, Modifier.fillMaxWidth(), enabled = !protect || password.isNotBlank())
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceCard {
            Text("Import / Restore", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(importPassword, { importPassword = it }, label = { Text("Password, if protected") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            HaloWorkspaceButton("Choose Brain Dump", { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth())
            val valid = state.importValidation as? BrainArchiveValidation.Valid
            if (valid != null) {
                Text("${valid.plan.create.size} new · ${valid.plan.update.size} existing · ${valid.plan.conflicts.size} conflicts", style = MaterialTheme.typography.bodyMedium)
                if (valid.plan.missingFiles.isNotEmpty()) Text("${valid.plan.missingFiles.size} files need attention", color = MaterialTheme.colorScheme.error)
                valid.plan.conflicts.forEach { conflict ->
                    Text("Conflict · ${conflict.type} · ${conflict.objectId.take(12)}", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportConflictPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = conflictPolicies[conflict.objectId] == policy,
                                onClick = { conflictPolicies = conflictPolicies + (conflict.objectId to policy) },
                                label = { Text(policy.label()) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloWorkspaceButton("Merge", { viewModel.restore(BrainImportMode.MERGE, importPassword, conflictPolicies) }, Modifier.weight(1f))
                    OutlinedButton(onClick = { confirmReplace = true }, modifier = Modifier.weight(1f)) { Text("Replace") }
                }
                Text("Replace creates a local safety snapshot first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(18.dp))
        HaloWorkspaceSection("Maintenance") {
            TextButton(onClick = viewModel::clearModels) { Text("Clear model downloads") }
            TextButton(onClick = viewModel::clearDiagnostics) { Text("Clear diagnostics") }
            TextButton(onClick = viewModel::clearCache) { Text("Clear cache") }
        }
        if (state.relinkFiles.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            HaloWorkspaceSection("Files needing relink") {
                state.relinkFiles.forEach { file ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(file.displayName, modifier = Modifier.weight(1f))
                        TextButton(onClick = { relinkingId = file.objectId.value; relinkLauncher.launch(arrayOf("*/*")) }) { Text("Locate file") }
                    }
                }
            }
        }
        if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); state.progress?.let { Text(it) } }
        state.message?.let { Text(it, modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary) }
        if (state.message?.startsWith("Restore completed") == true) {
            TextButton(onClick = viewModel::undoRestore) { Text("Undo restore") }
        }
        Spacer(Modifier.height(80.dp))
    }
}

private fun ImportConflictPolicy.label(): String = when (this) {
    ImportConflictPolicy.KEEP_LOCAL -> "Keep local"
    ImportConflictPolicy.USE_BACKUP -> "Use backup"
    ImportConflictPolicy.DUPLICATE -> "Duplicate"
}

@Composable private fun UsageRow(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(formatBytes(bytes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun backupLocationLabel(location: BackupLocationState): String = when (location) {
    BackupLocationState.NotConfigured -> "Backup folder · Not selected"
    is BackupLocationState.Available -> "Backup folder · ${location.displayName ?: "Connected"}"
    is BackupLocationState.PermissionLost -> "Backup folder access was removed"
    is BackupLocationState.Unavailable -> "Backup folder unavailable · ${location.reason}"
}
