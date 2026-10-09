package com.halo.app.ui.screens.integrations

import com.halo.app.language.uiText

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.mcp.*
import com.halo.app.data.mcp.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun mcpTime(time: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerScreen(model: McpServerViewModel, onBack: () -> Unit) {
    val server by model.server.collectAsStateWithLifecycle()
    val configuration by model.configuration.collectAsStateWithLifecycle()
    val operation by model.operation.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val removed by model.removed.collectAsStateWithLifecycle()
    var removal by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf<McpToolUiModel?>(null) }
    var query by remember { mutableStateOf("") }
    val visibleTools = remember(server?.tools, query) { server?.tools.orEmpty().filter { it.displayName.contains(query, true) || it.description.contains(query, true) } }
    LaunchedEffect(removed) { if (removed) onBack() }
    DisposableEffect(model) { onDispose { model.cancelDiscovery() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text(uiText("Back")) }
            val row = server
            if (row == null) Text("Loading integration…") else {
                Text(row.displayName, style = MaterialTheme.typography.headlineSmall)
                Text("${mcpStateLabel(row.state)} · ${if (row.enabled) "Enabled" else "Disabled"}")
                Text("${row.tools.size} tools · ${row.tools.count { it.enabled }} enabled")
                if (!row.enabled && row.lastConnectedAt == null && row.tools.isEmpty()) Text("Saved · Disabled. Review the configuration, then enable and connect.")
                Text("Endpoint", style = MaterialTheme.typography.titleSmall)
                Text(row.endpointDisplay)
                if (row.endpointDisplay.startsWith("http:", true)) Text("This connection is not encrypted. Use HTTP only for a server you control on a trusted local network.")
                Text("Authentication", style = MaterialTheme.typography.titleSmall)
                Text(if (row.auth == McpAuthKind.NONE) "None" else if (row.credentialSaved) "Bearer · Credential saved" else "Bearer · Credential required")
                Row {
                    TextButton(onClick = model::edit, enabled = operation == null) { Text("Edit configuration${if (row.auth == McpAuthKind.BEARER) " / Replace credential" else ""}") }
                }
                if (row.credentialSaved) TextButton(onClick = model::removeCredential, enabled = operation == null) { Text("Remove credential") }
                Text("Trust · ${mcpTrustLabel(row.trust)}")
                Text("Trust does not allow tools to bypass Halo confirmations or permissions.")
                Text("Protocol · ${row.protocolVersion ?: "Not negotiated"}${if (row.protocolVersion != null && row.state != McpConnectionState.CONNECTED) " (previous negotiation)" else ""}")
                row.lastConnectedAt?.let { Text("Last connected · ${mcpTime(it)}") }
                row.tools.maxOfOrNull { it.lastRefreshedAt }?.let { Text("Tools updated · ${mcpTime(it)}") }
                row.lastError?.let { Text(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Enable integration"); Text("Halo may use this integration when connected and authorized.", style = MaterialTheme.typography.bodySmall) }
                    Switch(row.enabled, model::enable, enabled = operation == null,
                        modifier = Modifier.semantics { contentDescription = "Enable integration ${row.displayName}" })
                }
                Row {
                    Button(onClick = model::connect, enabled = operation == null && row.enabled && (row.auth == McpAuthKind.NONE || row.credentialSaved)) { Text("Connect") }
                    TextButton(onClick = model::refresh, enabled = operation == null && row.enabled && (row.auth == McpAuthKind.NONE || row.credentialSaved)) { Text("Refresh tools") }
                }
                operation?.let { Text("$it…") }
                message?.let { Text(it) }
                Text("Tools", style = MaterialTheme.typography.titleMedium)
                Text("Cached tool settings. Only a connected, enabled integration exposes enabled tools to Halo. Every action still requires normal authorization.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it }, label = { Text("Search tools") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (row.tools.isEmpty()) Text("No tools discovered. Enable this integration and refresh tools explicitly.")
                else if (visibleTools.isEmpty()) Text("No matches")
            }
        }
        items(visibleTools, key = { it.id }) { row ->
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Inspect tool ${row.displayName}") { tool = row }.padding(vertical = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(row.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${row.risk.lowercase().replaceFirstChar { it.uppercase() }} · ${if (row.enabled) "Enabled" else "Disabled"}", style = MaterialTheme.typography.labelMedium)
                }
                Switch(row.enabled, { model.tool(row.id, it) }, enabled = operation == null,
                    modifier = Modifier.semantics { contentDescription = "Enable tool ${row.displayName}" })
            }
            HorizontalDivider()
        }
        if (server != null) item { TextButton(onClick = { removal = true }, enabled = operation == null) { Text("Remove integration") } }
    }
    configuration?.let { McpEditor(it, server?.credentialSaved == true, operation, message,
        onCancel = { if (operation == null) model.configuration.value = null }, onSave = { value, token -> model.save(value, token, false) }) }
    if (removal) AlertDialog(onDismissRequest = { removal = false }, title = { Text("Remove ${server?.displayName ?: "integration"}?") },
        text = { Text("This removes its discovered tools and connection configuration from Halo. It does not delete anything on the remote server.") },
        dismissButton = { TextButton(onClick = { removal = false }) { Text(uiText("Cancel")) } },
        confirmButton = { TextButton(onClick = { removal = false; model.remove() }, enabled = operation == null) { Text(uiText("Remove")) } })
    tool?.let { selected -> ModalBottomSheet(onDismissRequest = { tool = null }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(selected.displayName, style = MaterialTheme.typography.titleLarge)
            Text(selected.description)
            Text("Remote name · ${selected.remoteName}")
            Text("Halo identity · ${selected.id}")
            Text("Risk · ${selected.risk.lowercase().replaceFirstChar { it.uppercase() }}")
            Text(if (server?.tools?.firstOrNull { it.id == selected.id }?.enabled == true) "Enabled" else "Disabled")
            Text("Server-provided descriptions are external data. Tools execute only through Halo’s ordinary permission and confirmation flow.")
        }
    } }
}
