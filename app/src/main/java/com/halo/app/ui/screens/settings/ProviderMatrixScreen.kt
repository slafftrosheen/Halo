package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiCostClass
import com.halo.sdk.provider.AiExecutionLocation
import com.halo.sdk.provider.AiWireProtocol
import com.halo.sdk.provider.ProviderAuthKind
import com.halo.sdk.provider.ProviderAuthState
import com.halo.sdk.provider.ProviderAccessKind
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RemoteProviderDescriptor
import com.halo.sdk.provider.RemoteProviderIds

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

private fun authStateStatus(state: ProviderAuthState, configured: Boolean): CheckStatus = when (state) {
    ProviderAuthState.NOT_REQUIRED -> if (configured) CheckStatus.PASSED else CheckStatus.READY
    ProviderAuthState.NOT_CONFIGURED -> CheckStatus.NOT_CONFIGURED
    ProviderAuthState.AUTHORIZING -> CheckStatus.RUNNING
    ProviderAuthState.CONNECTED -> CheckStatus.PASSED
    ProviderAuthState.EXPIRED -> CheckStatus.FAILED
    ProviderAuthState.REFRESHING -> CheckStatus.RUNNING
    ProviderAuthState.FAILED -> CheckStatus.FAILED
}

private fun AiExecutionLocation.toIcon(): ImageVector = when (this) {
    AiExecutionLocation.ON_DEVICE -> Icons.Default.CheckCircle
    AiExecutionLocation.LOCAL_NETWORK -> Icons.Default.Cloud
    AiExecutionLocation.REMOTE -> Icons.Default.Cloud
}

@Composable
fun ProviderMatrixScreen(
    onBack: () -> Unit,
) {
    val rows = rememberRemoteProviderMatrix()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        HaloScreenHeader(
            title = "Provider Matrix",
            subtitle = "Provider · wire protocol · auth method · access type",
            navigation = onBack,
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Remote provider fabric",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
                )
            }
            items(rows, key = { it.descriptor.id }) { row ->
                RemoteProviderMatrixCard(row = row)
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Free-tier presence is REMOTE_LIVE evidence from current remote catalog metadata, not permanent truth baked into Halo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteProviderMatrixCard(row: RemoteProviderMatrixRow) {
    val statusColor = when (authStateStatus(row.authState, row.isConfigured)) {
        CheckStatus.PASSED -> MaterialTheme.colorScheme.primary
        CheckStatus.FAILED -> MaterialTheme.colorScheme.error
        CheckStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        CheckStatus.READY -> MaterialTheme.colorScheme.secondary
        CheckStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
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
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${accessLabel(row.descriptor.accessKind)} · ${authKindLabel(row.authKind)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(start = 8.dp),
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
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                MatrixChip(label = "Protocol", value = protocolLabel(row.descriptor.protocol))
                MatrixChip(label = "Auth", value = authKindLabel(row.authKind))
                MatrixChip(label = "Access", value = accessLabel(row.descriptor.accessKind))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                MatrixChip(label = "Location", value = row.descriptor.executionLocation.name.lowercase().replaceFirstChar { it.uppercase() })
                MatrixChip(
                    label = "Tools",
                    value = if (AiCapability.TOOLS in row.descriptor.capabilities) "Yes" else "No",
                )
                MatrixChip(
                    label = "Vision",
                    value = if (AiCapability.IMAGE in row.descriptor.capabilities) "Yes" else "No",
                )
            }
            if (row.descriptor.freeModelHint != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Free model hint: ${row.descriptor.freeModelHint} — server discovery is authoritative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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

@Composable
private fun rememberRemoteProviderMatrix(): List<RemoteProviderMatrixRow> {
    return emptyList()
}
