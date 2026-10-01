package com.halo.app.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.speech.diagnostics.SpeechDiagnosticReferences

@Composable
fun SpeechLabScreen(viewModel: SpeechLabViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reference by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var compareAuto by remember { mutableStateOf(false) }
    var repetitions by remember { mutableStateOf(1) }
    var includeTranscripts by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.capture(reference)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Speech Lab", style = MaterialTheme.typography.headlineMedium)
        Text("Capture once, compare installed models on the same PCM. Audio and results stay in memory until you leave.")
        Text(if (viewModel.runner.language() == "lv") "Saki: Halo runas tests" else "Say: Halo speech test")
        OutlinedTextField(reference, { reference = it }, label = { Text("Optional reference for WER / CER") }, enabled = !state.busy)
        var showReferences by remember { mutableStateOf(false) }
        TextButton(onClick = { showReferences = !showReferences }) { Text("English / Latvian test phrases") }
        if (showReferences) SpeechDiagnosticReferences.forEach { phrase -> TextButton(onClick = { reference = phrase }, enabled = !state.busy) { Text(phrase) } }
        Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !state.busy) { Text("Capture sample (up to 15 seconds)") }
        if (state.recording) Button(onClick = viewModel::finishCapture) { Text("Finish capture") }
        state.targets.forEach { target ->
            Row { Checkbox(target.modelId in chosen, { checked -> chosen = if (checked) chosen + target.modelId else chosen - target.modelId }, enabled = !state.busy); Text("${target.displayName} · ${target.language}") }
        }
        if (state.targets.isEmpty()) Text("No verified speech models installed. Install one in Speech recognition settings.")
        Row { Checkbox(compareAuto, { compareAuto = it }, enabled = !state.busy); Text("Also compare Whisper fixed language vs auto") }
        Row { Checkbox(repetitions == 3, { repetitions = if (it) 3 else 1 }, enabled = !state.busy); Text("3 warm repetitions (one cold run per model)") }
        Button(onClick = {
            val targets = state.targets.filter { it.modelId in chosen }.flatMap { target ->
                if (compareAuto && target.modelId.startsWith("whisper") && target.language != "auto") listOf(target, target.copy(language = "auto")) else listOf(target)
            }
            viewModel.benchmark(targets, repetitions, reference)
        }, enabled = state.hasSample && chosen.isNotEmpty() && !state.busy) { Text("Run serial comparison") }
        if (state.busy) { LinearProgressIndicator(); TextButton(onClick = viewModel::cancel) { Text("Cancel (waits for native cleanup)") } }
        Row { Checkbox(includeTranscripts, { includeTranscripts = it }); Text("Include transcripts and reference in copy") }
        TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.summary(includeTranscripts))) }, enabled = state.hasSample && !state.busy) { Text("Copy session summary") }
        Text(state.message)
    }
}
