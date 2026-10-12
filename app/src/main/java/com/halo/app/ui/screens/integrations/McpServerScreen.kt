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
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.design.*
import com.halo.app.ui.icons.HaloGlyph
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
    var removal by rememberSaveable(model.id) { mutableStateOf(false) }
    var credentialRemoval by rememberSaveable(model.id) { mutableStateOf(false) }
    var details by rememberSaveable(model.id) { mutableStateOf(false) }
    var tool by remember { mutableStateOf<McpToolUiModel?>(null) }
    var query by rememberSaveable(model.id) { mutableStateOf("") }
    val visibleTools = remember(server?.tools, query) { server?.tools.orEmpty().filter { it.displayName.contains(query, true) || it.description.contains(query, true) } }
    LaunchedEffect(removed) { if (removed) onBack() }
    DisposableEffect(model) { onDispose { model.cancelDiscovery() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            HaloScreenHeader(server?.displayName ?: uiText("Integration"), navigation = onBack)
            val row = server
            if (row == null) Text(uiText("Loading integration…")) else {
                Text(uiText(mcpStateLabel(row.state)) + " · " + uiText(if (row.enabled) "Enabled" else "Disabled"))
                Text(uiText("%1\$d tools · %2\$d enabled", row.tools.size, row.tools.count { it.enabled }))
                if (!row.enabled && row.lastConnectedAt == null && row.tools.isEmpty()) Text(uiText("Saved · Disabled. Review the configuration, then enable and connect."))
                Text(uiText("Endpoint"), style = MaterialTheme.typography.titleSmall)
                Text(row.endpointDisplay)
                if (row.endpointDisplay.startsWith("http:", true)) Text(uiText("This connection is not encrypted. Use HTTP only for a server you control on a trusted local network."))
                Text(uiText("Authentication"), style = MaterialTheme.typography.titleSmall)
                Text(uiText(if (row.auth == McpAuthKind.NONE) "None" else if (row.credentialSaved) "Bearer · Credential saved" else "Bearer · Credential required"))
                Row {
                    TextButton(onClick = model::edit, enabled = operation == null) { Text("Edit configuration${if (row.auth == McpAuthKind.BEARER) " / Replace credential" else ""}") }
                }
                if (row.credentialSaved) TextButton(onClick = { credentialRemoval = true }, enabled = operation == null) { Text(uiText("Remove credential")) }
                Text(uiText("Trust: %1\$s", uiText(mcpTrustLabel(row.trust))))
                Text(uiText("Trust does not allow tools to bypass Halo confirmations or permissions."))
                TextButton(onClick = { details = !details }) { Text(uiText(if (details) "Hide connection details" else "Connection details")) }
                if (details) {
                Text(uiText("Protocol: %1\$s", row.protocolVersion ?: uiText("Not negotiated")))
                if (row.protocolVersion != null && row.state != McpConnectionState.CONNECTED) Text(uiText("Previous connection details; current connection is not confirmed."))
                row.lastConnectedAt?.let { Text("Last connected · ${mcpTime(it)}") }
                row.tools.maxOfOrNull { it.lastRefreshedAt }?.let { Text("Tools updated · ${mcpTime(it)}") }
                }
                row.lastError?.let { HaloFeedbackSurface(uiText("Connection needs attention"), uiText("Check the endpoint, credential and network, then connect again."), kind = HaloFeedbackKind.FAILURE) }
                val enableLabel = uiText("Enable integration %1\$s", row.displayName)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(uiText("Enable integration")); Text(uiText("Halo may use this integration when connected and authorized."), style = MaterialTheme.typography.bodySmall) }
                    Switch(row.enabled, model::enable, enabled = operation == null,
                        modifier = Modifier.semantics { contentDescription = enableLabel })
                }
                Column {
                    HaloFocusAction(uiText("Connect"), HaloGlyph.DEVICE, operation == null && row.enabled && (row.auth == McpAuthKind.NONE || row.credentialSaved), model::connect, active = row.state != McpConnectionState.CONNECTED)
                    TextButton(onClick = model::refresh, enabled = operation == null && row.enabled && (row.auth == McpAuthKind.NONE || row.credentialSaved)) { Text(uiText("Refresh tools")) }
                }
                operation?.let { HaloProgressPresentation(uiText(it)) }
                message?.let { Text(uiText(it)) }
                Text(uiText("Tools"), style = MaterialTheme.typography.titleMedium)
                Text("Cached tool settings. Only a connected, enabled integration exposes enabled tools to Halo. Every action still requires normal authorization.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it }, label = { Text(uiText("Search tools")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (row.tools.isEmpty()) Text(uiText("No tools discovered. Enable this integration and refresh tools explicitly."))
                else if (visibleTools.isEmpty()) { Text(uiText("No matches")); TextButton(onClick = { query = "" }) { Text(uiText("Clear search")) } }
            }
        }
        items(visibleTools, key = { it.id }) { row ->
            val inspectLabel = uiText("Inspect tool %1\$s", row.displayName)
            val enableLabel = uiText("Enable tool %1\$s", row.displayName)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = inspectLabel) { tool = row }.padding(vertical = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(row.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(uiText(row.risk.lowercase().replaceFirstChar { it.uppercase() }) + " · " + uiText(if (row.enabled) "Enabled" else "Disabled"), style = MaterialTheme.typography.labelMedium)
                }
                Switch(row.enabled, { model.tool(row.id, it) }, enabled = operation == null,
                    modifier = Modifier.semantics { contentDescription = enableLabel })
            }
            HorizontalDivider()
        }
        if (server != null) item { TextButton(onClick = { removal = true }, enabled = operation == null) { Text(uiText("Remove integration")) } }
    }
    configuration?.let { McpEditor(it, server?.credentialSaved == true, operation, message,
        onCancel = { if (operation == null) model.configuration.value = null }, onSave = { value, token -> model.save(value, token, false) }) }
    if (removal) AlertDialog(onDismissRequest = { removal = false }, title = { Text("Remove ${server?.displayName ?: "integration"}?") },
        text = { Text(uiText("This removes its discovered tools and connection configuration from Halo. It does not delete anything on the remote server.")) },
        dismissButton = { TextButton(onClick = { removal = false }) { Text(uiText("Cancel")) } },
        confirmButton = { TextButton(onClick = { removal = false; model.remove() }, enabled = operation == null) { Text(uiText("Remove")) } })
    if (credentialRemoval) AlertDialog(onDismissRequest = { credentialRemoval = false }, title = { Text(uiText("Remove credential?")) },
        text = { Text(uiText("This integration will need authorization again. Remote server data is kept.")) },
        confirmButton = { TextButton(enabled = operation == null, onClick = { credentialRemoval = false; model.removeCredential() }) { Text(uiText("Remove credential")) } },
        dismissButton = { TextButton(onClick = { credentialRemoval = false }) { Text(uiText("Cancel")) } })
    tool?.let { selected -> ModalBottomSheet(onDismissRequest = { tool = null }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(selected.displayName, style = MaterialTheme.typography.titleLarge)
            Text(selected.description)
            Text("Remote name · ${selected.remoteName}")
            Text("Halo identity · ${selected.id}")
            Text("Risk · ${selected.risk.lowercase().replaceFirstChar { it.uppercase() }}")
            Text(if (server?.tools?.firstOrNull { it.id == selected.id }?.enabled == true) "Enabled" else "Disabled")
            Text(uiText("Server-provided descriptions are external data. Tools execute only through Halo’s ordinary permission and confirmation flow."))
        }
    } }
}
