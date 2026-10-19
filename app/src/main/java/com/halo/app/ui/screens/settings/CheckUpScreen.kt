package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloShapes

/** User check-up deliberately consumes statuses, never diagnostic names or details. */
@Composable
fun CheckUpScreen(
    viewModel: SystemCheckViewModel,
    onBack: () -> Unit,
    onIntelligence: () -> Unit,
    onVoice: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val observations = systemCheckEssentialItems(state.sections).associateBy { it.id }
    val labels = listOf(
        "core" to "halo",
        "ai_selection" to "Answers",
        "mic_permission" to "Microphone",
        "stt_selection" to "Listening",
        "phone_operator_accessibility" to "Phone actions",
    )
    val next = systemCheckSafeSetupTarget(state.sections)
    val reading = com.halo.app.di.ManualDependencyGraph.readAloud
    val readingState by reading.state.collectAsStateWithLifecycle()
    val sample = uiText("Hello, I’m halo. Can you hear me clearly?")
    var attempted by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<Boolean?>(null) }
    var recognized by remember { mutableStateOf<String?>(null) }
    val sampleIsPlaying = readingState?.let { sample == it.sentences.joinToString(" ") && it.playing } == true
    LaunchedEffect(sampleIsPlaying) { if (sampleIsPlaying) { attempted = true; heard = null } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { HaloScreenHeader(uiText("Check-up"), uiText("See what is available, then choose what to set up."), navigation = onBack) }
        item {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    labels.forEach { (id, label) ->
                        val status = observations[id]?.status
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(uiText(label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(uiText(when (status) {
                                CheckStatus.READY -> "Available"
                                CheckStatus.PASSED -> "Checked"
                                CheckStatus.RUNNING -> "Checking…"
                                CheckStatus.NOT_CONFIGURED -> "Needs setup"
                                CheckStatus.FAILED -> "Needs attention"
                                CheckStatus.BLOCKED -> "Unavailable right now"
                                CheckStatus.UNSUPPORTED -> "Not available on this phone"
                                null -> "Not checked yet"
                            }), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (next != null) item {
            Button(onClick = if (next == SystemCheckSetupTarget.INTELLIGENCE) onIntelligence else onVoice,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = HaloShapes.pill) {
                Text(uiText(if (next == SystemCheckSetupTarget.INTELLIGENCE) "Choose how halo answers" else "Set up voice"))
            }
        }
        item {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Try listening"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Say a short sentence, then review what halo heard."), style = MaterialTheme.typography.bodyMedium)
                    com.halo.app.ui.components.HaloVoiceTextActions(text = "", onText = { recognized = it },
                        appendDictation = false, maxChars = 1200)
                    recognized?.let { words ->
                        Text(uiText("What halo heard"), style = MaterialTheme.typography.labelLarge)
                        Text(words, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Speaking"), style = MaterialTheme.typography.titleMedium)
                    com.halo.app.ui.components.HaloReadAloud(sample, label = uiText("Try halo’s voice"))
                    if (attempted) {
                        Text(uiText("Did you hear halo?"))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { heard = true }) { Text(uiText("Yes")) }
                            TextButton(onClick = { heard = false; reading.stop() }) { Text(uiText("Not yet")) }
                        }
                    }
                    if (heard == true) Text(uiText("Voice confirmed by you"), color = MaterialTheme.colorScheme.primary)
                    if (heard == false) TextButton(onClick = onVoice) { Text(uiText("Set up voice")) }
                }
            }
        }
        item {
            Text(uiText("Available means ready to try. It does not mean you have already tried it."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::runSafeChecks, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText("Refresh"))
            }
        }
    }
}

/** A bounded report: fixed labels and status values only; no source detail or payloads. */
@Composable
fun TechnicalReportScreen(viewModel: SystemCheckViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val report = remember(state.sections) {
        buildString {
            appendLine("halo · Technical report")
            appendLine("Availability is not proof of a completed check.")
            systemCheckEssentialItems(state.sections).forEach { item ->
                appendLine("${item.id}: ${item.status.name}")
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { HaloScreenHeader(uiText("Technical report"), uiText("Safe to share: no keys, no messages"), navigation = onBack) }
        item {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(report, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Button(onClick = { clipboard.setText(AnnotatedString(report)); copied = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(uiText(if (copied) "Copied" else "Copy report"))
            }
        }
    }
}
