package com.halo.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.host.android.assistant.AssistantIntegrationState
import com.halo.host.android.assistant.AssistantRoleController

@Composable
fun AndroidAssistantScreen(
    roleController: AssistantRoleController,
    onBack: () -> Unit
) {
    val state by roleController.integrationState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        roleController.refresh()
    }

    LaunchedEffect(Unit) {
        roleController.refresh()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, roleController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) roleController.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = HaloSpacing.screen)
            .verticalScroll(rememberScrollState())
    ) {
        HaloScreenHeader("Android Assistant", "System interactor & gesture integration", navigation = onBack)

        Spacer(Modifier.height(HaloSpacing.md))

        val (bannerTitle, bannerSubtitle, bannerColor) = when (val s = state) {
            is AssistantIntegrationState.Active -> Triple("Halo is your Default Assistant ✓", "Assistant gesture and voice interactor active", HaloPastelMint)
            is AssistantIntegrationState.RoleHeldNotActive -> Triple("Role Held, Service Inactive", "Halo holds the role but needs system interactor activation", MaterialTheme.colorScheme.tertiaryContainer)
            is AssistantIntegrationState.EligibleNotSelected -> Triple("Halo is Ready for Assistant Role", "Eligible candidate for default system assistant", HaloPastelBlue)
            is AssistantIntegrationState.NotEligible -> Triple("Assistant Integration Needs Repair", "Voice interaction service metadata or permission missing", MaterialTheme.colorScheme.errorContainer)
            is AssistantIntegrationState.Checking -> Triple("Checking Assistant Integration...", "Inspecting package manager and role state", MaterialTheme.colorScheme.surfaceVariant)
            is AssistantIntegrationState.Error -> Triple("Integration Error", s.message, MaterialTheme.colorScheme.errorContainer)
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bannerColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(bannerTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(bannerSubtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { roleController.launchEnrollment { launcher.launch(it) } },
                enabled = state !is AssistantIntegrationState.Active && state !is AssistantIntegrationState.NotEligible,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(when (state) {
                    is AssistantIntegrationState.Active -> "Assistant active"
                    is AssistantIntegrationState.RoleHeldNotActive -> "Repair activation"
                    else -> "Set as Assistant"
                })
            }

            OutlinedButton(
                onClick = { roleController.refresh() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Re-inspect")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Integration Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        val diag = when (val s = state) {
            is AssistantIntegrationState.Active -> s.diagnostics
            is AssistantIntegrationState.RoleHeldNotActive -> s.diagnostics
            is AssistantIntegrationState.EligibleNotSelected -> s.diagnostics
            is AssistantIntegrationState.NotEligible -> s.diagnostics
            else -> null
        }

        if (diag != null) {
            BreakdownRow("VoiceInteractionService Declared", diag.voiceInteractionServiceDeclared)
            BreakdownRow("BIND_VOICE_INTERACTION Permission", diag.bindPermissionCorrect)
            BreakdownRow("Voice Interaction Metadata", diag.serviceMetadataPresent)
            BreakdownRow("Session Service Declared", diag.sessionServiceDeclared)
            BreakdownRow("ROLE_ASSISTANT Available", diag.roleAvailable)
            BreakdownRow("ROLE_ASSISTANT Held", diag.roleHeld)
            BreakdownRow("Active Interactor Service", diag.activeVoiceInteractionService)

            if (diag.problems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Detected Issues:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                diag.problems.forEach { problem ->
                    Text("• $problem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, pass: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(if (pass) "PASS ✓" else "FAIL ✕", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (pass) HaloPastelMint else MaterialTheme.colorScheme.error)
        }
    }
}
