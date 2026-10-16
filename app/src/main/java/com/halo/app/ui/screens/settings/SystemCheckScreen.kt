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
    onOpenIntelligence: (() -> Unit)? = null,
    onOpenVoiceSettings: (() -> Unit)? = null,
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

        // These five facts are projected from the current observation only.
        // READY = available; PASSED = a check actually succeeded.
        val essential = remember(uiState.sections) { systemCheckEssentialItems(uiState.sections) }
        SystemCheckEssentialOverview(essential)
        val suggested = remember(uiState.sections) {
            systemCheckSafeSetupTarget(uiState.sections)
        }
        if (suggested == SystemCheckSetupTarget.INTELLIGENCE && onOpenIntelligence != null) {
            SystemCheckBestSetup(uiText("Choose how halo answers"),
                uiText("Set up a model before running an AI probe."), onOpenIntelligence)
        } else if (suggested == SystemCheckSetupTarget.VOICE && onOpenVoiceSettings != null) {
            SystemCheckBestSetup(uiText("Set up voice"),
                uiText("Check microphone access before starting a speech probe."), onOpenVoiceSettings)
        }
        Text(if (tally.total == 0) uiText("No checks reported yet") else
            uiText("Ready or passed: %1\$d of %2\$d", tally.good, tally.total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = viewModel::runSafeChecks,
                modifier = Modifier.weight(1f),
                shape = com.halo.app.ui.design.HaloShapes.pill
            ) {
                Text(uiText("Refresh"))
            }

            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(uiState.summaryReport))
                },
                modifier = Modifier.weight(1f),
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
    val statusColor = when (item.status) {
        CheckStatus.PASSED -> MaterialTheme.colorScheme.primary
        CheckStatus.FAILED -> MaterialTheme.colorScheme.error
        CheckStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        CheckStatus.READY -> MaterialTheme.colorScheme.secondary
        CheckStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
        CheckStatus.BLOCKED -> MaterialTheme.colorScheme.error
        CheckStatus.UNSUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.settingsCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                HaloIcon(
                    glyph = item.glyph,
                    contentDescription = item.name,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.durationMs != null) {
                    Text(
                        text = "Completed in ${item.durationMs}ms",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Compact, expandable-worthy technical detail: stage statuses, resource facts and
                // digests only. Never prompts, generated text or tool argument values.
                var details by androidx.compose.runtime.saveable.rememberSaveable(item.id) { mutableStateOf(false) }
                if (!item.detail.isNullOrBlank()) TextButton(onClick = { details = !details }) { Text(uiText(if (details) "Hide technical details" else "Technical details")) }
                item.detail?.takeIf { it.isNotBlank() && details }?.let { detail ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            Column(horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Status is NOT clickable. A READY row is not proof of a probe.
                Surface(
                    shape = com.halo.app.ui.design.HaloShapes.pill,
                    color = statusColor.copy(alpha = .10f),
                ) {
                    Text(uiText(systemCheckStatusLabel(item.status)),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
                systemCheckExplicitActionLabel(item)?.let { label ->
                    TextButton(onClick = onAction,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(label), style = MaterialTheme.typography.labelMedium)
                    }
                }
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

/** The brand-board overview samples five real observations, not five probes
 * that Halo pretends to have run. Missing observations are simply omitted.
 */
internal val systemCheckEssentialIds = listOf(
    "core", "mic_permission", "stt_selection", "ai_selection", "phone_operator_accessibility"
)
internal fun systemCheckEssentialItems(sections: List<SystemCheckSection>): List<SystemCheckItem> {
    val byId = sections.flatMap { it.items }.associateBy(SystemCheckItem::id)
    return systemCheckEssentialIds.mapNotNull(byId::get)
}
internal fun systemCheckStatusLabel(status: CheckStatus): String = when(status) {
    CheckStatus.READY -> "Available"
    CheckStatus.PASSED -> "Passed"
    CheckStatus.RUNNING -> "Running"
    CheckStatus.FAILED -> "Failed"
    CheckStatus.NOT_CONFIGURED -> "Needs setup"
    CheckStatus.BLOCKED -> "Blocked"
    CheckStatus.UNSUPPORTED -> "Unsupported"
}

internal enum class SystemCheckSetupTarget { INTELLIGENCE, VOICE }

/** Only route to existing setup screens. Never start a probe, request new
 * permissions, or automatically select a provider from this summary.
 */
internal fun systemCheckSafeSetupTarget(sections: List<SystemCheckSection>): SystemCheckSetupTarget? {
    val essential = systemCheckEssentialItems(sections)
    if (essential.any { it.id == "ai_selection" && it.status == CheckStatus.NOT_CONFIGURED })
        return SystemCheckSetupTarget.INTELLIGENCE
    if (essential.any { it.id == "mic_permission" && it.status == CheckStatus.NOT_CONFIGURED })
        return SystemCheckSetupTarget.VOICE
    return null
}

/** Diagnostic actions are explicit, and only rows that own an explicit action
 * get a button. Passive status rows may no longer trigger Refresh by accident.
 */
internal fun systemCheckExplicitActionLabel(item: SystemCheckItem): String? {
    if (item.status in setOf(CheckStatus.RUNNING, CheckStatus.UNSUPPORTED)) return null
    return when (item.id) {
        "assistant" -> "Open settings"
        "mic_capture" -> "Test microphone"
        "stt_recognition" -> "Test recognition"
        "litert_control" -> if (item.status == CheckStatus.BLOCKED) "Unload local model" else "Test local inference"
        "litert_provider", "ai_execution", "agentic_execution", "litert_tools", "vision_probe" -> "Run probe"
        "audio" -> "Test audio"
        "phone_alarm", "phone_app", "phone_volume" -> "Run test"
        "payload_gc" -> "Collect"
        "presentation_preview" -> "Preview"
        else -> if (item.id.startsWith("remote_")) "Refresh" else null
    }
}

@Composable
internal fun SystemCheckEssentialOverview(items: List<SystemCheckItem>) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("Essential checks"), style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text(uiText("No checks reported yet"),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            } else items.forEachIndexed { index, item ->
                if (index > 0) androidx.compose.material3.HorizontalDivider(
                    color = scheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Text(uiText(systemCheckStatusLabel(item.status)),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (item.status) {
                            CheckStatus.PASSED -> scheme.primary
                            CheckStatus.FAILED -> scheme.error
                            else -> scheme.onSurfaceVariant
                        })
                }
            }
        }
    }
}

@Composable
private fun SystemCheckBestSetup(label: String, message: String, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Next to set up"), style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

