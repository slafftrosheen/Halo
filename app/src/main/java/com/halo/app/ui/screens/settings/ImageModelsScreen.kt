package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.icons.HaloGlyph
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
    var query by rememberSaveable { mutableStateOf("") }
    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try { val result = service.catalogue(providerId); choices = result.models; error = if (result.errors.isEmpty()) null else "Image models couldn't be loaded." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Image models couldn't be loaded." }
            finally { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader(uiText("Image models"), uiText("Choose separately from your chat model."), navigation = onBack) }
        item {
            OutlinedTextField(query, { query = it }, label = { Text(uiText("Search image models")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            HaloFocusAction(uiText("Load provider image models"), HaloGlyph.MODEL, !busy, { refresh() }, active = choices.isEmpty())
            if (busy) HaloProgressPresentation(uiText("Loading…"))
            TextButton(onClick = onSetup) { Text(uiText("Provider setup")) }
            Text(uiText("Selecting a model sends no prompt. Generate or edit images from a note or canvas."), style = MaterialTheme.typography.bodySmall)
            error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
        }
        if (choices.isEmpty() && !busy) item { Text(uiText("Load the live list after configuring this provider.")) }
        if (choices.isNotEmpty() && choices.none { it.model.displayName.contains(query, true) || it.model.id.contains(query, true) }) item {
            Text(uiText("No matching models")); TextButton(onClick = { query = "" }) { Text(uiText("Clear search")) }
        }
        items(choices.filter { it.model.displayName.contains(query, true) || it.model.id.contains(query, true) }, key = { it.model.id }) { choice ->
            Card(onClick = { service.select(choice); selected = choice.providerId to choice.model.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(choice.model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(uiText(if (choice.model.supportsEditing) "Generate and edit images" else "Generate images"), style = MaterialTheme.typography.bodySmall)
                    if (selected.first == choice.providerId && selected.second == choice.model.id) Text(uiText("Selected for workspace images"), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
