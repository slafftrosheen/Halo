package com.halo.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.providers.litert.models.DownloadState

@Composable
fun ModelBrowserScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelUri(uri)
        }
    }

    val catalogModels = ui.catalogModels.filter { model ->
        query.isBlank() || model.displayName.contains(query, ignoreCase = true) || model.id.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Models", "Curated LiteRT models & SAF importer", navigation = onBack)

        Spacer(Modifier.height(HaloSpacing.sm))

        OutlinedButton(
            onClick = { importLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Import Local .litertlm File")
        }

        Spacer(Modifier.height(HaloSpacing.md))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search catalog models") }
        )

        Spacer(Modifier.height(HaloSpacing.md))

        if (catalogModels.isEmpty()) {
            Text("No models match your search query.", modifier = Modifier.padding(top = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalogModels, key = { it.id }) { model ->
                    val isInstalled = ui.localModels.any { it.id == model.id }
                    val isActive = isInstalled && model.id == ui.activeModelId
                    val downloadState = ui.downloadStates[model.id] ?: DownloadState.NotDownloaded

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("ID: ${model.id} · ${size(model.expectedBytes ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                when {
                                    isActive -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = HaloPastelMint,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text("Active ✓", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    isInstalled || downloadState is DownloadState.Downloaded -> {
                                        Button(onClick = { viewModel.selectModel(model.id) }) {
                                            Text("Select")
                                        }
                                    }
                                    downloadState is DownloadState.Downloading -> {
                                        Text("${(downloadState.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    downloadState is DownloadState.Failed -> {
                                        OutlinedButton(onClick = { viewModel.downloadCatalogModel(model) }) {
                                            Text("Retry")
                                        }
                                    }
                                    else -> {
                                        Button(onClick = { viewModel.downloadCatalogModel(model) }) {
                                            Text("Download")
                                        }
                                    }
                                }
                            }

                            if (downloadState is DownloadState.Downloading) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadState.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (downloadState is DownloadState.Failed) {
                                Spacer(Modifier.height(4.dp))
                                Text(downloadState.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun size(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)
