package com.halo.app.ui.screens.integrations

import com.halo.app.language.uiText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.mcp.*
import com.halo.app.data.mcp.*

@Composable
fun IntegrationsScreen(model: McpServersViewModel, onBack: () -> Unit, onServer: (String) -> Unit) {
    val servers by model.servers.collectAsStateWithLifecycle()
    val visible by model.visible.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val operation by model.operation.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) { saved?.let { adding = false; model.saved.value = null; onServer(it) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            HaloScreenHeader(uiText("Integrations"), uiText("Connect services you choose"), navigation = onBack)
            TextButton(onClick = { help = !help }) { Text(uiText("What is MCP?")) }
            if (help) Text(uiText("MCP servers expose external tools to halo. halo namespaces those tools and applies its own permissions, confirmations and run limits before they can execute."))
            Text(uiText("MCP"), style = MaterialTheme.typography.titleMedium)
            Text(uiText("%1\$d servers · %2\$d enabled tools", servers.size, servers.sumOf { server -> server.tools.count { it.enabled } }), style = MaterialTheme.typography.labelMedium)
            HaloFocusAction(uiText("Add MCP server"), HaloGlyph.TOOL, operation == null,
                { model.error.value = null; adding = true }, active = servers.isEmpty())
            OutlinedTextField(query, { model.query.value = it }, label = { Text(uiText("Search servers and tools")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (servers.isEmpty()) item {
            Text(uiText("Connect external tools"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("MCP lets halo use capabilities from servers you choose. Every remote action still follows halo’s permission and confirmation rules."))
        } else if (visible.isEmpty()) item { Text(uiText("No matches")); TextButton(onClick = { model.query.value = "" }) { Text(uiText("Clear search")) } }
        items(visible, key = { it.id }) { server ->
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = uiText("Open %1\$s", server.displayName)) { onServer(server.id) }.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(server.displayName, style = MaterialTheme.typography.titleMedium)
                Text(uiText(mcpStateLabel(server.state)) + " · " + uiText(if (server.enabled) "Enabled" else "Disabled"))
                Text(uiText("%1\$d tools · %2\$d enabled", server.tools.size, server.tools.count { it.enabled }), style = MaterialTheme.typography.bodyMedium)
                Text(server.endpointDisplay, style = MaterialTheme.typography.bodySmall)
                server.lastError?.let { Text(uiText("Connection needs attention. Open this integration to review its setup."), style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
        }
    }
    if (adding) McpEditor(null, false, operation, error, onCancel = { if (operation == null) adding = false }, onSave = { value, token -> model.add(value, token) })
}

/** New tokens are ephemeral Compose state only, never rememberSaveable or existing secret values. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpEditor(initial: McpServerConfiguration?, credentialSaved: Boolean, operation: String?, error: String?,
    onCancel: () -> Unit, onSave: (McpServerConfiguration, String?) -> Unit) {
    var name by rememberSaveable(initial?.name) { mutableStateOf(initial?.name.orEmpty()) }
    var endpoint by rememberSaveable(initial?.endpoint, stateSaver = mcpEndpointDraftSaver) { mutableStateOf(initial?.endpoint.orEmpty()) }
    var auth by rememberSaveable(initial?.auth) { mutableStateOf(initial?.auth ?: McpAuthKind.NONE) }
    var trust by rememberSaveable(initial?.trust) { mutableStateOf(initial?.trust ?: McpTrustLevel.UNTRUSTED) }
    var token by remember { mutableStateOf("") }
    var replacing by rememberSaveable { mutableStateOf(!credentialSaved) }
    val validation = validateMcpEndpoint(endpoint)
    ModalBottomSheet(onDismissRequest = onCancel) {
        LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(uiText(if (initial == null) "Add MCP server" else "Edit MCP server"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(uiText("Name")) }, enabled = operation == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(uiText("Endpoint")) }, enabled = operation == null, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (endpoint.isNotBlank() && validation != null) Text(uiText(validation))
                if (endpoint.startsWith("http:", true)) Text(uiText("This connection is not encrypted. Use HTTP only for a server you control on a trusted local network."))
                Text(uiText("Authentication"), style = MaterialTheme.typography.titleSmall)
                Row { McpAuthKind.entries.forEach { kind -> FilterChip(selected = auth == kind, onClick = { auth = kind; token = "" }, enabled = operation == null,
                    label = { Text(uiText(if (kind == McpAuthKind.NONE) "None" else "Bearer")) }, modifier = Modifier.padding(end = 8.dp)) } }
                if (auth == McpAuthKind.BEARER) {
                    if (credentialSaved && !replacing) {
                        Text(uiText("Credential saved"))
                        TextButton(onClick = { replacing = true }, enabled = operation == null) { Text(uiText("Replace")) }
                    }
                    if (replacing) OutlinedTextField(token, { token = it.take(8192) }, label = { Text(uiText("Bearer token")) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), enabled = operation == null, modifier = Modifier.fillMaxWidth())
                }
                Text(uiText("Trust"), style = MaterialTheme.typography.titleSmall)
                McpTrustLevel.entries.forEach { level ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = operation == null) { trust = level }) {
                        RadioButton(selected = trust == level, onClick = { trust = level }, enabled = operation == null)
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(uiText(mcpTrustLabel(level)))
                            Text(uiText(when (level) { McpTrustLevel.UNTRUSTED -> "You have not vouched for this server."; McpTrustLevel.USER_APPROVED -> "You recognize and intentionally configured this server."; McpTrustLevel.LOCAL_TRUSTED -> "A server you control in your local environment." }), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(uiText("Trust does not allow tools to bypass halo confirmations or permissions."))
                error?.let { Text(uiText(it)) }
                operation?.let { HaloProgressPresentation(uiText(it)) }
                Row {
                    TextButton(onClick = { token = ""; onCancel() }, enabled = operation == null) { Text(uiText("Cancel")) }
                    Button(onClick = { val entered = token.takeIf { auth == McpAuthKind.BEARER && replacing }; onSave(McpServerConfiguration(name, endpoint, auth, trust), entered) },
                        enabled = operation == null && name.isNotBlank() && validation == null && (auth == McpAuthKind.NONE || !replacing || token.isNotBlank())) { Text(uiText("Save")) }
                }
                if (initial == null) Text(uiText("Saved integrations start disabled. Review the configuration, then enable and connect."))
            }
        }
    }
}

internal val mcpEndpointDraftSaver = Saver<String, String>(
    save = { persistableMcpEndpointDraft(it) }, restore = { it })

internal fun persistableMcpEndpointDraft(endpoint: String): String? = endpoint.takeIf {
    endpoint.isEmpty() || (validateMcpEndpoint(endpoint) == null && runCatching {
        java.net.URI(endpoint).let { uri -> uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null }
    }.getOrDefault(false))
}
