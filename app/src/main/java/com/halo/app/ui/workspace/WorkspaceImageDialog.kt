package com.halo.app.ui.workspace

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.online.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceImageDialog(studio: WorkspaceImageStudio, saveLabel: String? = null, scopeLabel: String? = null, dismissLabel: String? = null, onDismiss: () -> Unit) {
    val state by studio.state.collectAsStateWithLifecycle()
    val privacyMode by com.halo.app.di.ManualDependencyGraph.privacyModes.mode.collectAsStateWithLifecycle()
    val cloudEnabled = privacyMode == com.halo.app.ai.PrivacyMode.CLOUD
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var costs by remember { mutableStateOf(false) }
    LaunchedEffect(studio) { studio.refresh() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, studio) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) studio.cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); studio.cancel() }
    }
    AlertDialog(onDismissRequest = { if (!state.saving) { studio.cancel(); onDismiss() } },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.halo.app.ui.components.PrivacyModeControl()
                Text(uiText(if (state.editing) "Edit an image" else "Create an image"))
            }
        },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(scopeLabel ?: uiText("Describe it, preview it, then save. The original stays intact."), style = MaterialTheme.typography.bodyMedium)
                if (!cloudEnabled) Text(uiText("This action needs Full Power mode. Use the switch at the top of Halo."),
                    style = MaterialTheme.typography.bodySmall)
                if (state.referenceUnavailable) {
                    Text(uiText("The reference image could not be read. Close and select the image again."), color = MaterialTheme.colorScheme.error)
                } else state.referenceBytes?.let { bytes ->
                    Text(uiText("Original image"), style = MaterialTheme.typography.titleSmall)
                    GeneratedImagePreview(bytes, Modifier.fillMaxWidth().height(140.dp), label = uiText("Original image"))
                }
                OutlinedButton(onClick = { search = ""; menu = true }, modifier = Modifier.fillMaxWidth(), enabled = state.choices.isNotEmpty() && !state.busy && !state.saving && !state.loading) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(uiText("Image model"), style = MaterialTheme.typography.labelSmall)
                        Text(state.selected?.model?.displayName ?: uiText("Choose an image model"), maxLines = 3)
                        state.selected?.let { Text(it.providerName, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                HaloTextField(state.prompt, studio::prompt, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    label = { Text(uiText(if (state.result != null) "Prompt or changes for this preview" else if (state.editing) "What would you like to change?" else "Describe your image")) }, enabled = !state.busy && !state.saving, kind = VoiceFieldKind.TEXT)
                if (state.loading || state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (state.preparingSpend) stringResource(R.string.online_preparing) else if (state.loading) uiText("Loading image models...") else uiText("Creating your image..."))
                }
                state.result?.let { image ->
                    Text(uiText("Preview · not saved"), style = MaterialTheme.typography.titleSmall)
                    GeneratedImagePreview(image.bytes, Modifier.fillMaxWidth().height(220.dp))
                    state.resultLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { studio.generate() }, enabled = cloudEnabled && state.prompt.isNotBlank() && !state.loading && !state.busy && !state.saving) { Text(uiText("Regenerate")) }
                        TextButton(onClick = { studio.generate(editPreview = true) }, enabled = cloudEnabled && state.prompt.isNotBlank() && !state.loading && !state.busy && !state.saving && state.selected?.model?.supportsEditing == true) { Text(uiText("Edit preview")) }
                    }
                    Text(uiText("Tap the image to look closer. Save keeps a new copy in this workspace."), style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                state.onlineError?.let { Text(onlineProblemText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (state.onlineError == com.halo.app.online.OnlineProblem.CREDIT_LIMIT && state.selected?.providerId == "openrouter")
                    TextButton(onClick = { uri.openUri("https://openrouter.ai/settings/credits") }) { Text(uiText("Check provider credits")) }
                if (!state.loading && (state.choices.isEmpty() || state.catalogueWarning || state.onlineError in setOf(com.halo.app.online.OnlineProblem.AUTHENTICATION, com.halo.app.online.OnlineProblem.KEY_REQUIRED))) TextButton(onClick = {
                    studio.cancel(); onDismiss()
                    com.halo.app.di.ManualDependencyGraph.requestedHaloScreen.value = "settings/intelligence/providers"
                }) { Text(uiText("Provider setup")) }
                TextButton(onClick = studio::refresh, enabled = !state.loading && !state.busy && !state.saving) { Text(uiText("Refresh image models")) }
                Text(uiText("Uses the provider credentials in Settings. Your prompt and any reference image are sent to that provider."), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.online_cancel_warning), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { costs = true }) { Text(stringResource(R.string.online_costs)) }
            }
        },
        confirmButton = {
            if (state.result != null) Button(onClick = { studio.saveResult(onDismiss) }, enabled = !state.busy && !state.saving) { Text(if (state.saving) uiText("Saving...") else saveLabel ?: uiText("Save image")) }
            else Button(onClick = { studio.generate() }, enabled = cloudEnabled && !state.referenceUnavailable && state.selected != null && state.prompt.isNotBlank() && !state.busy && !state.loading) { Text(uiText("Generate")) }
        }, dismissButton = { TextButton(onClick = { studio.cancel(); onDismiss() }, enabled = !state.saving) { Text(if (state.busy) uiText("Cancel generation") else dismissLabel ?: uiText("Close")) } })
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text(uiText("Choose an image model")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HaloTextField(search, { search = it }, label = { Text(uiText("Search image models")) }, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SEARCH)
            val matches = state.choices.filter { "${it.providerName} ${it.model.displayName}".contains(search, true) }
            if (matches.isEmpty()) Text(uiText("No matching models"))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(matches, key = { "${it.providerId}/${it.model.id}" }) { choice ->
                    TextButton(onClick = { studio.select(choice); menu = false }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(choice.model.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(choice.providerName, style = MaterialTheme.typography.bodySmall)
                            Text(uiText(if (choice.model.requiresReference) "Reference image required" else if (choice.model.supportsEditing) "Generate and edit images" else "Generate images"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { menu = false }) { Text(uiText("Close")) } })
    state.spendPreview?.let { preview ->
        OnlineSpendApprovalDialog(preview.challenge.id, preview.providerName, preview.modelName,
            detail = state.prompt + "\n\n" + (if (preview.hasReference) stringResource(R.string.online_image_reference) + "\n\n" else "") + stringResource(R.string.online_image_unknown),
            pricingUrl = imageProviderPricingUrl(state.selected?.providerId.orEmpty()),
            onDismiss = studio::dismissSpend, onApprove = studio::confirmSpend)
    }
    if (costs) OnlineCostDialog { costs = false }
}
