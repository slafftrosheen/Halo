package com.halo.app.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.halo.app.ai.ProviderControlRepository
import com.halo.sdk.provider.*

@Composable
fun ProviderCenterScreen(control: ProviderControlRepository, onBack: () -> Unit, onProvider: (String) -> Unit) {
    val rows by control.rows.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text("Providers", style = MaterialTheme.typography.headlineMedium); Text("Set up a provider here, then choose its model separately.") }
        items(rows, key = { it.descriptor.providerId }) { row ->
            Card(onClick = { onProvider(row.descriptor.providerId) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(row.descriptor.displayName + if (row.selected) " · Selected" else "", style = MaterialTheme.typography.titleMedium)
                    Text("${row.descriptor.location.name.replace('_', ' ')} · ${row.descriptor.access.replace('_', ' ')}")
                    Text("${row.connection.name.replace('_', ' ')} · ${row.modelCount} models · ${row.catalogue.name.lowercase()}")
                }
            }
        }
    }
}

@Composable
fun ProviderDetailScreen(control: ProviderControlRepository, providerId: String, onBack: () -> Unit,
    onModels: (String) -> Unit, onAssignRole: () -> Unit, onDevice: () -> Unit, onDiagnostics: () -> Unit) {
    val rows by control.rows.collectAsState()
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
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text(row?.descriptor?.displayName ?: "Provider unavailable", style = MaterialTheme.typography.headlineMedium) }
        if (row != null) {
            item {
                Text("${row.descriptor.protocol} · ${row.descriptor.location.name.replace('_', ' ')} · ${row.descriptor.access}")
                Text("Generation: ${row.generation.name.replace('_', ' ')}")
                row.lastCheckedAt?.let { Text("Last connection check: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}") }
                row.lastError?.let { Text("Last error: $it") }
                Text("Authentication / connection: ${row.connection.name.replace('_', ' ')}")
                Text(if (row.descriptor.fields.none { it.kind == ProviderFieldKind.SECRET }) "No credential required" else if (row.credentialConfigured) "Credential configured" else "No stored credential")
                Text("Catalogue: ${row.catalogue.name.lowercase()} · ${row.modelCount} models")
            }
            items(row.descriptor.fields, key = { it.id }) { field ->
                val oauth = values["gemini_auth_mode"] == "OAUTH"
                val customNoAuth = values["custom_openai_compatible_auth_mode"] == "NONE"
                when(field.kind) {
                    ProviderFieldKind.SECRET -> if (!customNoAuth && !(providerId == "gemini" && oauth))
                        OutlinedTextField(secret, { secret = it }, label = { Text("Replace ${field.label}") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    ProviderFieldKind.TEXT, ProviderFieldKind.ENDPOINT -> if (field.id != "gemini_project_id" || oauth)
                        OutlinedTextField(values[field.id].orEmpty(), { values = values + (field.id to it) }, label = { Text(field.label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ProviderFieldKind.ENUM -> Column {
                        Text(field.label)
                        field.options.forEach { option -> TextButton(onClick = { values = values + (field.id to option); secret = "" }) { Text(option + if (values[field.id] == option) " · Selected" else "") } }
                    }
                    ProviderFieldKind.OAUTH_ACTION -> if (oauth) {
                        Button(onClick = { authorize() }) { Text("Authorize with Google") }
                        TextButton(onClick = { control.disconnectGeminiOAuth() }) { Text("Disconnect Google OAuth") }
                    }
                }
            }
            item {
                if (row.descriptor.fields.isNotEmpty()) {
                    Button(onClick = {
                        val draft = secret; secret = ""
                        control.save(providerId, values, draft.takeIf { it.isNotBlank() }) { ok -> message = if (ok) "Saved. Model selection is unchanged." else "Invalid endpoint or configuration; unable to save" }
                    }) { Text("Save configuration") }
                    if (row.credentialConfigured) TextButton(onClick = { secret = ""; control.removeCredential(providerId) }) { Text("Remove credential") }
                }
                message?.let { Text(it) }
                Button(enabled = row.configured && row.connection != com.halo.app.ai.ProviderConnectionState.CHECKING, onClick = { control.testConnection(providerId) }) { Text("Test authentication / connection") }
                Button(enabled = row.configured && row.generation != com.halo.app.ai.GenerationProof.CHECKING, onClick = { control.checkGeneration(providerId) }) { Text("Run generation check") }
                TextButton(enabled = row.configured, onClick = { control.useDefault(providerId) }) { Text("Use as default") }
                TextButton(onClick = onAssignRole) { Text("Assign role") }
                Button(enabled = row.configured, onClick = { control.refreshModels(providerId) }) { Text("Refresh model metadata") }
                TextButton(onClick = { onModels(providerId) }) { Text("Browse this provider’s models") }
                if (row.descriptor.location == AiExecutionLocation.ON_DEVICE) TextButton(onClick = onDevice) { Text("Manage local models") }
                TextButton(onClick = onDiagnostics) { Text("System check and diagnostics") }
                Text("Saving credentials never enables cloud use. Remote requests use your account’s billing and retention policy.")
            }
        }
    }
}

private tailrec fun Context.controlActivity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.controlActivity(); else -> null }
