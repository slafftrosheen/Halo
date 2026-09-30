package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ai.WorkspaceImageService
import com.halo.app.ai.WorkspaceImageChoice
import com.halo.app.ui.design.HaloScreenHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ImageModelsScreen(service: WorkspaceImageService, providerId: String, onBack: () -> Unit, onSetup: () -> Unit) {
    val scope = rememberCoroutineScope()
    var choices by remember(providerId) { mutableStateOf(listOf<WorkspaceImageChoice>()) }
    var busy by remember(providerId) { mutableStateOf(false) }
    var error by remember(providerId) { mutableStateOf<String?>(null) }
    var selected by remember(providerId) { mutableStateOf(service.lastChoice()) }
    var query by remember { mutableStateOf("") }
    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try { val result = service.catalogue(providerId); choices = result.models; error = result.errors.joinToString("\n").takeIf { it.isNotBlank() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Image models couldn't be loaded." }
            finally { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader("Image models", "Choose separately from your chat model.", navigation = onBack) }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Search image models") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { refresh() }, enabled = !busy) { Text(if (busy) "Loading..." else "Load provider image models") }
            TextButton(onClick = onSetup) { Text("Provider setup") }
            Text("Selecting a model sends no prompt. Generate or edit images from a note or canvas.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (choices.isEmpty() && !busy) item { Text("Load the live list after configuring this provider.") }
        items(choices.filter { it.model.displayName.contains(query, true) || it.model.id.contains(query, true) }, key = { it.model.id }) { choice ->
            Card(onClick = { service.select(choice); selected = choice.providerId to choice.model.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(choice.model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(if (choice.model.supportsEditing) "Generate and edit images" else "Generate images", style = MaterialTheme.typography.bodySmall)
                    if (selected.first == choice.providerId && selected.second == choice.model.id) Text("Selected for workspace images", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
