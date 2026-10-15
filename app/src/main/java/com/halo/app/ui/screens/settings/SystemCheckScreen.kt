package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloIcon

@Composable
fun SystemCheckScreen(
    viewModel: SystemCheckViewModel,
    onBack: () -> Unit,
    onOpenPhoneSandbox: (() -> Unit)? = null,
    onOpenAutomationSandbox: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var pendingSpeechAction by remember { mutableStateOf<SystemCheckAction>(SystemCheckAction.RunCaptureProbe) }
    var technicalTools by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val tally = remember(uiState.sections) { systemCheckTally(uiState.sections) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.handleAction(pendingSpeechAction)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.leaveSpeechProbe() } }


    val assistantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.recordAssistantEnrollmentResult(result.resultCode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = com.halo.app.ui.design.HaloSpacing.screen)
    ) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Column {
        Spacer(Modifier.height(16.dp))
        HaloScreenHeader(
            title = uiText("System Check"),
            subtitle = uiText("Run checks explicitly. Hardware and inference checks may need access or configuration."),
            navigation = onBack
        )

        Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                com.halo.app.ui.presence.HaloSignalFace(
                    com.halo.app.ui.presence.HaloSignalPresentation(
                        state = com.halo.app.ui.presence.HaloSignalState.IDLE,
                        label = uiText("System Check"), reducedMotion = true),
                    size = 64.dp, contentDescription = uiText("System Check"))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (tally.total == 0) uiText("No checks reported yet") else
                        uiText("Ready or passed: %1\$d of %2\$d", tally.good, tally.total),
                        style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Ready means available. Passed means a check completed."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = viewModel::runSafeChecks,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = com.halo.app.ui.design.HaloShapes.pill
            ) {
                Text(uiText("Refresh"))
            }

            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(uiState.summaryReport))
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = com.halo.app.ui.design.HaloShapes.pill
            ) {
                Text(uiText("Copy Summary"))
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { technicalTools = !technicalTools },
            modifier = Modifier.heightIn(min = 48.dp)) {
            Text(uiText(if (technicalTools) "Hide technical tools" else "Technical tools"))
        }
        if (technicalTools) {
            TextButton(onClick = { com.halo.app.di.ManualDependencyGraph.routinesViewModel.reconcile() }) {
                Text(uiText("Reconcile automation schedules"))
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(uiState.localAiDiagnostics)) }) {
                Text(uiText("Copy local AI diagnostics"))
            }
            TextButton(onClick = viewModel::runSafeChecks) {
                Text(uiText("Refresh automation source availability"))
            }
            var reflectionPreview by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { reflectionPreview = !reflectionPreview }) {
                Text(uiText("Preview reflection policy"))
            }
            if (reflectionPreview) Text(uiText("Trivial tasks: none. Meaningful completed tasks: deterministic episodes. Optional substantial local FAST reflection: bounded, preemptible, no tools. Inferred memories and behaviors require Notebook review."))
            onOpenAutomationSandbox?.let { open ->
                TextButton(onClick = open) { Text(uiText("Automation sandbox · preview only")) }
            }
        }
        Spacer(Modifier.height(16.dp))

        } }
        if (technicalTools) {
            item { ProviderDiagnosticsPanel(com.halo.app.di.ManualDependencyGraph.providerControlRepository,
                com.halo.app.di.ManualDependencyGraph.speechSynthesisRegistry) }
            item { PhoneOperatorPanel(observationControls = true, onOpenSandbox = onOpenPhoneSandbox) }
        }
        /*
         * MINING-P2-001 presentation preview. Static sample components, rendered only after an explicit
         * tap: no model call, no tool, no provider, no network.
         */
        if (uiState.showPresentationPreview) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = uiText("Presentation preview"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = uiText("Fixed sample data. This exercises every component the renderer supports, including the inert choice group."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    com.halo.app.interaction.StructuredPresentationRenderer(
                        presentation = com.halo.app.interaction.PresentationPreviewCatalog.staticPreview(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { viewModel.handleAction(SystemCheckAction.PreviewPresentations) }) {
                        Text(uiText("Close preview"))
                    }
                }
            }
        }

        if (uiState.speechRecording) item {
            TextButton(onClick = viewModel::finishSpeechCapture) { Text(uiText("Finish speech capture")) }
            TextButton(onClick = viewModel::cancelSpeechProbe) { Text(uiText("Cancel speech probe")) }
        }
        uiState.speechProbeTranscript?.let { transcript -> item { Text("Local transcript: $transcript") } }
        uiState.sections.forEach { section ->
            item {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
                )
            }
            items(section.items, key = { it.id }) { item ->
                SystemCheckCard(
                    item = item,
                    onAction = {
                        // MINING-P0-001 each id maps to the diagnostic level it actually exercises.
                        // Probe actions are never chained: differential diagnosis needs them separate.
                        val action = when {
                            item.id == "assistant" -> SystemCheckAction.OpenAssistantSettings
                            item.id == "mic_capture" -> SystemCheckAction.RunCaptureProbe
                            item.id == "stt_recognition" -> SystemCheckAction.RunSttProbe
                            // A blocked control probe means a native model is resident: the
                            // operator explicitly unloads it instead of Halo silently loading a
                            // second engine to satisfy a diagnostic.
                            item.id == "litert_control" && item.status == CheckStatus.BLOCKED ->
                                SystemCheckAction.UnloadLocalModel
                            item.id == "litert_control" -> SystemCheckAction.RunLiteRtControlProbe
                            item.id == "litert_provider" -> SystemCheckAction.RunProviderVerification
                            item.id == "litert_tools" -> SystemCheckAction.RunToolChannelProbe
                            item.id == "vision_probe" -> SystemCheckAction.RunVisionProbe
                            item.id == "ai_execution" -> SystemCheckAction.RunProviderProbe
                            item.id == "agentic_execution" -> SystemCheckAction.RunAgenticProbe
                            item.id == "audio" -> SystemCheckAction.RunTtsProbe
                            item.id == "phone_alarm" -> SystemCheckAction.TestAlarm
                            item.id == "phone_app" -> SystemCheckAction.TestOpenApp
                            item.id == "phone_volume" -> SystemCheckAction.TestVolume
                            item.id == "presentation_preview" -> SystemCheckAction.PreviewPresentations
                            item.id == "payload_gc" -> SystemCheckAction.CollectPayloads
                            item.id.startsWith("remote_") -> SystemCheckAction.Refresh
                            else -> SystemCheckAction.Refresh
                        }
                        if (item.id.startsWith("remote_")) viewModel.refreshRemoteProviders()
                        if (action is SystemCheckAction.RunSttProbe || action is SystemCheckAction.RunCaptureProbe) {
                            pendingSpeechAction = action
                            microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else if (action is SystemCheckAction.OpenAssistantSettings) {
                            viewModel.launchAssistantEnrollment { intent -> assistantLauncher.launch(intent) }
                        } else {
                            viewModel.handleAction(action)
                        }
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun SystemCheckCard(
    item: SystemCheckItem,
    onAction: () -> Unit
) {
    val statusLabel = when (item.status) {
        CheckStatus.PASSED -> "Passed"
        CheckStatus.FAILED -> "Failed"
        CheckStatus.RUNNING -> "Running"
        CheckStatus.READY -> "Ready"
        CheckStatus.NOT_CONFIGURED -> "Setup required"
        CheckStatus.BLOCKED -> "Blocked"
        CheckStatus.UNSUPPORTED -> "Not supported"
    }
    val actionLabel = when {
        item.id == "litert_control" && item.status == CheckStatus.BLOCKED -> "Unload local model"
        item.id == "mic_capture" -> "Check microphone"
        item.id == "stt_recognition" -> "Check speech recognition"
        item.id == "litert_control" -> "Check local runtime"
        item.id == "litert_provider" -> "Verify provider"
        item.id == "litert_tools" -> "Check tool support"
        item.id == "vision_probe" -> "Check vision"
        item.id == "ai_execution" -> "Check model routing"
        item.id == "agentic_execution" -> "Check actions and tools"
        item.id == "payload_gc" -> "Clean up stored payloads"
        item.id == "assistant" -> "Open assistant settings"
        item.id == "audio" -> "Check speech output"
        item.id == "phone_alarm" -> "Test alarm"
        item.id == "phone_app" -> "Test opening an app"
        item.id == "phone_volume" -> "Test volume"
        item.id == "presentation_preview" -> "Presentation preview"
        else -> "Refresh"
    }
    Card(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.settingsCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = com.halo.app.ui.design.HaloShapes.control,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    HaloIcon(item.glyph, null, Modifier.padding(10.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(uiText(statusLabel), style = MaterialTheme.typography.labelLarge,
                        color = if (item.status == CheckStatus.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(item.reason, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.durationMs?.let { Text(uiText("Completed in %1\$d ms", it),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (item.status == CheckStatus.RUNNING) {
                com.halo.app.ui.design.HaloProgressPresentation(uiText("Running"))
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp),
                    shape = com.halo.app.ui.design.HaloShapes.pill) { Text(uiText(actionLabel)) }
            }
            var details by androidx.compose.runtime.saveable.rememberSaveable(item.id) { mutableStateOf(false) }
            if (!item.detail.isNullOrBlank()) TextButton(onClick = { details = !details },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText(if (details) "Hide technical details" else "Technical details"))
            }
            item.detail?.takeIf { it.isNotBlank() && details }?.let { detail ->
                Text(detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Report evidence as recorded: availability is not proof that a probe ran. */
internal data class SystemCheckTally(val good: Int, val total: Int)
internal fun systemCheckTally(sections: List<SystemCheckSection>): SystemCheckTally {
    val items = sections.flatMap(SystemCheckSection::items)
    return SystemCheckTally(
        good = items.count { it.status == CheckStatus.READY || it.status == CheckStatus.PASSED },
        total = items.size,
    )
}
