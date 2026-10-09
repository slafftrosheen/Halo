package com.halo.app.ui.screens.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.halo.app.ai.label
import com.halo.app.ai.ProviderControlRepository
import com.halo.sdk.provider.*

@Composable
fun ProviderCenterScreen(control: ProviderControlRepository, onBack: () -> Unit, onProvider: (String) -> Unit) {
    val rows by control.rows.collectAsStateWithLifecycle()
    val groups = remember(rows) {
        listOf(AiExecutionLocation.ON_DEVICE to "On device", AiExecutionLocation.LOCAL_NETWORK to "Local network", AiExecutionLocation.REMOTE to "Cloud")
            .map { (location, title) -> title to rows.filter { it.descriptor.location == location }
                .sortedWith(compareByDescending<com.halo.app.ai.ProviderControlRow> { it.configured }.thenBy { it.descriptor.displayName }) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") { HaloScreenHeader("Providers", "Connect Halo to the intelligence you choose.", navigation = onBack) }
        groups.forEach { (title, group) ->
            if (group.isNotEmpty()) item { Text(title, style = MaterialTheme.typography.titleSmall) }
            items(group, key = { it.descriptor.providerId }) { row ->
                Card(onClick = { onProvider(row.descriptor.providerId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.descriptor.displayName + if (row.selected) " · Chat" else "", style = MaterialTheme.typography.titleMedium)
                        Text(row.connection.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${row.modelCount} chat models" + if (control.providers.any { it.id == row.descriptor.providerId && it is AiImageProvider }) " · Image creation" else "", style = MaterialTheme.typography.labelSmall)
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
    val row = rows.firstOrNull { it.descriptor.providerId == providerId }
    var secret by remember(providerId) { mutableStateOf("") }
    var values by remember(providerId, row?.fields) { mutableStateOf(row?.fields.orEmpty()) }
    var message by remember(providerId) { mutableStateOf<String?>(null) }
    val activity = LocalContext.current.controlActivity()
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") { HaloScreenHeader(row?.descriptor?.displayName ?: "Provider unavailable", navigation = onBack) }
        if (row != null) {
            item {
                Text("${row.descriptor.protocol} · ${row.descriptor.location.name.replace('_', ' ')} · ${row.descriptor.access}")
                Text(row.generation.label)
                row.lastCheckedAt?.let { Text("Last connection check: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}") }
                row.lastError?.let { Text("Last check: ${row.connection.label}") }
                Text(row.connection.label)
                Text(if (row.descriptor.fields.none { it.kind == ProviderFieldKind.SECRET }) "No credential required" else if (row.credentialConfigured) "Credential configured" else "No stored credential")
                Text(if (row.modelCount == 0) "Models: Not loaded" else "Models: ${row.modelCount} cached")
                row.lastSelectedModelId?.let { Text("Current: $it") }
            }
            items(row.descriptor.fields, key = { it.id }) { field ->
                val oauth = values["gemini_auth_mode"] == "OAUTH"
                val customNoAuth = values["custom_openai_compatible_auth_mode"] == "NONE"
                when(field.kind) {
                    ProviderFieldKind.SECRET -> if (!customNoAuth && !(providerId == "gemini" && oauth))
                        OutlinedTextField(secret, { secret = it }, label = { Text(field.label) }, placeholder = { Text(if (row.credentialConfigured) "Credential configured" else "Enter credential") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    ProviderFieldKind.TEXT, ProviderFieldKind.ENDPOINT -> if (field.id != "gemini_project_id" || oauth)
                        OutlinedTextField(values[field.id].orEmpty(), { values = values + (field.id to it) }, label = { Text(field.label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ProviderFieldKind.ENUM -> Column {
                        Text(field.label)
                        field.options.forEach { option -> TextButton(onClick = { values = values + (field.id to option); secret = "" }) { Text(option + if (values[field.id] == option) " · Selected" else "") } }
                    }
                    ProviderFieldKind.OAUTH_ACTION -> if (oauth) {
                        Button(onClick = { authorize() }) { Text(uiText("Authorize with Google")) }
                        TextButton(onClick = { control.disconnectGeminiOAuth() }) { Text(uiText("Disconnect Google OAuth")) }
                    }
                }
            }
            item {
                if (row.descriptor.fields.isNotEmpty()) {
                    Button(onClick = {
                        val draft = secret
                        control.save(providerId, values, draft.takeIf { it.isNotBlank() }) { ok ->
                            if (ok && secret == draft) secret = ""
                            message = if (ok) "Configuration saved; connection not checked" else "Unable to save; check the configuration and try again"
                        }
                    }) { Text(uiText("Save configuration")) }
                    if (row.credentialConfigured) TextButton(onClick = { secret = ""; control.removeCredential(providerId) }) { Text(uiText("Remove credential")) }
                }
                message?.let { Text(it) }
                OutlinedButton(enabled = row.configured && row.connection != com.halo.app.ai.ProviderConnectionState.CHECKING, onClick = { control.testConnection(providerId) }) { Text(uiText("Test connection")) }
                OutlinedButton(enabled = row.configured && row.generation != com.halo.app.ai.GenerationProof.CHECKING, onClick = { control.checkGeneration(providerId) }) { Text(uiText("Test generation")) }
                TextButton(enabled = row.configured, onClick = { control.useDefault(providerId) }) { Text(uiText("Use for Chat")) }
                TextButton(onClick = onAssignRole) { Text(uiText("Assign roles")) }
                OutlinedButton(enabled = row.configured, onClick = { control.refreshModels(providerId) }) { Text(uiText("Refresh models")) }
                if (control.providers.any { it.id == providerId && it is AiImageProvider }) {
                    TextButton(onClick = onImages) { Text(uiText("Browse image generation models")) }
                    Text(uiText("Image creation uses its own model selection in Canvas and Notebook."), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onModels(providerId) }) { Text(uiText("Browse this provider’s models")) }
                if (row.descriptor.location == AiExecutionLocation.ON_DEVICE) TextButton(onClick = onDevice) { Text(uiText("Manage local models")) }
                TextButton(onClick = onDiagnostics) { Text(uiText("System check and diagnostics")) }
                Text(uiText("Saving credentials never enables cloud use. Remote requests use your account’s billing and retention policy."))
            }
        }
    }
}

private tailrec fun Context.controlActivity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.controlActivity(); else -> null }
