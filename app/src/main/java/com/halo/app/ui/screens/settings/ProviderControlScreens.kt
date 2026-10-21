package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.halo.app.ui.design.HaloScreenHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import com.halo.app.ui.design.*
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.halo.app.ai.label
import com.halo.app.ai.ProviderControlRepository
import com.halo.app.ai.providerOutcomeLabel
import com.halo.app.ai.providerKeyPage
import com.halo.sdk.provider.*

@Composable
fun ProviderCenterScreen(control: ProviderControlRepository, onBack: () -> Unit, onProvider: (String) -> Unit) {
    val rows by control.rows.collectAsStateWithLifecycle()
    val groups = remember(rows) {
        listOf(AiExecutionLocation.ON_DEVICE to "On device", AiExecutionLocation.LOCAL_NETWORK to "Local network", AiExecutionLocation.REMOTE to "Cloud")
            .map { (location, title) -> title to rows.filter { it.descriptor.location == location }
                .sortedWith(compareByDescending<com.halo.app.ai.ProviderControlRow> { it.configured }.thenBy { it.descriptor.displayName }) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") { HaloScreenHeader(uiText("Providers"), uiText("Connect Halo to the intelligence you choose."), navigation = onBack) }
        groups.forEach { (title, group) ->
            if (group.isNotEmpty()) item { Text(uiText(title), style = MaterialTheme.typography.titleSmall) }
            items(group, key = { it.descriptor.providerId }) { row ->
                Card(shape = com.halo.app.ui.design.HaloShapes.settingsCard, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), onClick = { onProvider(row.descriptor.providerId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.descriptor.displayName, style = MaterialTheme.typography.titleMedium)
                        if (row.selected) Text(uiText("Conversation default"), style = MaterialTheme.typography.labelMedium)
                        Text(uiText(row.connection.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uiText("%1\$d cached models", row.modelCount), style = MaterialTheme.typography.labelSmall)
                        if (control.providers.any { it.id == row.descriptor.providerId && it is AiImageProvider }) Text(uiText("Image creation"))
                        if (row.selected) row.lastSelectedModelId?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderDetailScreen(control: ProviderControlRepository, providerId: String, onBack: () -> Unit,
    onModels: (String) -> Unit, onAssignRole: () -> Unit, onDevice: () -> Unit, onDiagnostics: () -> Unit, onImages: () -> Unit = {}) {
    val rows by control.rows.collectAsStateWithLifecycle()
    val checkGeneration = rememberProviderGenerationCheck(control)
    val row = rows.firstOrNull { it.descriptor.providerId == providerId }
    var secret by remember(providerId) { mutableStateOf("") }
    var values by rememberSaveable(providerId, stateSaver = mapSaver(
        save = { drafts -> drafts.filterKeys { key -> row?.descriptor?.fields?.firstOrNull { it.id == key }?.kind != ProviderFieldKind.ENDPOINT }.toMap() },
        restore = { row?.fields.orEmpty() + it.mapValues { entry -> entry.value as String } }
    )) { mutableStateOf(row?.fields.orEmpty()) }
    LaunchedEffect(row?.fields) { values = row?.fields.orEmpty() + values }
    var saving by remember(providerId) { mutableStateOf(false) }
    var removeCredential by rememberSaveable(providerId) { mutableStateOf(false) }
    var message by remember(providerId) { mutableStateOf<String?>(null) }
    val unsaved = values != row?.fields.orEmpty() || secret.isNotBlank()
    val context = LocalContext.current
    val activity = context.controlActivity()
    val client = remember(activity) { activity?.let { Identity.getAuthorizationClient(it) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = if (result.resultCode == Activity.RESULT_OK) result.data?.let { data -> runCatching { client?.getAuthorizationResultFromIntent(data)?.accessToken }.getOrNull() } else null
        if (token.isNullOrBlank()) message = "Google authorization was cancelled or unavailable"
        else { control.acceptGeminiOAuth(token, values["gemini_project_id"].orEmpty()); message = "OAuth authorized; test connection to check readiness" }
    }
    fun authorize() {
        if (values["gemini_project_id"].isNullOrBlank() || client == null) { message = "Enter a Google Cloud project ID; Google authorization must be available"; return }
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/cloud-platform"), Scope("https://www.googleapis.com/auth/generative-language.retriever"))).build()
        client.authorize(request).addOnSuccessListener { result ->
            if (result.hasResolution()) result.pendingIntent?.let { launcher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
            else result.accessToken?.takeIf { it.isNotBlank() }?.let { control.acceptGeminiOAuth(it, values["gemini_project_id"].orEmpty()); message = "OAuth authorized; test connection to check readiness" }
        }.addOnFailureListener { message = "Google authorization failed" }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") { HaloScreenHeader(row?.descriptor?.displayName ?: uiText("Provider unavailable"), navigation = onBack) }
        if (row == null) item { HaloEmptyState(uiText("Provider unavailable"), uiText("Return to Providers and choose an available setup."), HaloGlyph.MODEL, action = HaloHeaderAction(uiText("Back"), onBack)) }
        if (row != null) {
            item {
                Text(uiText(executionLocationLabel(row.descriptor.location)), style = MaterialTheme.typography.labelLarge)
                Text(uiText(row.connection.label))
                Text(uiText(row.generation.label))
                row.diagnostic?.let { diagnostic ->
                    Text(uiText(providerOutcomeLabel(diagnostic.outcome)), style = MaterialTheme.typography.bodySmall)
                }
                Text(uiText("Reply tests apply to this provider, not every model. Saving configuration clears previous checks."), style = MaterialTheme.typography.bodySmall)
                Text(uiText(if (row.descriptor.fields.none { it.kind == ProviderFieldKind.SECRET }) "No credential required" else if (row.credentialConfigured) "Credential configured" else "No stored credential"))
                providerKeyPage(providerId)?.let { url -> TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }) { Text(uiText("Get API key")) } }
                Text(uiText("%1\$d cached models", row.modelCount))
                row.lastSelectedModelId?.let { selected ->
                    val name = control.catalogue.state.value.models.firstOrNull {
                        it.model.providerId == providerId && it.model.id == selected
                    }?.model?.displayName
                    Text(if (name.isNullOrBlank()) uiText("An answer model is selected") else uiText("Current model: %1\$s", name))
                }

            }
            items(row.descriptor.fields, key = { it.id }) { field ->
                val oauth = values["gemini_auth_mode"] == "OAUTH"
                val customNoAuth = values["custom_openai_compatible_auth_mode"] == "NONE"
                when(field.kind) {
                    ProviderFieldKind.SECRET -> if (!customNoAuth && !(providerId == "gemini" && oauth))
                        HaloTextField(secret, { secret = it }, enabled = !saving, label = { Text(uiText(field.label)) }, placeholder = { Text(uiText(if (row.credentialConfigured) "Credential configured" else "Enter credential")) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                            TextButton(enabled = !saving, onClick = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                secret = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().take(16_384)
                            }) { Text(uiText("Paste credential")) }
                        }, kind = VoiceFieldKind.SECRET)
                    ProviderFieldKind.TEXT, ProviderFieldKind.ENDPOINT -> if (field.id != "gemini_project_id" || oauth)
                        HaloTextField(values[field.id].orEmpty(), { values = values + (field.id to it) }, enabled = !saving, label = { Text(uiText(field.label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.URL_ID)
                    ProviderFieldKind.ENUM -> Column {
                        Text(uiText(field.label))
                        field.options.forEach { option -> TextButton(enabled = !saving, onClick = { values = values + (field.id to option); secret = "" }) { Text(uiText(providerOptionLabel(option)) + if (values[field.id] == option) " · " + uiText("Selected") else "") } }
                    }
                    ProviderFieldKind.OAUTH_ACTION -> if (oauth) {
                        com.halo.app.ui.components.HaloActionButton(onClick = { authorize() }) { Text(uiText("Authorize with Google")) }
                        TextButton(onClick = { control.disconnectGeminiOAuth() }) { Text(uiText("Disconnect Google OAuth")) }
                    }
                }
            }
            item {
                if (row.descriptor.fields.isNotEmpty()) {
                    HaloFocusAction(uiText(if (saving) "Saving…" else "Save securely"), HaloGlyph.MODEL, !saving, onClick = {
                        if (saving) return@HaloFocusAction
                        saving = true
                        val draft = secret
                        val configurationDraft = values
                        control.save(providerId, configurationDraft, draft.takeIf { it.isNotBlank() }) { ok ->
                            saving = false
                            if (ok && secret == draft) secret = ""
                            if (ok && values == configurationDraft) values = control.rows.value.firstOrNull { it.descriptor.providerId == providerId }?.fields.orEmpty()
                            message = if (ok) "Configuration saved; connection not checked" else control.lastDiagnostic(providerId)?.outcome?.let(::providerOutcomeLabel) ?: "Unable to save; check the configuration and try again"
                        }
                    }, active = !row.configured || unsaved)
                    if (row.credentialConfigured) TextButton(enabled = !saving, onClick = { removeCredential = true }) { Text(uiText("Remove credential")) }
                }
                message?.let { Text(uiText(it)) }
                if (unsaved) Text(uiText("Save your changes before testing. Checks use the saved configuration."))
                if (!saving && !unsaved && row.configured) IntelligenceGuidance(
                    intelligenceNextStep(true, row.connection, !row.lastSelectedModelId.isNullOrBlank(), row.generation),
                    {}, { onModels(providerId) }, { control.testConnection(providerId) }, { checkGeneration(providerId) })
                TextButton(enabled = row.configured && !saving && !unsaved, onClick = { control.useDefault(providerId) }) { Text(uiText(if (row.selected) "Conversation default" else "Use for Chat")) }
                OutlinedButton(enabled = row.configured, onClick = { control.refreshModels(providerId) }) { Text(uiText("Fetch my models")) }
                if (control.providers.any { it.id == providerId && it is AiImageProvider }) {
                    TextButton(onClick = onImages) { Text(uiText("Browse image generation models")) }
                    Text(uiText("Image creation uses its own model selection in Canvas and Notebook."), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onModels(providerId) }) { Text(uiText("Browse this provider’s models")) }
                if (row.descriptor.location == AiExecutionLocation.ON_DEVICE) TextButton(onClick = onDevice) { Text(uiText("Manage local models")) }
                Text(uiText("Saving credentials never enables cloud use. Remote requests use your account’s billing and retention policy."))
            }
        }
    }
    if (removeCredential) AlertDialog(onDismissRequest = { removeCredential = false },
        title = { Text(uiText("Remove credential?")) },
        text = { Text(uiText("This provider will need authorization again. Saved notes and conversations are kept.")) },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            removeCredential = false; secret = ""; control.removeCredential(providerId)
        }) { Text(uiText("Remove credential")) } },
        dismissButton = { TextButton(onClick = { removeCredential = false }) { Text(uiText("Cancel")) } })
}

private tailrec fun Context.controlActivity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.controlActivity(); else -> null }

internal fun providerOptionLabel(option: String): String = when(option) {
    "API_KEY" -> "API key"
    "OAUTH" -> "Advanced Google OAuth"
    "NONE" -> "No authentication"
    "BEARER_TOKEN" -> "Access token"
    else -> option
}
