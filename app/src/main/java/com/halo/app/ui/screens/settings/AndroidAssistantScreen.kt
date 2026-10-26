package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.halo.host.android.assistant.AssistantEnrollmentLaunchResult
import com.halo.host.android.assistant.AssistantRoleController
import com.halo.host.android.assistant.ActiveInteractorStatus
import com.halo.host.android.assistant.AssistantRuntimeStateStore
import com.halo.sdk.runtime.InteractionAudioAffinity
import com.halo.sdk.runtime.InteractionAudioRouteState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AndroidAssistantScreen(
    roleController: AssistantRoleController,
    onBack: () -> Unit,
    /**
     * MINING-P0-001 interaction audio affinity, owned by the audio session coordinator that holds
     * the leases. Optional so the screen still renders without an audio session in tests.
     */
    audioAffinity: StateFlow<InteractionAudioAffinity?> = MutableStateFlow(null),
    showDiagnostics: Boolean = false,
) {
    SettingsChildZone("Android assistant", onBack)
    val state by roleController.integrationState.collectAsStateWithLifecycle()
    val enrollmentDiagnostics by roleController.enrollmentDiagnostics.collectAsStateWithLifecycle()
    val runtimeState by AssistantRuntimeStateStore.state.collectAsStateWithLifecycle()
    val affinity by audioAffinity.collectAsStateWithLifecycle()
    var enrollmentError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        roleController.recordEnrollmentActivityResult(result.resultCode)
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
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloScreenHeader(uiText("Android assistant"), uiText("Talk to halo from any app"), navigation = onBack)

        Spacer(Modifier.height(HaloSpacing.md))
        if (showDiagnostics) PhoneOperatorPanel(technical = true)

        val (bannerTitle, bannerSubtitle, bannerColor) = when (val s = state) {
            is AssistantIntegrationState.Active -> Triple("halo is ready", "Use your phone’s assistant gesture to talk to halo.", MaterialTheme.colorScheme.primaryContainer)
            is AssistantIntegrationState.RoleHeldNotActive -> Triple("Selected, waiting for Android", "Open Android’s assistant settings and choose halo again.", MaterialTheme.colorScheme.tertiaryContainer)
            is AssistantIntegrationState.EligibleNotSelected -> Triple("Choose halo as your assistant", "Make halo available with your phone’s assistant gesture.", MaterialTheme.colorScheme.secondaryContainer)
            is AssistantIntegrationState.NotEligible -> Triple("Your assistant needs attention", "Open Android’s assistant settings to review your choice.", MaterialTheme.colorScheme.errorContainer)
            is AssistantIntegrationState.Checking -> Triple("Checking your assistant…", "This only takes a moment.", MaterialTheme.colorScheme.surfaceContainerLow)
            is AssistantIntegrationState.Error -> Triple("Could not check your assistant", "Refresh to try again.", MaterialTheme.colorScheme.errorContainer)
        }

        Surface(
            shape = com.halo.app.ui.design.HaloShapes.card,
            color = bannerColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(uiText(bannerTitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(uiText(bannerSubtitle), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(20.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val canRequestRole = state is AssistantIntegrationState.EligibleNotSelected &&
                !enrollmentDiagnostics.settingsFallbackRecommended
            com.halo.app.ui.components.HaloActionButton(
                onClick = {
                    enrollmentError = when (val result = roleController.launchEnrollment { launcher.launch(it) }) {
                        is AssistantEnrollmentLaunchResult.Launched -> null
                        is AssistantEnrollmentLaunchResult.Failed -> result.reason
                    }
                },
                enabled = canRequestRole,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(uiText(if (state is AssistantIntegrationState.Active) "Assistant active" else "Choose halo"))
            }

            OutlinedButton(
                onClick = { roleController.refresh() },
                modifier = Modifier.fillMaxWidth(),
                shape = com.halo.app.ui.design.HaloShapes.pill
            ) {
                Text(uiText("Refresh"))
            }
        }

        if (enrollmentDiagnostics.settingsFallbackRecommended || state is AssistantIntegrationState.RoleHeldNotActive) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    enrollmentError = when (val result = roleController.launchDefaultAssistantSettings { launcher.launch(it) }) {
                        is AssistantEnrollmentLaunchResult.Launched -> null
                        is AssistantEnrollmentLaunchResult.Failed -> result.reason
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = com.halo.app.ui.design.HaloShapes.pill,
            ) { Text(uiText("Open Android settings")) }
        }

        enrollmentError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(uiText("Could not open assistant settings. Try Android settings directly."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        if (showDiagnostics) {
        Text(uiText("Integration Breakdown"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        val diag = when (val s = state) {
            is AssistantIntegrationState.Active -> s.diagnostics
            is AssistantIntegrationState.RoleHeldNotActive -> s.diagnostics
            is AssistantIntegrationState.EligibleNotSelected -> s.diagnostics
            is AssistantIntegrationState.NotEligible -> s.diagnostics
            else -> null
        }

        if (diag != null) {
            val candidate = diag.voiceInteractionServiceDeclared && diag.bindPermissionCorrect &&
                diag.serviceMetadataPresent && diag.sessionServiceDeclared
            DiagnosticStateRow("Candidate", if (candidate) "YES" else "NO", candidate)
            DiagnosticStateRow("Default role", if (diag.roleHeld) "HELD" else "NOT HELD", diag.roleHeld)
            DiagnosticStateRow(
                "Interactor",
                when (diag.activeInteractorStatus) {
                    ActiveInteractorStatus.ACTIVE -> "ACTIVE"
                    ActiveInteractorStatus.INACTIVE -> "INACTIVE"
                    ActiveInteractorStatus.UNKNOWN -> "UNKNOWN"
                },
                diag.activeInteractorStatus == ActiveInteractorStatus.ACTIVE,
                unknown = diag.activeInteractorStatus == ActiveInteractorStatus.UNKNOWN,
            )
            DiagnosticStateRow("Service", if (runtimeState.serviceReady) "ACTIVE" else "NOT BOUND", runtimeState.serviceReady)
            DiagnosticStateRow("Session", if (runtimeState.sessionCreated) "CREATED" else "NOT CREATED", runtimeState.sessionCreated)
            DiagnosticStateRow("Capture", runtimeState.state.name.replace('_', ' '), runtimeState.state.name in setOf("LISTENING", "TRANSCRIBING", "INFERENCE_RUNNING", "RESPONDING", "THINKING"))
            runtimeState.lastResponseAtMs?.let { DiagnosticTextRow("Last response", DateFormat.getDateTimeInstance().format(Date(it))) }
            runtimeState.inputRoute?.let { DiagnosticTextRow("Input route", it) }
            runtimeState.outputRoute?.let { DiagnosticTextRow("Output route", it) }

            // MINING-P0-001 audio continuity: origin -> intended output -> actual endpoint must
            // stay visible across STT, inference and TTS preparation. Diagnostic only; this screen
            // does not configure routes. Device display labels only, never MAC addresses.
            affinity?.let { binding ->
                Spacer(Modifier.height(14.dp))
                Text(uiText("Audio continuity"), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                DiagnosticTextRow("Interaction", binding.interactionId.take(20))
                DiagnosticTextRow("Origin", binding.originLabel)
                DiagnosticTextRow("Intended output", binding.intendedTargetLabel)
                DiagnosticTextRow("Actual endpoint", binding.resolvedEndpointLabel ?: "unresolved")
                DiagnosticTextRow("Fallback", binding.fallbackPolicyLabel)
                DiagnosticStateRow(
                    "Route state",
                    binding.routeState.name,
                    pass = binding.routeState == InteractionAudioRouteState.BOUND,
                    unknown = binding.routeState == InteractionAudioRouteState.PENDING ||
                        binding.routeState == InteractionAudioRouteState.ENDED,
                )
            }

            Spacer(Modifier.height(14.dp))
            BreakdownRow("VoiceInteractionService Declared", diag.voiceInteractionServiceDeclared)
            BreakdownRow("BIND_VOICE_INTERACTION Permission", diag.bindPermissionCorrect)
            BreakdownRow("Voice Interaction Metadata", diag.serviceMetadataPresent)
            BreakdownRow("Session Service Declared", diag.sessionServiceDeclared)
            BreakdownRow("Recognition Service Declared", diag.recognitionServiceDeclared)
            BreakdownRow("Speech Recognition Permission", diag.recognitionServicePermissionCorrect)
            BreakdownRow("ROLE_ASSISTANT Available", diag.roleAvailable)
            BreakdownRow("ROLE_ASSISTANT Held", diag.roleHeld)
            BreakdownRow("Active Interactor Service", diag.activeVoiceInteractionService)

            enrollmentDiagnostics.recordedAtMs?.let { recordedAt ->
                Spacer(Modifier.height(12.dp))
                Text(uiText("Last enrollment return"), style = MaterialTheme.typography.labelLarge)
                DiagnosticTextRow("Mechanism", enrollmentDiagnostics.requestMechanism?.name ?: "Unknown")
                DiagnosticTextRow("Activity result", enrollmentDiagnostics.activityResultCode?.toString() ?: "Unavailable")
                DiagnosticTextRow("Role after return", enrollmentDiagnostics.roleHeldAfterReturn?.toString() ?: "Unknown")
                DiagnosticTextRow("Recorded", DateFormat.getDateTimeInstance().format(Date(recordedAt)))
            }

            Spacer(Modifier.height(12.dp))
            Text(uiText("Platform recognition adapter"), style = MaterialTheme.typography.labelLarge)
            DiagnosticTextRow("Service created", runtimeState.recognition.serviceCreatedCount.toString())
            DiagnosticTextRow("Start requests", runtimeState.recognition.startListeningCount.toString())
            DiagnosticTextRow("Mode", "Compatibility endpoint; Halo STT owns capture")

            if (diag.problems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(uiText("Detected Issues:"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                diag.problems.forEach { problem ->
                    Text("• $problem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        }
    }
}

@Composable
private fun DiagnosticStateRow(label: String, value: String, pass: Boolean, unknown: Boolean = false) {
    DiagnosticTextRow(
        label,
        value,
        when {
            unknown -> MaterialTheme.colorScheme.tertiary
            pass -> HaloPastelMint
            else -> MaterialTheme.colorScheme.error
        },
    )
}

@Composable
private fun DiagnosticTextRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(uiText(label), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun BreakdownRow(label: String, pass: Boolean) {
    Surface(
        shape = com.halo.app.ui.design.HaloShapes.pill,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(uiText(label), style = MaterialTheme.typography.bodyMedium)
            Text(if (pass) "PASS ✓" else "FAIL ✕", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (pass) HaloPastelMint else MaterialTheme.colorScheme.error)
        }
    }
}
