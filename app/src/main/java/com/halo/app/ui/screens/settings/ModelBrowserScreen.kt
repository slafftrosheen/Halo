package com.halo.app.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.models.ModelCatalogEntry
import com.halo.app.models.ModelCatalogState
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.providers.llamacpp.DownloadState
import com.halo.providers.llamacpp.LocalModelMemoryPlanner.RemoteModelFit

@Composable
fun ModelBrowserScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var showBase by remember { mutableStateOf(false) }
    var showIncompatible by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ModelCatalogEntry?>(null) }
    val catalog = ui.catalogState
    val all = when (catalog) {
        is ModelCatalogState.Ready -> catalog.entries
        is ModelCatalogState.OfflineCache -> catalog.entries
        else -> emptyList()
    }
    val visible = remember(all, query, showBase, showIncompatible) {
        all.filter { entry ->
            (showBase || entry.isInstructionModel) &&
                (showIncompatible || entry.fit !is RemoteModelFit.TooLarge) &&
                (query.isBlank() || entry.model.repoId.contains(query.trim(), true) ||
                    entry.file.filename.contains(query.trim(), true))
        }
    }
    selected?.let { entry ->
        ModelDetailDialog(entry, viewModel.catalogModelId(entry)?.let { ui.downloadStates[it] },
            error = viewModel.catalogModelId(entry)?.let { ui.downloadErrors[it] },
            onDismiss = { selected = null },
            onDownload = { viewModel.downloadCatalogEntry(entry) },
            onSelect = {
                viewModel.catalogModelId(entry)?.let(viewModel::setActiveLocalModel)
                selected = null
            })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Models", "Recommended for this phone", navigation = onBack,
            primaryAction = HaloHeaderAction("Refresh", viewModel::refreshCatalog))
        val status = when (catalog) {
            is ModelCatalogState.Ready -> "Updated " + DateUtils.getRelativeTimeSpanString(
                catalog.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            is ModelCatalogState.OfflineCache -> "Offline catalog · last synced " +
                DateUtils.getRelativeTimeSpanString(catalog.updatedAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            is ModelCatalogState.Refreshing -> "Refreshing catalog"
            is ModelCatalogState.Failed -> catalog.reason
            ModelCatalogState.Idle -> "Catalog is ready to sync"
        }
        Text(status.toString(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(HaloSpacing.md))
        OutlinedTextField(value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Search models") })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show base models", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            Switch(checked = showBase, onCheckedChange = { showBase = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show incompatible", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            Switch(checked = showIncompatible, onCheckedChange = { showIncompatible = it })
        }
        if (visible.isEmpty()) {
            Text(if (catalog is ModelCatalogState.Refreshing) "Finding GGUF models…"
                else "No models match these filters.", modifier = Modifier.padding(top = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(visible, key = { it.artifact.toString() }) { entry ->
                    val modelId = viewModel.catalogModelId(entry)
                    val downloadState = modelId?.let { ui.downloadStates[it] }
                    TextButton(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(entry.model.repoId.substringAfterLast('/'),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("${entry.file.quantization.name} · ${size(entry.file.bytes)} · " +
                                when (downloadState) {
                                    DownloadState.Downloaded -> "Downloaded"
                                    DownloadState.Downloading -> "Downloading"
                                    DownloadState.Failed -> modelId?.let { ui.downloadErrors[it] } ?: "Download failed; tap to retry"
                                    else -> fitLabel(entry.fit)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ModelDetailDialog(
    entry: ModelCatalogEntry,
    downloadState: DownloadState?,
    error: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onSelect: () -> Unit
) {
    val fit = entry.fit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.model.repoId.substringAfterLast('/')) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Repository · ${entry.model.repoId}")
                Text("File · ${entry.file.filename}")
                Text("Quantization · ${entry.file.quantization.name}")
                Text("Download · ${size(entry.file.bytes)}")
                Text("Fit · ${fitLabel(fit)}")
                fit.context?.let { Text("Recommended context · ${it / 1024}k") }
                Text("License · ${entry.model.license ?: "Not declared"}")
                entry.model.lastModified?.let { Text("Updated · ${it.toString().take(10)}") }
            }
        },
        confirmButton = {
            when (downloadState) {
                DownloadState.Downloaded -> TextButton(onClick = onSelect) { Text("Use model") }
                DownloadState.Downloading -> Text("Downloading", modifier = Modifier.padding(12.dp))
                else -> TextButton(onClick = onDownload,
                    enabled = fit !is RemoteModelFit.TooLarge) { Text("Download") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun fitLabel(fit: RemoteModelFit): String = when (fit) {
    is RemoteModelFit.Recommended, is RemoteModelFit.Fits -> "Great fit"
    is RemoteModelFit.Tight -> "Tight"
    is RemoteModelFit.TooLarge -> "Too large"
    RemoteModelFit.Unknown -> "Unknown fit"
}

private fun size(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)
