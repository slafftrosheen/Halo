package com.halo.app.ui.screens.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var adding by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { saved?.let { adding = false; model.saved.value = null; onServer(it) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Integrations", style = MaterialTheme.typography.headlineSmall)
            Text("MCP servers and external capabilities")
            TextButton(onClick = { help = !help }) { Text("What is MCP?") }
            if (help) Text("MCP servers expose external tools to Halo. Halo namespaces those tools and applies its own permissions, confirmations and run limits before they can execute.")
            Text("MCP", style = MaterialTheme.typography.titleMedium)
            Text("${servers.size} servers · ${servers.sumOf { server -> server.tools.count { it.enabled } }} enabled tools", style = MaterialTheme.typography.labelMedium)
            Button(onClick = { model.error.value = null; adding = true }, enabled = operation == null) { Text("Add MCP server") }
            OutlinedTextField(query, { model.query.value = it }, label = { Text("Search servers and tools") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (servers.isEmpty()) item {
            Text("Connect external tools", style = MaterialTheme.typography.titleLarge)
            Text("MCP lets Halo use capabilities from servers you choose. Every remote action still follows Halo’s permission and confirmation rules.")
        } else if (visible.isEmpty()) item { Text("No matches") }
        items(visible, key = { it.id }) { server ->
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${server.displayName}") { onServer(server.id) }.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(server.displayName, style = MaterialTheme.typography.titleMedium)
                Text("${mcpStateLabel(server.state)} · ${if (server.enabled) "Enabled" else "Disabled"}")
                Text("${server.tools.size} tools · ${server.tools.count { it.enabled }} enabled", style = MaterialTheme.typography.bodyMedium)
                Text(server.endpointDisplay, style = MaterialTheme.typography.bodySmall)
                server.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var endpoint by remember(initial) { mutableStateOf(initial?.endpoint.orEmpty()) }
    var auth by remember(initial) { mutableStateOf(initial?.auth ?: McpAuthKind.NONE) }
    var trust by remember(initial) { mutableStateOf(initial?.trust ?: McpTrustLevel.UNTRUSTED) }
    var token by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(!credentialSaved) }
    val validation = validateMcpEndpoint(endpoint)
    ModalBottomSheet(onDismissRequest = onCancel) {
        LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (initial == null) "Add MCP server" else "Edit MCP server", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, enabled = operation == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Endpoint") }, enabled = operation == null, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (endpoint.isNotBlank() && validation != null) Text(validation)
                if (endpoint.startsWith("http:", true)) Text("This connection is not encrypted. Use HTTP only for a server you control on a trusted local network.")
                Text("Authentication", style = MaterialTheme.typography.titleSmall)
                Row { McpAuthKind.entries.forEach { kind -> FilterChip(selected = auth == kind, onClick = { auth = kind; token = "" }, enabled = operation == null,
                    label = { Text(if (kind == McpAuthKind.NONE) "None" else "Bearer") }, modifier = Modifier.padding(end = 8.dp)) } }
                if (auth == McpAuthKind.BEARER) {
                    if (credentialSaved && !replacing) {
                        Text("Credential saved")
                        TextButton(onClick = { replacing = true }, enabled = operation == null) { Text("Replace") }
                    }
                    if (replacing) OutlinedTextField(token, { token = it.take(8192) }, label = { Text("Bearer token") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), enabled = operation == null, modifier = Modifier.fillMaxWidth())
                }
                Text("Trust", style = MaterialTheme.typography.titleSmall)
                McpTrustLevel.entries.forEach { level ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = operation == null) { trust = level }) {
                        RadioButton(selected = trust == level, onClick = { trust = level }, enabled = operation == null)
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(mcpTrustLabel(level))
                            Text(when (level) { McpTrustLevel.UNTRUSTED -> "You have not vouched for this server."; McpTrustLevel.USER_APPROVED -> "You recognize and intentionally configured this server."; McpTrustLevel.LOCAL_TRUSTED -> "A server you control in your local environment." }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("Trust does not allow tools to bypass Halo confirmations or permissions.")
                error?.let { Text(it) }
                operation?.let { Text("$it…") }
                Row {
                    TextButton(onClick = { token = ""; onCancel() }, enabled = operation == null) { Text("Cancel") }
                    Button(onClick = { val entered = token.takeIf { auth == McpAuthKind.BEARER && replacing }; token = ""; onSave(McpServerConfiguration(name, endpoint, auth, trust), entered) },
                        enabled = operation == null && name.isNotBlank() && validation == null && (auth == McpAuthKind.NONE || !replacing || token.isNotBlank())) { Text("Save") }
                }
                if (initial == null) Text("Saved integrations start disabled. Review the configuration, then enable and connect.")
            }
        }
    }
}
