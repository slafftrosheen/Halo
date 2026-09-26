package com.halo.app.ui.screens.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.providers.litert.models.LiteRtCatalogModel

@Composable
fun ModelBrowserScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val catalogModels = ui.catalogModels.filter { model ->
        query.isBlank() || model.displayName.contains(query, ignoreCase = true) || model.id.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Models", "Curated LiteRT models for on-device inference", navigation = onBack)

        Spacer(Modifier.height(HaloSpacing.md))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search models") }
        )

        Spacer(Modifier.height(HaloSpacing.md))

        if (catalogModels.isEmpty()) {
            Text("No models match your search query.", modifier = Modifier.padding(top = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalogModels, key = { it.id }) { model ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("ID: ${model.id} · ${size(model.expectedBytes ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.selectModel(model.id) }) {
                                Text("Select")
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
