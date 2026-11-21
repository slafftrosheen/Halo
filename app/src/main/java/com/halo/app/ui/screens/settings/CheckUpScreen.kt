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
    onTourRoute: ((String) -> Unit)? = null,
) {
    val title = androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_help_title)
    SettingsChildZone(title, onBack, listOf(
        settingsChildLink("refresh", "Check now", viewModel::runSafeChecks),
        settingsChildLink("onIntelligence", "Brain", onIntelligence),
        settingsChildLink("onVoice", "Voice & reading", onVoice),
    ))
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
    val sampleIsPlaying = readingState?.let { sample == it.sections.joinToString(" ") && it.playing } == true
    LaunchedEffect(sampleIsPlaying) { if (sampleIsPlaying) { attempted = true; heard = null } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val checkTime = remember(state.sections) { if (state.sections.isNotEmpty()) System.currentTimeMillis() else null }
    val localSpace = remember(checkTime) { context.filesDir.usableSpace }
    val onlyStatuses = labels.map { (id, label) ->
        uiText(label) to observations[id]?.status
    }
    val reportTitle = androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_help_share_report)
    val storageLine = uiText("Free storage") + " · " + android.text.format.Formatter.formatFileSize(context, localSpace)
    val reportReady = onlyStatuses.any { it.second != null && it.second != CheckStatus.RUNNING }
    fun shareCheckup(labels: List<Pair<String, String>>) {
        val report = (listOf(title) + labels.map { (name, status) -> "$name · $status" } + storageLine).joinToString("\n")
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, report)
        }
        context.startActivity(android.content.Intent.createChooser(send, reportTitle))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            val subtitle = androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_help_sub)
            if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloScreenHeader(title, subtitle, navigation = onBack)
            else Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        if (onTourRoute != null) item(key = "tours") { HelpTourChooser(onTourRoute) }
        item {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val needs = onlyStatuses.count { (_, status) ->
                        status in setOf(CheckStatus.FAILED, CheckStatus.BLOCKED, CheckStatus.NOT_CONFIGURED)
                    }
                    Text(uiText(if (needs == 0) "Check-up status" else "Needs a look"), style = MaterialTheme.typography.titleMedium)
                    onlyStatuses.forEach { (label, status) ->
                        val attention = status in setOf(CheckStatus.FAILED, CheckStatus.BLOCKED, CheckStatus.NOT_CONFIGURED)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(uiText(zone6CheckStatus(status)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    attention -> MaterialTheme.colorScheme.tertiary
                                    status == CheckStatus.PASSED -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                })
                        }
                    }
                    Text(storageLine, style = MaterialTheme.typography.bodyMedium)

                }
            }
        }
        if (next != null) item {
            com.halo.app.ui.components.HaloActionButton(onClick = if (next == SystemCheckSetupTarget.INTELLIGENCE) onIntelligence else onVoice,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
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
                    com.halo.app.ui.components.HaloReadAloud(sample, label = uiText("Try halo’s voice"), category = com.halo.sdk.provider.RemoteDataCategory.TEXT)
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
            val rows = onlyStatuses.map { (label, status) -> label to uiText(zone6CheckStatus(status)) }
            Text(uiText("How do I…"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onIntelligence, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Choose how halo answers")) }
                TextButton(onVoice, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Set up voice")) }
            }
            OutlinedButton({ shareCheckup(rows) }, enabled = reportReady, modifier = Modifier.heightIn(min = 48.dp)) { Text(reportTitle) }
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_help_share_report_note),
                style = MaterialTheme.typography.bodySmall)
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

private fun zone6CheckStatus(status: CheckStatus?): String = when (status) {
    CheckStatus.READY -> "Available"
    CheckStatus.PASSED -> "Checked"
    CheckStatus.RUNNING -> "Checking…"
    CheckStatus.NOT_CONFIGURED -> "Needs setup"
    CheckStatus.FAILED -> "Needs attention"
    CheckStatus.BLOCKED -> "Unavailable right now"
    CheckStatus.UNSUPPORTED -> "Not available on this phone"
    else -> "Not checked yet"
}

/** A bounded report: fixed labels and status values only; no source detail or payloads. */
@Composable
fun TechnicalReportScreen(viewModel: SystemCheckViewModel, onBack: () -> Unit) {
    SettingsChildZone("Technical report", onBack)
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
        item { if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloScreenHeader(uiText("Technical report"), uiText("Safe to share: no keys, no messages"), navigation = onBack) }
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
