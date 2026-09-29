package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiExecutionLocation
import com.halo.sdk.provider.AiWireProtocol
import com.halo.sdk.provider.ProviderAccessKind
import com.halo.sdk.provider.ProviderAuthKind
import com.halo.sdk.provider.ProviderAuthState
import com.halo.sdk.provider.ProviderReadiness
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RemoteProviderDescriptor
import com.halo.sdk.provider.RemoteProviderIds
import com.halo.sdk.provider.RemoteProviderPresenceRegistry
import com.halo.sdk.provider.RemoteProviderPresets

data class RemoteProviderMatrixRow(
    val descriptor: RemoteProviderDescriptor,
    val status: ProviderStatus,
    val authState: ProviderAuthState,
    val authKind: ProviderAuthKind,
    val isConfigured: Boolean,
)

private fun accessLabel(access: ProviderAccessKind): String = when (access) {
    ProviderAccessKind.LOCAL -> "Local"
    ProviderAccessKind.FREE_TIER -> "Free tier"
    ProviderAccessKind.SUBSCRIPTION -> "Subscription"
    ProviderAccessKind.PAY_AS_YOU_GO -> "Pay-as-you-go"
    ProviderAccessKind.MIXED -> "Mixed"
    ProviderAccessKind.CUSTOM -> "Custom"
}

private fun protocolLabel(protocol: AiWireProtocol): String = when (protocol) {
    AiWireProtocol.OPENAI_CHAT_COMPLETIONS -> "OpenAI chat completions"
    AiWireProtocol.OPENAI_RESPONSES -> "OpenAI responses"
    AiWireProtocol.ANTHROPIC_MESSAGES -> "Anthropic messages"
    AiWireProtocol.GEMINI_GENERATE_CONTENT -> "Gemini generate content"
    AiWireProtocol.OLLAMA -> "Ollama"
    AiWireProtocol.LITERT_NATIVE -> "LiteRT native"
}

private fun authKindLabel(kind: ProviderAuthKind): String = when (kind) {
    ProviderAuthKind.NONE -> "None"
    ProviderAuthKind.API_KEY -> "API key"
    ProviderAuthKind.BEARER_TOKEN -> "Bearer token"
    ProviderAuthKind.GOOGLE_OAUTH -> "Google OAuth"
    ProviderAuthKind.DEVICE_OAUTH -> "Device OAuth"
    ProviderAuthKind.COMPANION -> "Companion"
}

private fun authStateLabel(state: ProviderAuthState): String = when (state) {
    ProviderAuthState.NOT_REQUIRED -> "Not required"
    ProviderAuthState.NOT_CONFIGURED -> "Not configured"
    ProviderAuthState.AUTHORIZING -> "Authorizing…"
    ProviderAuthState.CONNECTED -> "Connected"
    ProviderAuthState.EXPIRED -> "Expired"
    ProviderAuthState.REFRESHING -> "Refreshing…"
    ProviderAuthState.FAILED -> "Failed"
}

private fun AiExecutionLocation.toIcon(): ImageVector = Icons.Default.Cloud

@Composable
fun ProviderMatrixScreen(
    onBack: () -> Unit,
    registry: RemoteProviderPresenceRegistry? = null,
    credentialPresent: (String) -> Boolean = { false },
    onSaveCredential: (String, String) -> Unit = { _, _ -> },
    onSaveEndpoint: (String, String) -> Unit = { _, _ -> },
    onSaveCustomAuthMode: (String) -> Unit = {},
    onTestProvider: (String, (Boolean) -> Unit) -> Unit = { _, callback -> callback(false) },
) {
    var setupRevision by remember { mutableIntStateOf(0) }
    var configuring by remember { mutableStateOf<RemoteProviderDescriptor?>(null) }
    val rows = remoteProviderMatrixRows(registry, credentialPresent, setupRevision)

    configuring?.let { descriptor ->
        ProviderSetupDialog(
            descriptor = descriptor,
            configured = credentialPresent(credentialKeyFor(descriptor.id)),
            onDismiss = { configuring = null },
            onSave = { credential, endpoint, customAuthMode ->
                if (credential.isNotBlank()) onSaveCredential(descriptor.id, credential)
                if (endpoint.isNotBlank()) onSaveEndpoint(descriptor.id, endpoint)
                customAuthMode?.let(onSaveCustomAuthMode)
                setupRevision++
            },
            onTest = { callback -> onTestProvider(descriptor.id, callback) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        HaloScreenHeader(
            title = "Provider setup",
            subtitle = "Connect remote inference without changing your active model",
            navigation = onBack,
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Remote provider fabric",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
                )
            }
            items(rows, key = { it.descriptor.id }) { row ->
                RemoteProviderMatrixCard(
                    row = row,
                    onConfigure = { configuring = row.descriptor },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Connecting a provider only stores its configuration. It never changes Halo's selected provider or model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Free-tier labels describe provider access, not guaranteed permanent quota. Live model discovery remains authoritative.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteProviderMatrixCard(
    row: RemoteProviderMatrixRow,
    onConfigure: () -> Unit,
) {
    val statusColor = when (row.authState) {
        ProviderAuthState.CONNECTED, ProviderAuthState.NOT_REQUIRED -> MaterialTheme.colorScheme.primary
        ProviderAuthState.EXPIRED, ProviderAuthState.FAILED -> MaterialTheme.colorScheme.error
        ProviderAuthState.AUTHORIZING, ProviderAuthState.REFRESHING -> MaterialTheme.colorScheme.tertiary
        ProviderAuthState.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onConfigure,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = row.descriptor.executionLocation.toIcon(),
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.descriptor.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${accessLabel(row.descriptor.accessKind)} · ${authKindLabel(row.authKind)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = authStateLabel(row.authState),
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MatrixChip("Protocol", protocolLabel(row.descriptor.protocol))
                MatrixChip("Tools", if (AiCapability.TOOLS in row.descriptor.capabilities) "Yes" else "No")
                MatrixChip("Vision", if (AiCapability.IMAGE in row.descriptor.capabilities) "Yes" else "No")
            }
            row.descriptor.freeModelHint?.let { hint ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Free model hint: $hint · server discovery is authoritative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap to configure",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProviderSetupDialog(
    descriptor: RemoteProviderDescriptor,
    configured: Boolean,
    onDismiss: () -> Unit,
    onSave: (credential: String, endpoint: String, customAuthMode: String?) -> Unit,
    onTest: ((Boolean) -> Unit) -> Unit,
) {
    var credential by rememberSaveable(descriptor.id) { mutableStateOf("") }
    var endpoint by rememberSaveable(descriptor.id) { mutableStateOf("") }
    var customAuthMode by rememberSaveable(descriptor.id) { mutableStateOf("NONE") }
    var testResult by rememberSaveable(descriptor.id) { mutableStateOf<String?>(null) }
    val needsEndpoint = descriptor.id == RemoteProviderIds.OPENCODE_COMPANION ||
        descriptor.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE
    val needsCredential = descriptor.authKinds.any { it != ProviderAuthKind.NONE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(descriptor.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${accessLabel(descriptor.accessKind)} · ${protocolLabel(descriptor.protocol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (configured) "Credential configured" else "Credential not configured",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (needsEndpoint) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Endpoint") },
                        placeholder = {
                            Text(
                                if (descriptor.id == RemoteProviderIds.OPENCODE_COMPANION) {
                                    "http://100.x.x.x:8080"
                                } else {
                                    "http://192.168.1.10:8080/v1"
                                }
                            )
                        },
                    )
                }
                if (descriptor.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NONE", "API_KEY", "BEARER_TOKEN").forEach { mode ->
                            TextButton(onClick = { customAuthMode = mode }) {
                                Text(if (customAuthMode == mode) "✓ $mode" else mode)
                            }
                        }
                    }
                }
                if (needsCredential || customAuthMode != "NONE") {
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(
                                if (descriptor.id == RemoteProviderIds.OPENCODE_COMPANION) {
                                    "Companion credential"
                                } else {
                                    "API key / token"
                                }
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text(if (configured) "Leave blank to keep current credential" else "") },
                    )
                }
                Text(
                    text = "Saving connection details does not select this provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    testResult = "Testing…"
                    onTest { ok -> testResult = if (ok) "Connection OK" else "Connection failed" }
                }) {
                    Text("Test connection")
                }
                testResult?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == "Connection OK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    credential,
                    endpoint,
                    customAuthMode.takeIf { descriptor.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE },
                )
                credential = ""
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun RowScope.MatrixChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun remoteProviderMatrixRows(
    registry: RemoteProviderPresenceRegistry?,
    credentialPresent: (String) -> Boolean,
    revision: Int,
): List<RemoteProviderMatrixRow> {
    revision // force recalculation after a setup write
    return listOf(
        RemoteProviderPresets.groq,
        RemoteProviderPresets.cerebras,
        RemoteProviderPresets.opencodeZen,
        RemoteProviderPresets.xai,
        RemoteProviderPresets.opencodeCompanion,
        RemoteProviderPresets.customOpenAiCompatible,
    ).map { descriptor ->
        val presence = registry?.presence(descriptor.id)?.value
        val configured = credentialPresent(credentialKeyFor(descriptor.id)) || presence?.isConfigured == true
        val authState = when {
            configured -> ProviderAuthState.CONNECTED
            presence != null -> presence.authInfo.authState
            else -> ProviderAuthState.NOT_CONFIGURED
        }
        RemoteProviderMatrixRow(
            descriptor = descriptor,
            status = presence?.status
                ?: ProviderStatus(ProviderStatus.State.UNCONFIGURED, null, ProviderReadiness.Unconfigured),
            authState = authState,
            authKind = descriptor.authKinds.firstOrNull() ?: ProviderAuthKind.NONE,
            isConfigured = configured,
        )
    }
}

fun credentialKeyFor(providerId: String): String = when (providerId) {
    RemoteProviderIds.GROQ -> "groq_api_key"
    RemoteProviderIds.CEREBRAS -> "cerebras_api_key"
    RemoteProviderIds.OPENCODE_ZEN -> "opencode_zen_api_key"
    RemoteProviderIds.XAI -> "xai_api_key"
    RemoteProviderIds.OPENCODE_COMPANION -> "opencode_companion_credential"
    else -> "custom_openai_compatible_credential"
}
