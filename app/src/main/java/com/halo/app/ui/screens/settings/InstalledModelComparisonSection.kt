package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.providers.litert.models.LocalModelDescriptor

/** Opt-in CPU comparison. Prompt/output live only in this screen and its existing view model. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstalledModelComparisonSection(viewModel: IntelligenceViewModel, models: List<LocalModelDescriptor>) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var prompt by remember { mutableStateOf("") }
    val running by viewModel.comparing.collectAsStateWithLifecycle()
    val results by viewModel.comparisonResults.collectAsStateWithLifecycle()
    val error by viewModel.comparisonError.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.clearComparison() } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(uiText("Compare two installed models")) }
        if (expanded) {
            Text(uiText("One private prompt, two fresh CPU engines, one at a time. Your assistant selection stays the same. Nothing is added to diagnostic exports."), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { model -> FilterChip(selected = model.id in selected, enabled = !running,
                    onClick = { selected = if (model.id in selected) selected - model.id else (selected + model.id).takeLast(2) },
                    label = { Text(model.displayName) }) }
            }
            HaloTextField(prompt, { prompt = it.take(4096) }, enabled = !running,
                label = { Text(uiText("Comparison prompt")) }, modifier = Modifier.fillMaxWidth(), maxLines = 5, kind = VoiceFieldKind.TEXT, maxChars = 4096)
            Button(enabled = !running && selected.size == 2 && prompt.isNotBlank(),
                onClick = { viewModel.compareInstalledModels(selected, prompt) }) { Text(uiText("Run CPU comparison")) }
            if (running) TextButton(onClick = viewModel::cancelComparison) { Text(uiText("Cancel test")) }
            results.forEach { result ->
                Text(result.modelId, style = MaterialTheme.typography.titleSmall)
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.atMs)), style = MaterialTheme.typography.bodySmall)
                val fallback = uiText("Model default")
                val settings = listOf(uiText("Temperature") to (result.tuning.temperature?.toString() ?: fallback),
                    uiText("Top-p") to (result.tuning.topP?.toString() ?: fallback),
                    uiText("Top-k") to (result.tuning.topK?.toString() ?: fallback),
                    uiText("Maximum output tokens") to "≤${minOf(result.tuning.maxOutputTokens ?: 256, 256)}",
                    uiText("CPU threads") to (result.tuning.cpuThreads?.toString() ?: fallback))
                Text(settings.joinToString(" · ") { "${it.first}: ${it.second}" }, style = MaterialTheme.typography.bodySmall)
                Text(result.output, style = MaterialTheme.typography.bodyMedium)
                Text("${result.effectiveBackend} · ${uiText("Effective context")}: ${result.effectiveContext ?: uiText("Model default")}\n" +
                    "${uiText("Initialization")}: ${result.loadMs} ms · ${uiText("First token")}: ${result.firstTokenMs} ms\n" +
                    "${uiText("Estimated tokens per second")}: %.2f".format(result.estimatedTokensPerSecond),
                    style = MaterialTheme.typography.bodySmall)
                Text("${uiText("Artifact fingerprint")}: ${result.sha256}", style = MaterialTheme.typography.bodySmall)

            }
            error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}
